package pillihuaman.com.pe.engine.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.engine.application.port.inbound.WhatsAppUseCase;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppIntegrationPort;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppPersistencePort;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppTranslationPort;
import pillihuaman.com.pe.engine.domain.model.ChannelStateDTO;
import pillihuaman.com.pe.engine.domain.model.WhatsAppContact;
import pillihuaman.com.pe.engine.domain.model.WhatsAppEvent;
import pillihuaman.com.pe.engine.domain.model.WhatsAppMessage;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.websocket.WhatsAppWebSocketSessionManager;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService implements WhatsAppUseCase {
    private final WhatsAppIntegrationPort integrationPort;
    private final WhatsAppPersistencePort persistencePort;
    private final WhatsAppTranslationPort translationPort;
    private final WhatsAppWebSocketSessionManager sessionManager;

    @Override
    public Mono<Map<String, Object>> generateLinkQr(String tenantId, String bearerToken) {
        // >>> CHANGE
        log.info("WHATSAPP_QR_GENERATE", "Requesting QR link for tenant: " + tenantId, "USER");
        return integrationPort.getQrCode(tenantId, bearerToken);
        // <<< CHANGE
    }


    @Override
    public Mono<ChannelStateDTO> getLinkState(String tenantId, String bearerToken) {

        return integrationPort.checkState(tenantId, bearerToken);

    }

    private String extractText(Map<String, Object> data) {
        Map<String, Object> message = (Map<String, Object>) data.get("message");
        if (message == null) return "";
        if (message.containsKey("conversation")) return (String) message.get("conversation");
        if (message.containsKey("extendedTextMessage")) {
            return (String) ((Map<String, Object>) message.get("extendedTextMessage")).get("text");
        }
        return "";
    }

    @Override
    public Mono<WhatsAppMessage> sendOutgoingMessage(String tenantId, String recipientPhone, String text, String bearerToken) {
        log.info("[OUTBOUND-INIT] Target: {} | Tenant: {}", recipientPhone, tenantId);

        WhatsAppMessage initialMsg = new WhatsAppMessage(
                UUID.randomUUID().toString(), "ME", recipientPhone, text,
                "", "es", "es", Instant.now(), tenantId, true, false,
                "QUEUED", null, null, 0,
                "TEXT", null, null, null, false
        );

        return persistencePort.saveMessage(initialMsg)
                .flatMap(queuedMsg -> persistencePort.findContactByPhoneAndTenant(recipientPhone, tenantId)
                        .defaultIfEmpty(new WhatsAppContact(
                                UUID.randomUUID().toString(), recipientPhone, "Default", "es", tenantId))
                        .flatMap(contact -> {
                            String targetLang = contact.preferredLanguage();
                            return translationPort.translateText(text, "es", targetLang, bearerToken)
                                    .flatMap(translated -> {
                                        WhatsAppMessage sendingMsg = updateMessageState(
                                                queuedMsg, "SENDING", translated, targetLang, null, null, 0);
                                        return persistencePort.saveMessage(sendingMsg)
                                                .flatMap(msg -> integrationPort.sendTranslatedMessage(
                                                                tenantId, recipientPhone, translated, bearerToken)
                                                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                                                .filter(this::isTransientError))
                                                        .flatMap(extId -> {
                                                            WhatsAppMessage sentMsg = updateMessageState(
                                                                    msg, "SENT", translated, targetLang, extId, null, 0);
                                                            return persistencePort.saveMessage(sentMsg);
                                                        })
                                                        .onErrorResume(e -> {
                                                            log.error("[OUTBOUND-FAILED] Flow interrupted: {}", e.getMessage());
                                                            WhatsAppMessage failMsg = updateMessageState(
                                                                    msg, "FAILED", translated, targetLang, null, e.getMessage(), 3);
                                                            return persistencePort.saveMessage(failMsg);
                                                        })
                                                );
                                    });
                        }))
                .doOnSuccess(saved -> sessionManager.pushMessageToTenant(tenantId, saved));
    }

    private WhatsAppMessage updateMessageState(
            WhatsAppMessage msg, String status, String translated, String targetLang,
            String extId, String failReason, int retries) {
        return new WhatsAppMessage(
                msg.id(), msg.sender(), msg.recipient(), msg.originalText(),
                translated, msg.sourceLanguage(), targetLang, msg.timestamp(),
                msg.tenantId(), msg.outgoing(), true, status, extId, failReason, retries,
                msg.messageKind(), msg.mediaUrl(), msg.mimeType(), msg.caption(), msg.hasMedia()
        );
    }

    private boolean isTransientError(Throwable e) {
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wce) {
            return wce.getStatusCode().is5xxServerError() || wce.getStatusCode().value() == 408;
        }
        return e instanceof java.util.concurrent.TimeoutException || e instanceof java.io.IOException;
    }

    private String determineKindFromMimeType(String mimeType) {
        if (mimeType == null) return "DOCUMENT";
        if (mimeType.startsWith("image/")) return "IMAGE";
        if (mimeType.startsWith("video/")) return "VIDEO";
        if (mimeType.startsWith("audio/")) return "AUDIO";
        return "DOCUMENT";
    }

    @Override
    public Mono<WhatsAppMessage> sendOutgoingMessage(
            String tenantId, String recipientPhone, String text,
            String mediaBase64, String mimeType, String fileName, String bearerToken) {

        log.info("[OUTBOUND-INIT] Target: {} | Tenant: {} | HasMedia: {}", recipientPhone, tenantId, mediaBase64 != null);

        final boolean isMedia = mediaBase64 != null && !mediaBase64.isBlank();
        final String messageKind = isMedia ? determineKindFromMimeType(mimeType) : "TEXT";
        final String safeText = text != null ? text : "";

        WhatsAppMessage initialMsg = new WhatsAppMessage(
                UUID.randomUUID().toString(), "ME", recipientPhone, safeText,
                "", "es", "es", Instant.now(), tenantId, true, false,
                "QUEUED", null, null, 0,
                messageKind, isMedia ? mediaBase64 : null, mimeType, isMedia ? safeText : null, isMedia
        );

        return persistencePort.saveMessage(initialMsg)
                .flatMap(queuedMsg -> persistencePort.findContactByPhoneAndTenant(recipientPhone, tenantId)
                        .defaultIfEmpty(new WhatsAppContact(
                                UUID.randomUUID().toString(), recipientPhone, "Default", "es", tenantId))
                        .flatMap(contact -> {
                            String targetLang = contact.preferredLanguage();

                            // Traducimos el texto (o el caption si es media)
                            Mono<String> translationMono = safeText.isBlank()
                                    ? Mono.just("")
                                    : translationPort.translateText(safeText, "es", targetLang, bearerToken);

                            return translationMono.flatMap(translated -> {
                                WhatsAppMessage sendingMsg = updateMessageState(
                                        queuedMsg, "SENDING", translated, targetLang, null, null, 0);

                                return persistencePort.saveMessage(sendingMsg)
                                        .flatMap(msg -> {
                                            // Enrutamiento inteligente a Evolution API
                                            Mono<String> apiCall = isMedia
                                                    ? integrationPort.sendMediaMessage(tenantId, recipientPhone, mediaBase64, mimeType, fileName, translated, bearerToken)
                                                    : integrationPort.sendTranslatedMessage(tenantId, recipientPhone, translated, bearerToken);

                                            return apiCall
                                                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                                            .filter(this::isTransientError))
                                                    .flatMap(extId -> {
                                                        WhatsAppMessage sentMsg = updateMessageState(
                                                                msg, "SENT", translated, targetLang, extId, null, 0);
                                                        return persistencePort.saveMessage(sentMsg);
                                                    })
                                                    .onErrorResume(e -> {
                                                        log.error("[OUTBOUND-FAILED] Flow interrupted: {}", e.getMessage());
                                                        WhatsAppMessage failMsg = updateMessageState(
                                                                msg, "FAILED", translated, targetLang, null, e.getMessage(), 3);
                                                        return persistencePort.saveMessage(failMsg);
                                                    });
                                        });
                            });
                        }))
                .doOnSuccess(saved -> sessionManager.pushMessageToTenant(tenantId, saved));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<WhatsAppMessage> handleIncomingWebhook(final Map<String, Object> webhookData) {
        final String eventType = (String) webhookData.getOrDefault("event", "UNKNOWN");
        String rawTenant = String.valueOf(webhookData.get("instance"));

        if (rawTenant == null || "null".equals(rawTenant) || rawTenant.isBlank()) {
            rawTenant = "DEFAULT";
            log.warn("[WEBHOOK] Tenant not found in payload. Falling back to DEFAULT.");
        }
        final String tenantId = rawTenant;

        String rawPayload;
        try {
            rawPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(webhookData);
        } catch (Exception e) {
            rawPayload = webhookData.toString();
        }

        WhatsAppEvent rawEvent = new WhatsAppEvent(
                UUID.randomUUID().toString(), tenantId, eventType, rawPayload,
                Instant.now(), "PENDING", null
        );

        return persistencePort.saveEvent(rawEvent)
                .flatMap(savedEvent -> {
                    if ("messages.update".equals(eventType)) {
                        return processMessageUpdate(webhookData, savedEvent, tenantId);
                    }
                    if ("messages.upsert".equals(eventType)) {
                        return processWebhookPayload(webhookData, savedEvent, tenantId);
                    }
                    // Acknowledge other events but do not process as messages
                    return persistencePort.updateEventStatus(savedEvent.id(), "ACKNOWLEDGED", "Non-message event")
                            .then(Mono.empty());
                })
                .onErrorResume(error -> {
                    log.error("[WEBHOOK-ERROR] Extreme processing failure: {}", error.getMessage());
                    return persistencePort.updateEventStatus(rawEvent.id(), "FAILED", error.getMessage())
                            .then(Mono.empty());
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<WhatsAppMessage> processMessageUpdate(
            Map<String, Object> webhookData, WhatsAppEvent event, String tenantId) {

        final Object dataObj = webhookData.get("data");
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) {
            return persistencePort.updateEventStatus(event.id(), "IGNORED", "No data list").then(Mono.empty());
        }

        Map<String, Object> updateItem = (Map<String, Object>) dataList.get(0);
        Map<String, Object> key = (Map<String, Object>) updateItem.get("key");
        Map<String, Object> update = (Map<String, Object>) updateItem.get("update");

        if (key == null || update == null) {
            return persistencePort.updateEventStatus(event.id(), "IGNORED", "Missing key or update node").then(Mono.empty());
        }

        String externalId = (String) key.get("id");
        String newStatus = (String) update.get("status");

        if (externalId == null || newStatus == null) {
            return persistencePort.updateEventStatus(event.id(), "IGNORED", "Missing ID or Status").then(Mono.empty());
        }

        return persistencePort.findByExternalId(tenantId, externalId)
                .flatMap(existingMsg -> {
                    WhatsAppMessage updatedMsg = new WhatsAppMessage(
                            existingMsg.id(), existingMsg.sender(), existingMsg.recipient(),
                            existingMsg.originalText(), existingMsg.translatedText(), existingMsg.sourceLanguage(),
                            existingMsg.targetLanguage(), existingMsg.timestamp(), existingMsg.tenantId(),
                            existingMsg.outgoing(), existingMsg.translated(), newStatus.toUpperCase(),
                            existingMsg.externalMessageId(), existingMsg.failureReason(), existingMsg.retryCount(),
                            existingMsg.messageKind(), existingMsg.mediaUrl(), existingMsg.mimeType(),
                            existingMsg.caption(), existingMsg.hasMedia()
                    );
                    return persistencePort.saveMessage(updatedMsg)
                            .flatMap(saved -> persistencePort.updateEventStatus(event.id(), "PROCESSED", null)
                                    .thenReturn(saved))
                            .doOnSuccess(saved -> sessionManager.pushMessageToTenant(tenantId, saved));
                })
                .switchIfEmpty(persistencePort.updateEventStatus(event.id(), "IGNORED", "Message not found locally").then(Mono.empty()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMessage(Map<String, Object> message) {
        if (message == null) return java.util.Collections.emptyMap();
        String[] wrappers = {"ephemeralMessage", "viewOnceMessage", "viewOnceMessageV2", "viewOnceMessageV2Extension", "documentWithCaptionMessage"};
        for (String wrapper : wrappers) {
            if (message.containsKey(wrapper)) {
                Map<String, Object> wrapped = (Map<String, Object>) message.get(wrapper);
                if (wrapped != null && wrapped.containsKey("message")) {
                    return unwrapMessage((Map<String, Object>) wrapped.get("message"));
                }
            }
        }
        return message;
    }

    @SuppressWarnings("unchecked")
    private Mono<WhatsAppMessage> processWebhookPayload(
            Map<String, Object> webhookData, WhatsAppEvent event, String tenantId) {

        final Object dataObj = webhookData.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return Mono.empty();

        final Object keyObj = data.get("key");
        if (!(keyObj instanceof Map<?, ?> key)) return Mono.empty();

        final boolean fromMe = Boolean.TRUE.equals(key.get("fromMe"));
        final String remoteJidRaw = (String) key.get("remoteJid");
        final String externalId = (String) key.get("id");

        if (remoteJidRaw == null || remoteJidRaw.isBlank() || remoteJidRaw.contains("@g.us")) {
            return persistencePort.updateEventStatus(event.id(), "IGNORED", "Group or invalid")
                    .then(Mono.empty());
        }

        final String contactPhone = remoteJidRaw.split("@")[0];
        final String sender = fromMe ? "ME" : contactPhone;
        final String recipient = fromMe ? contactPhone : "ME";
        final boolean isOutgoing = fromMe;

        return persistencePort.findByExternalId(tenantId, externalId)
                .flatMap(existingMsg -> {
                    log.info("[WEBHOOK-DEDUPLICATION] Message {} already exists. Updating status.", externalId);
                    WhatsAppMessage updatedMsg = new WhatsAppMessage(
                            existingMsg.id(), existingMsg.sender(), existingMsg.recipient(), existingMsg.originalText(),
                            existingMsg.translatedText(), existingMsg.sourceLanguage(), existingMsg.targetLanguage(),
                            existingMsg.timestamp(), existingMsg.tenantId(), existingMsg.outgoing(), existingMsg.translated(),
                            isOutgoing ? "SENT" : "DELIVERED", existingMsg.externalMessageId(), existingMsg.failureReason(), existingMsg.retryCount(),
                            existingMsg.messageKind(), existingMsg.mediaUrl(), existingMsg.mimeType(), existingMsg.caption(),
                            existingMsg.hasMedia()
                    );
                    return persistencePort.saveMessage(updatedMsg)
                            .flatMap(saved -> persistencePort.updateEventStatus(event.id(), "PROCESSED_DEDUPLICATED", null).thenReturn(saved))
                            .doOnSuccess(saved -> sessionManager.pushMessageToTenant(tenantId, saved));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Map<String, Object> rawMessageNode = (Map<String, Object>) data.get("message");
                    MessageExtractionResult extracted = extractMessageContent(rawMessageNode);

                    if (extracted.text().isBlank() && !extracted.hasMedia()) {
                        return persistencePort.updateEventStatus(event.id(), "IGNORED", "Empty text and no media")
                                .then(Mono.empty());
                    }

                    // >>> CHANGE: FIX REACTOR STREAM (Previene que Mono.empty silencie los textos)
                    Mono<String> mediaUrlMono = Mono.just(extracted.mediaUrl() != null ? extracted.mediaUrl() : "NO_MEDIA");

                    if (extracted.hasMedia() && extracted.mediaUrl() != null && extracted.mediaUrl().contains(".whatsapp.net")) {
                        log.info("[MEDIA] Link .enc detectado. Solicitando descifrado Base64 a Evolution API...");

                        mediaUrlMono = integrationPort.getBase64Media(tenantId, (Map<String, Object>) data)
                                .defaultIfEmpty(extracted.mediaUrl()); // Fallback
                    }

                    return mediaUrlMono.flatMap(finalMediaUrl -> {
                        // Limpiamos el flag temporal para que Mongo guarde null en textos limpios
                        String cleanMediaUrl = "NO_MEDIA".equals(finalMediaUrl) ? null : finalMediaUrl;

                        return persistencePort.findContactByPhoneAndTenant(contactPhone, tenantId)
                                .switchIfEmpty(Mono.defer(() -> {
                                    log.warn("[WEBHOOK-RECONCILIATION] Contact {} missing. Generating profile.", contactPhone);
                                    WhatsAppContact newContact = new WhatsAppContact(
                                            UUID.randomUUID().toString(), contactPhone, "Unknown", "es", tenantId);
                                    return persistencePort.saveContact(newContact);
                                }))
                                .flatMap(contact -> {
                                    Mono<String> translationMono = Mono.just(extracted.text());

                                    if (!isOutgoing && extracted.text() != null && !extracted.text().isBlank()) {
                                        translationMono = translationPort.translateText(
                                                extracted.text(), contact.preferredLanguage(), "es", "SYSTEM"
                                        );
                                    }

                                    return translationMono.flatMap(translated -> {
                                        final WhatsAppMessage message = new WhatsAppMessage(
                                                UUID.randomUUID().toString(), sender, recipient, extracted.text(), translated,
                                                contact.preferredLanguage(), "es", Instant.now(), tenantId, isOutgoing,
                                                !extracted.text().equals(translated), isOutgoing ? "SENT" : "DELIVERED",
                                                externalId, null, 0,
                                                extracted.kind(),
                                                cleanMediaUrl,
                                                extracted.mimeType(),
                                                extracted.caption(), extracted.hasMedia()
                                        );

                                        return persistencePort.saveMessage(message)
                                                .flatMap(saved -> persistencePort.updateEventStatus(event.id(), "PROCESSED", null)
                                                        .thenReturn(saved))
                                                .doOnSuccess(saved -> {
                                                    log.info("[WEBHOOK-SUCCESS] Msg saved. Kind: {}, Outgoing: {}",
                                                            saved.messageKind(), isOutgoing);
                                                    sessionManager.pushMessageToTenant(tenantId, saved);
                                                });
                                    });
                                });
                    });
                }));
    }

    private MessageExtractionResult extractMessageContent(Map<String, Object> rawMessage) {
        if (rawMessage == null) return new MessageExtractionResult("", "TEXT", null, null, null, false);

        Map<String, Object> message = unwrapMessage(rawMessage);

        if (message.containsKey("conversation")) {
            return new MessageExtractionResult((String) message.get("conversation"), "TEXT", null, null, null, false);
        }

        if (message.containsKey("extendedTextMessage")) {
            Map<String, Object> extended = (Map<String, Object>) message.get("extendedTextMessage");
            return new MessageExtractionResult((String) extended.get("text"), "TEXT", null, null, null, false);
        }

        String[] mediaTypes = {"imageMessage", "videoMessage", "documentMessage", "audioMessage", "stickerMessage"};
        for (String type : mediaTypes) {
            if (message.containsKey(type)) {
                Map<String, Object> mediaData = (Map<String, Object>) message.get(type);
                String kind = type.replace("Message", "").toUpperCase();

                String mediaUrl = (String) mediaData.getOrDefault("url", mediaData.get("base64"));
                String mimeType = (String) mediaData.get("mimetype");
                String caption = (String) mediaData.getOrDefault("caption", "");

                String text = caption;
                if (text.isBlank() && mediaData.containsKey("fileName")) {
                    text = (String) mediaData.get("fileName");
                }
                if (text.isBlank()) text = "[" + kind + "]";

                return new MessageExtractionResult(text, kind, mediaUrl, mimeType, caption, true);
            }
        }
        return new MessageExtractionResult("", "UNKNOWN", null, null, null, false);
    }


    @Override
    public Mono<WhatsAppContact> registerOrUpdateContact(String tenantId, WhatsAppContact contact) {
        return persistencePort.findContactByPhoneAndTenant(contact.phoneNumber(), tenantId)
                .flatMap(existingContact -> {
                    WhatsAppContact updatedContact = new WhatsAppContact(
                            existingContact.id(),
                            contact.phoneNumber(),
                            contact.name(),
                            contact.preferredLanguage(),
                            tenantId
                    );
                    return persistencePort.saveContact(updatedContact);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    WhatsAppContact newContact = new WhatsAppContact(
                            contact.id() != null ? contact.id() : UUID.randomUUID().toString(),
                            contact.phoneNumber(),
                            contact.name(),
                            contact.preferredLanguage(),
                            tenantId
                    );
                    return persistencePort.saveContact(newContact);
                }));
    }

    @Override
    public Mono<List<WhatsAppContact>> getContacts(final String tenantId) {
        return persistencePort.findContactsByTenant(tenantId).collectList();
    }

    @Override
    public Mono<List<WhatsAppMessage>> getChatHistory(final String tenantId, final String phoneNumber) {
        return persistencePort.findChatHistory(tenantId, phoneNumber).collectList();
    }

    private record MessageExtractionResult(
            String text,
            String kind,
            String mediaUrl,
            String mimeType,
            String caption,
            boolean hasMedia
    ) {
    }
}
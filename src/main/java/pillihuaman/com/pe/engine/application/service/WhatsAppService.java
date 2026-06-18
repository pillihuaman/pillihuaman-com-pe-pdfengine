package pillihuaman.com.pe.engine.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.engine.application.port.inbound.WhatsAppUseCase;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppIntegrationPort;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppPersistencePort;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppTranslationPort;
import pillihuaman.com.pe.engine.domain.model.WhatsAppContact;
import pillihuaman.com.pe.engine.domain.model.WhatsAppMessage;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.websocket.WhatsAppWebSocketSessionManager;
import reactor.core.publisher.Mono;

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
    public Mono<String> generateLinkQr(String tenantId, String bearerToken) {
        log.info("WHATSAPP_QR_GENERATE", "Requesting QR link for tenant: " + tenantId, "USER");
        return integrationPort.getQrCode(tenantId, bearerToken);
    }

    @Override
    public Mono<String> getLinkState(String tenantId, String bearerToken) {
        return integrationPort.checkState(tenantId, bearerToken);
    }

    @Override
    public Mono<WhatsAppMessage> handleIncomingWebhook(Map<String, Object> webhookData) {
        String event = (String) webhookData.get("event");

        if (!"messages.upsert".equals(event)) {
            log.debug("[WhatsAppService] Ignored non-message webhook event: {}", event);
            return Mono.empty();
        }

        String tenantId = (String) webhookData.getOrDefault("instance", "DEFAULT");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
        if (data == null) return Mono.empty();

        @SuppressWarnings("unchecked")
        Map<String, Object> key = (Map<String, Object>) data.get("key");
        if (key == null) return Mono.empty();

        Boolean fromMe = (Boolean) key.get("fromMe");
        if (Boolean.TRUE.equals(fromMe)) {
            return Mono.empty();
        }

        String senderRaw = (String) key.get("remoteJid");
        final String sender =
                (senderRaw != null && senderRaw.contains("@"))
                        ? senderRaw.split("@")[0]
                        : (senderRaw != null ? senderRaw : "UNKNOWN");

        String extractedText = "";
        @SuppressWarnings("unchecked")
        Map<String, Object> messageMap = (Map<String, Object>) data.get("message");
        if (messageMap != null) {
            if (messageMap.containsKey("conversation")) {
                extractedText = (String) messageMap.get("conversation");
            } else if (messageMap.containsKey("extendedTextMessage")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> extText = (Map<String, Object>) messageMap.get("extendedTextMessage");
                extractedText = (String) extText.get("text");
            }
        }

        final String text = extractedText;

        if (text == null || text.isBlank()) {
            return Mono.empty();
        }

        // >>> CHANGE
        return persistencePort
                .findContactByPhoneAndTenant(sender, tenantId)
                .defaultIfEmpty(
                        new WhatsAppContact(UUID.randomUUID().toString(), sender, "New Contact", "en", tenantId))
                .flatMap(
                        contact ->
                                translationPort
                                        .translateText(text, contact.preferredLanguage(), "es", "SYSTEM_TOKEN")
                                        .flatMap(
                                                translatedText -> {
                                                    WhatsAppMessage message =
                                                            new WhatsAppMessage(
                                                                    UUID.randomUUID().toString(),
                                                                    sender,
                                                                    "ME",
                                                                    text,
                                                                    translatedText,
                                                                    contact.preferredLanguage(),
                                                                    "es",
                                                                    Instant.now(),
                                                                    tenantId,
                                                                    false,
                                                                    !text.equals(translatedText));

                                                    return persistencePort
                                                            .saveMessage(message)
                                                            .doOnSuccess(saved -> {
                                                                log.info("[AUDIT] WHATSAPP_INBOUND | Tenant: {} | Saved message ID: {}",
                                                                        tenantId, saved.id());
                                                                sessionManager.pushMessageToTenant(tenantId, saved);
                                                            });
                                                }));

    }

    @Override
    public Mono<WhatsAppMessage> sendOutgoingMessage(
            String tenantId, String recipientPhone, String text, String bearerToken) {
        return persistencePort
                .findContactByPhoneAndTenant(recipientPhone, tenantId)
                .defaultIfEmpty(
                        new WhatsAppContact(
                                UUID.randomUUID().toString(), recipientPhone, "Default Contact", "zh", tenantId))
                .flatMap(
                        contact -> {
                            String sourceLang = "es";
                            String targetLang = contact.preferredLanguage();
                            return translationPort
                                    .translateText(text, sourceLang, targetLang, bearerToken)
                                    .flatMap(
                                            translatedText -> {
                                                return integrationPort
                                                        .sendTranslatedMessage(tenantId, recipientPhone, translatedText, bearerToken)
                                                        .then(
                                                                Mono.defer(
                                                                        () -> {
                                                                            WhatsAppMessage message =
                                                                                    new WhatsAppMessage(
                                                                                            UUID.randomUUID().toString(),
                                                                                            "ME",
                                                                                            recipientPhone,
                                                                                            text,
                                                                                            translatedText,
                                                                                            sourceLang,
                                                                                            targetLang,
                                                                                            Instant.now(),
                                                                                            tenantId,
                                                                                            true,
                                                                                            true);

                                                                            return persistencePort
                                                                                    .saveMessage(message)
                                                                                    .doOnSuccess(saved -> {
                                                                                        log.info("[AUDIT] WHATSAPP_OUTBOUND | Tenant: {} | Sent message ID: {}",
                                                                                                tenantId, saved.id());
                                                                                        sessionManager.pushMessageToTenant(tenantId, saved);
                                                                                    });

                                                                        }));
                                            });
                        });
    }

    @Override
    public Mono<WhatsAppContact> registerOrUpdateContact(String tenantId, WhatsAppContact contact) {
        // >>> CHANGE
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
        // <<< CHANGE
    }

    @Override
    public Mono<List<WhatsAppContact>> getContacts(final String tenantId) {
        log.debug("Retrieving contacts for tenant {}", tenantId);
        return persistencePort.findContactsByTenant(tenantId).collectList();
    }

    @Override
    public Mono<List<WhatsAppMessage>> getChatHistory(final String tenantId, final String phoneNumber) {
        log.debug("Retrieving chat history for tenant {} and contact {}", tenantId, phoneNumber);
        return persistencePort.findChatHistory(tenantId, phoneNumber).collectList();
    }
}
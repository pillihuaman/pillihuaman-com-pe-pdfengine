package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.whatsapp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppPersistencePort;
import pillihuaman.com.pe.engine.domain.model.WhatsAppContact;
import pillihuaman.com.pe.engine.domain.model.WhatsAppEvent;
import pillihuaman.com.pe.engine.domain.model.WhatsAppMessage;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.WhatsAppEventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WhatsAppMongoAdapter implements WhatsAppPersistencePort {
    private final WhatsAppMessageRepository messageRepository;
    private final WhatsAppContactRepository contactRepository;
    private final WhatsAppEventRepository eventRepository;


    @Override
    public Mono<WhatsAppContact> saveContact(WhatsAppContact contact) {
        WhatsAppContactEntity entity = new WhatsAppContactEntity(
                contact.id(), contact.phoneNumber(), contact.name(),
                contact.preferredLanguage(), contact.tenantId()
        );
        return contactRepository.save(entity).map(e -> new WhatsAppContact(
                e.id(), e.phoneNumber(), e.name(), e.preferredLanguage(), e.tenantId()
        ));
    }

    @Override
    public Mono<WhatsAppMessage> saveMessage(WhatsAppMessage message) {
        WhatsAppMessageEntity entity = new WhatsAppMessageEntity(
                message.id(), message.sender(), message.recipient(), message.originalText(),
                message.translatedText(), message.sourceLanguage(), message.targetLanguage(),
                message.timestamp(), message.tenantId(), message.outgoing(), message.translated(),
                message.status(), message.externalMessageId(), message.failureReason(), message.retryCount(),
                message.messageKind(), message.mediaUrl(), message.mimeType(), message.caption(), message.hasMedia()
        );
        return messageRepository.save(entity)
                .map(e -> new WhatsAppMessage(
                        e.id(), e.sender(), e.recipient(), e.originalText(), e.translatedText(),
                        e.sourceLanguage(), e.targetLanguage(), e.timestamp(), e.tenantId(),
                        e.outgoing(), e.translated(), e.status(), e.externalMessageId(),
                        e.failureReason(), e.retryCount(),
                        e.messageKind() != null ? e.messageKind() : "TEXT",
                        e.mediaUrl(), e.mimeType(), e.caption(),
                        Boolean.TRUE.equals(e.hasMedia())
                ));
    }

    @Override
    public Mono<WhatsAppContact> findContactByPhoneAndTenant(String phoneNumber, String tenantId) {
        return contactRepository.findByPhoneNumberAndTenantId(phoneNumber, tenantId)
                .map(e -> new WhatsAppContact(
                        e.id(), e.phoneNumber(), e.name(), e.preferredLanguage(), e.tenantId()
                ));
    }

    @Override
    public Flux<WhatsAppMessage> findMessagesByTenant(String tenantId) {
        return messageRepository.findByTenantIdOrderByTimestampDesc(tenantId)
                .map(e -> new WhatsAppMessage(
                        e.id(), e.sender(), e.recipient(), e.originalText(), e.translatedText(),
                        e.sourceLanguage(), e.targetLanguage(), e.timestamp(), e.tenantId(),
                        e.outgoing(), e.translated(), e.status(), e.externalMessageId(), e.failureReason(), e.retryCount(),
                        e.messageKind() != null ? e.messageKind() : "TEXT",
                        e.mediaUrl(), e.mimeType(), e.caption(),
                        Boolean.TRUE.equals(e.hasMedia())
                ));
    }

    @Override
    public Flux<WhatsAppContact> findContactsByTenant(final String tenantId) {
        return contactRepository.findByTenantId(tenantId)
                .map(e -> new WhatsAppContact(
                        e.id(), e.phoneNumber(), e.name(), e.preferredLanguage(), e.tenantId()
                ));
    }

    @Override
    public Flux<WhatsAppMessage> findChatHistory(final String tenantId, final String phoneNumber) {
        return messageRepository.findConversationByTenantAndContact(tenantId, phoneNumber)
                .map(e -> new WhatsAppMessage(
                        e.id(), e.sender(), e.recipient(), e.originalText(), e.translatedText(),
                        e.sourceLanguage(), e.targetLanguage(), e.timestamp(), e.tenantId(),
                        e.outgoing(), e.translated(), e.status(), e.externalMessageId(), e.failureReason(), e.retryCount(),
                        e.messageKind() != null ? e.messageKind() : "TEXT",
                        e.mediaUrl(), e.mimeType(), e.caption(),
                        Boolean.TRUE.equals(e.hasMedia())
                ));
    }

    @Override
    public Mono<WhatsAppEvent> saveEvent(WhatsAppEvent event) {
        WhatsAppEventEntity entity = new WhatsAppEventEntity(
                event.id(), event.tenantId(), event.eventType(), event.rawPayload(),
                event.receivedAt(), event.status(), event.processingError()
        );
        return eventRepository.save(entity)
                .map(e -> new WhatsAppEvent(
                        e.id(), e.tenantId(), e.eventType(), e.rawPayload(),
                        e.receivedAt(), e.status(), e.processingError()
                ));
    }

    @Override
    public Mono<Void> updateEventStatus(String eventId, String status, String error) {
        return eventRepository.findById(eventId)
                .flatMap(entity -> {
                    WhatsAppEventEntity updated = new WhatsAppEventEntity(
                            entity.id(), entity.tenantId(), entity.eventType(),
                            entity.rawPayload(), entity.receivedAt(), status, error
                    );
                    return eventRepository.save(updated);
                })
                .then();
    }

    @Override
    public Mono<WhatsAppMessage> findByExternalId(String tenantId, String externalId) {
        return messageRepository.findByTenantIdAndExternalMessageId(tenantId, externalId)
                .map(this::mapToDomain);
    }

    private WhatsAppMessage mapToDomain(WhatsAppMessageEntity e) {
        return new WhatsAppMessage(
                e.id(), e.sender(), e.recipient(), e.originalText(), e.translatedText(),
                e.sourceLanguage(), e.targetLanguage(), e.timestamp(), e.tenantId(),
                e.outgoing(), e.translated(), e.status(), e.externalMessageId(),
                e.failureReason(), e.retryCount(),
                e.messageKind() != null ? e.messageKind() : "TEXT",
                e.mediaUrl(), e.mimeType(), e.caption(),
                Boolean.TRUE.equals(e.hasMedia())
        );
    }
}
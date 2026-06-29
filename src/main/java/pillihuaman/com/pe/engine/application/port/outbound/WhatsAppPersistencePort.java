package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.WhatsAppContact;
import pillihuaman.com.pe.engine.domain.model.WhatsAppEvent;
import pillihuaman.com.pe.engine.domain.model.WhatsAppMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface WhatsAppPersistencePort {
    Mono<WhatsAppMessage> saveMessage(WhatsAppMessage message);

    Mono<WhatsAppContact> saveContact(WhatsAppContact contact);

    Mono<WhatsAppContact> findContactByPhoneAndTenant(String phoneNumber, String tenantId);

    Flux<WhatsAppMessage> findMessagesByTenant(String tenantId);

    Flux<WhatsAppContact> findContactsByTenant(String tenantId);

    Flux<WhatsAppMessage> findChatHistory(String tenantId, String phoneNumber);

    Mono<WhatsAppEvent> saveEvent(WhatsAppEvent event);

    Mono<WhatsAppMessage> findByExternalId(String tenantId, String externalId);

    Mono<Void> updateEventStatus(String eventId, String status, String error);
}
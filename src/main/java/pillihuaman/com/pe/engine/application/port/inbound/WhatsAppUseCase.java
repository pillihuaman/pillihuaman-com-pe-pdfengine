package pillihuaman.com.pe.engine.application.port.inbound;

import pillihuaman.com.pe.engine.domain.model.ChannelStateDTO;
import pillihuaman.com.pe.engine.domain.model.WhatsAppContact;
import pillihuaman.com.pe.engine.domain.model.WhatsAppMessage;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface WhatsAppUseCase {
    Mono<Map<String, Object>> generateLinkQr(String tenantId, String bearerToken);

    Mono<WhatsAppMessage> handleIncomingWebhook(Map<String, Object> webhookData);

    Mono<WhatsAppMessage> sendOutgoingMessage(String tenantId, String recipientPhone, String text, String bearerToken);

    Mono<WhatsAppContact> registerOrUpdateContact(String tenantId, WhatsAppContact contact);

    Mono<List<WhatsAppContact>> getContacts(String tenantId);

    Mono<List<WhatsAppMessage>> getChatHistory(String tenantId, String phoneNumber);

    Mono<ChannelStateDTO> getLinkState(String tenantId, String bearerToken);

    Mono<WhatsAppMessage> sendOutgoingMessage(
            String tenantId, String recipientPhone, String text,
            String mediaBase64, String mimeType, String fileName, String bearerToken);
}
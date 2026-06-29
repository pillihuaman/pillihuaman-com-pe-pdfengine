package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.ChannelStateDTO;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface WhatsAppIntegrationPort {
    Mono<Map<String, Object>> getQrCode(String tenantId, String bearerToken);

    Mono<ChannelStateDTO> getLinkState(String tenantId, String bearerToken);

    Mono<ChannelStateDTO> checkState(String tenantId, String bearerToken);

    Mono<String> sendTranslatedMessage(
            String tenantId, String recipient, String messageText, String bearerToken);

    Mono<String> getBase64Media(String tenantId, Map<String, Object> messageNode);

    Mono<String> sendMediaMessage(
            String tenantId, String recipient, String mediaBase64, String mimeType,
            String fileName, String caption, String bearerToken);
}
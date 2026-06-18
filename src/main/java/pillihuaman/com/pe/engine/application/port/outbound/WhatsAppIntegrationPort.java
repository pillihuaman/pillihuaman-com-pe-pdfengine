package pillihuaman.com.pe.engine.application.port.outbound;

import reactor.core.publisher.Mono;

public interface WhatsAppIntegrationPort {
    Mono<String> getQrCode(String tenantId, String bearerToken);

    Mono<String> checkState(String tenantId, String bearerToken);

    Mono<Void> sendTranslatedMessage(String tenantId, String recipient, String messageText, String bearerToken);
}
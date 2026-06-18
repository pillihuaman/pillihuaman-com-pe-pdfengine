package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppIntegrationPort;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppIntegrationAdapter implements WhatsAppIntegrationPort {
    private final WebClient.Builder webClientBuilder;
    @Value("${external-api.evolution.url:https://evolution.alamodaperu.online}")
    private String evolutionUrl;
    @Value("${external-api.evolution.apikey:evolution-apikey-default}")
    private String apiKey;
    private final ObjectMapper objectMapper;

    private WebClient getClient() {
        return webClientBuilder.baseUrl(evolutionUrl).build();
    }

    @Override
    public Mono<String> getQrCode(String tenantId, String bearerToken) {
        return getClient().get()
                .uri("/instance/connect/" + tenantId)
                .header("apikey", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                // >>> CHANGE
                .onErrorResume(WebClientResponseException.NotFound.class, notFoundEx -> {
                    log.warn("Instance {} not found on Evolution API. Initiating dynamic auto-creation flow...", tenantId);

                    Map<String, Object> createBody = Map.of(
                            "instanceName", tenantId,
                            "token", apiKey,
                            "qrcode", true,
                            "integration", "WHATSAPP-BAILEYS"
                    );

                    return getClient().post()
                            .uri("/instance/create")
                            .header("apikey", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(createBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(createRes -> {
                                log.info("Successfully auto-created instance: {}. Retrying connect sequence.", tenantId);
                                return getClient().get()
                                        .uri("/instance/connect/" + tenantId)
                                        .header("apikey", apiKey)
                                        .retrieve()
                                        .bodyToMono(String.class);
                            });
                })
                // <<< CHANGE
                .doOnError(e -> log.error("Error fetching QR for tenant {}: {}", tenantId, e.getMessage()));
    }

    @Override
    public Mono<String> checkState(String tenantId, String bearerToken) {
        return getClient().get()
                .uri("/instance/connectionState/" + tenantId)
                .header("apikey", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorReturn("{\"state\": \"DISCONNECTED\"}");
    }

    @Override
    public Mono<Void> sendTranslatedMessage(String tenantId, String recipient, String messageText, String bearerToken) {
        // >>> CHANGE
        String uri = "/message/sendText/" + tenantId;

        Map<String, Object> body = Map.of(
                "number", recipient != null ? recipient : "",
                "options", Map.of("delay", 1200, "presence", "composing"),
                "text", messageText != null ? messageText : ""
        );

        try {
            String serializedBody = objectMapper.writeValueAsString(body);
            log.info("[WhatsAppIntegrationAdapter] OUTBOUND REQUEST TRACE:");
            log.info("  - Target URI: {}{}", evolutionUrl, uri);
            log.info("  - apikey Header: {}", apiKey != null ? "PRESENT (length=" + apiKey.length() + ")" : "NULL");
            log.info("  - Payload JSON: {}", serializedBody);
        } catch (Exception e) {
            log.error("[WhatsAppIntegrationAdapter] Failed to serialize payload trace: {}", e.getMessage());
        }

        return getClient().post()
                .uri(uri)
                .header("apikey", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                // >>> AÑADIMOS UN TIMEOUT DE 10 SEGUNDOS <<<
                .timeout(java.time.Duration.ofSeconds(10))
                .doOnNext(res -> log.info("[WhatsAppIntegrationAdapter] INBOUND RESPONSE TRACE: Received body: {}", res))
                .then()
                .doOnError(e -> log.error("[WhatsAppIntegrationAdapter] INBOUND ERROR TRACE: Failed to send text to {}: {}", recipient, e.getMessage(), e));
    }
}
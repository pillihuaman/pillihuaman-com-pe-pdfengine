package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppIntegrationPort;
import pillihuaman.com.pe.engine.domain.model.ChannelStateDTO;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
    public Mono<Map<String, Object>> getQrCode(String tenantId, String bearerToken) {
        return getClient().get()
                .uri("/instance/connect/" + tenantId)
                .header("apikey", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(Duration.ofSeconds(30))
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
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                            })
                            .flatMap(createRes -> {
                                log.info("Successfully auto-created instance: {}. Retrying connect sequence.", tenantId);
                                return getClient().get()
                                        .uri("/instance/connect/" + tenantId)
                                        .header("apikey", apiKey)
                                        .retrieve()
                                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                                        });
                            });
                })
                .doOnError(e -> log.error("Error fetching QR for tenant {}: {}", tenantId, e.getMessage()));
    }

    @Override
    public Mono<ChannelStateDTO> getLinkState(final String tenantId, final String bearerToken) {
        return checkState(tenantId, bearerToken);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ChannelStateDTO> checkState(String tenantId, String bearerToken) {
        return getClient().get()
                .uri("/instance/connectionState/" + tenantId)
                .header("apikey", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .map(res -> {
                    Map<String, Object> instance = (Map<String, Object>) res.get("instance");

                    String rawState = instance != null
                            ? String.valueOf(instance.getOrDefault("state", "DISCONNECTED"))
                            : String.valueOf(res.getOrDefault("state", "DISCONNECTED"));

                    String normalized = normalizeState(rawState);

                    return new ChannelStateDTO(
                            "WHATSAPP",
                            normalized,
                            tenantId,
                            Instant.now(),
                            rawState,
                            !"CONNECTED".equals(normalized)
                    );
                })
                .onErrorResume(e -> Mono.just(
                        new ChannelStateDTO(
                                "WHATSAPP", "ERROR", tenantId, Instant.now(), e.getMessage(), true
                        )
                ));
    }

    private String normalizeState(String rawState) {
        if (rawState == null) {
            return "DISCONNECTED";
        }

        return switch (rawState.toLowerCase()) {
            case "open", "connected" -> "CONNECTED";
            case "connecting", "pairing", "qr", "loading" -> "CONNECTING";
            case "disconnected", "close", "closed" -> "DISCONNECTED";
            default -> "ERROR";
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<String> sendTranslatedMessage(
            String tenantId, String recipient, String messageText, String bearerToken) {
        // >>> CHANGE
        final String uri = "/message/sendText/" + tenantId;
        final Map<String, Object> body = Map.of(
                "number", recipient,
                "options", Map.of("delay", 1200, "presence", "composing"),
                "text", messageText
        );

        return getClient().post()
                .uri(uri)
                .header("apikey", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.error("[EVOLUTION-ERROR] Status: {} | Body: {}", response.statusCode(), err);
                                    return Mono.error(new RuntimeException("API Failure: " + err));
                                })
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .map(res -> {
                    final Map<String, Object> key = (Map<String, Object>) res.get("key");
                    return (key != null && key.containsKey("id"))
                            ? (String) key.get("id")
                            : UUID.randomUUID().toString();
                })
                .timeout(Duration.ofSeconds(20))
                .doOnError(e -> log.error("[EVOLUTION-FAILED] Send failed to {}: {}", recipient, e.getMessage()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<String> sendMediaMessage(
            String tenantId, String recipient, String mediaBase64, String mimeType,
            String fileName, String caption, String bearerToken) {

        final String uri = "/message/sendMedia/" + tenantId;

        String mediaType = "document";
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) mediaType = "image";
            else if (mimeType.startsWith("video/")) mediaType = "video";
            else if (mimeType.startsWith("audio/")) mediaType = "audio";
        }

        // >>> CHANGE: Aplanamos el payload. Evolution lo espera en la raíz, no anidado.
        final Map<String, Object> body = new java.util.HashMap<>();
        body.put("number", recipient);
        body.put("options", Map.of("delay", 1200, "presence", "composing"));
        body.put("mediatype", mediaType);
        body.put("media", mediaBase64);

        if (caption != null && !caption.isBlank()) body.put("caption", caption);
        if (fileName != null && !fileName.isBlank()) body.put("fileName", fileName);
        if (mimeType != null && !mimeType.isBlank()) body.put("mimetype", mimeType);
        // <<< CHANGE

        return getClient().post()
                .uri(uri)
                .header("apikey", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.error("[EVOLUTION-ERROR] Media Status: {} | Body: {}", response.statusCode(), err);
                                    return Mono.error(new RuntimeException("API Media Failure: " + err));
                                })
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .map(res -> {
                    final Map<String, Object> key = (Map<String, Object>) res.get("key");
                    return (key != null && key.containsKey("id"))
                            ? (String) key.get("id")
                            : UUID.randomUUID().toString();
                })
                .timeout(Duration.ofSeconds(45)) // Mayor timeout para archivos grandes
                .doOnError(e -> log.error("[EVOLUTION-FAILED] Media send failed to {}: {}", recipient, e.getMessage()));
    }

    @Override
    public Mono<String> getBase64Media(String tenantId, Map<String, Object> fullMessageData) {
        final String uri = "/chat/getBase64FromMediaMessage/" + tenantId;

        // Evolution API v2 requiere { "message": { "key": {...}, "message": {...} } }
        // El parámetro fullMessageData YA contiene "key" y "message" extraído del webhook("data")
        final Map<String, Object> body = Map.of("message", fullMessageData);

        return getClient().post()
                .uri(uri)
                .header("apikey", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.error("[EVOLUTION-MEDIA-ERROR] 400 Bad Request. Body de Evolution: {}", err);
                                    return Mono.error(new RuntimeException("API Failure: " + err));
                                })
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .map(res -> {
                    String base64 = (String) res.get("base64");
                    if (base64 != null && base64.contains(",")) {
                        return base64.split(",")[1];
                    }
                    return base64 != null ? base64 : "";
                })
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(e -> {
                    log.error("[EVOLUTION-MEDIA] Falló el descifrado para tenant {}: {}", tenantId, e.getMessage());
                    return Mono.empty();
                });
    }
}
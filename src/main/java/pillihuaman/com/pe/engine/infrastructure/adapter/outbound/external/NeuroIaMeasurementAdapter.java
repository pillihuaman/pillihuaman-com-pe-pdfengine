package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pillihuaman.com.pe.engine.application.dto.UserMeasurementMetadata;
import pillihuaman.com.pe.engine.application.port.outbound.AiMeasurementPort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.model.measurement.BodyMeasurementResult;
import pillihuaman.com.pe.engine.infrastructure.common.ReqBase;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class NeuroIaMeasurementAdapter implements AiMeasurementPort {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${neuro-ia.api.url}")
    private String neuroIaUrl;

    public Mono<BodyMeasurementResult> extractMeasurements(
            final String systemPrompt,
            final UserMeasurementMetadata metadata,
            final String bearerToken) {
        log.info("[NeuroIA-Adapter] Requesting anthropometric analysis to centralized NeuroIA MS...");

        final ReqBase<Map<String, Object>> request = new ReqBase<>();
        request.setPayload(
                Map.of(
                        "systemPrompt", systemPrompt,
                        "userMetadata", metadata));

        return webClientBuilder
                .build()
                .post()
                .uri(neuroIaUrl + "/private/v1/ia/iaService/extract-measurements")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                // >>> CHANGE: Añadido log para observabilidad del raw payload antes de ser procesado
                .doOnNext(raw -> log.info("[NeuroIA-Adapter] Received RAW response: {}", raw))
                // <<< CHANGE
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(3)).filter(this::isTransientError))
                .flatMap(this::parseNeuroIaResponse);
    }

    private boolean isTransientError(final Throwable throwable) {
        if (throwable instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError() || wcre.getStatusCode().value() == 429;
        }
        return throwable instanceof java.util.concurrent.TimeoutException;
    }

    private Mono<BodyMeasurementResult> parseNeuroIaResponse(final String rawResponse) {
        return Mono.fromCallable(
                        () -> {
                            final RespBase<BodyMeasurementResult> response =
                                    objectMapper.readValue(
                                            rawResponse, new TypeReference<RespBase<BodyMeasurementResult>>() {
                                            });

                            if (response != null && response.getPayload() != null) {
                                // Aceptar el payload si no hay un status de error EXPLÍCITO.
                                if (response.getStatus() == null || !Boolean.FALSE.equals(response.getStatus().getSuccess())) {
                                    return response.getPayload();
                                }
                            }

                            final String errorMsg =
                                    (response != null
                                            && response.getStatus() != null
                                            && response.getStatus().getError() != null
                                            && response.getStatus().getError().getMessages() != null)
                                            ? String.join(", ", response.getStatus().getError().getMessages())
                                            : "Invalid RespBase structure. Raw: " + rawResponse;

                            throw new BusinessLogicException("NeuroIA MS rejected the request: " + errorMsg);
                        })
                .onErrorMap(e -> {
                    if (e instanceof BusinessLogicException) {
                        return e;
                    }
                    return new RuntimeException("Failed to map response from NeuroIA MS", e);
                });
    }
}
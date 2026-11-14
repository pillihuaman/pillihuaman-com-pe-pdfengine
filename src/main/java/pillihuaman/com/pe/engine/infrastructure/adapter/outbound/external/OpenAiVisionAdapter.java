package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pillihuaman.com.pe.engine.application.dto.UserMeasurementMetadata;
import pillihuaman.com.pe.engine.application.port.outbound.AiMeasurementPort;
import pillihuaman.com.pe.engine.domain.model.measurement.BodyMeasurementResult;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiVisionAdapter implements AiMeasurementPort {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:default-secret-key}")
    private String openAiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    @Override
    public Mono<BodyMeasurementResult> extractMeasurements(
            final String systemPrompt,
            final UserMeasurementMetadata metadata,
            final String bearerToken) {

        // >>> CHANGE
        final var request = OpenAiPayloadBuilder.buildRequest(systemPrompt);
        // <<< CHANGE

        if (log.isInfoEnabled()) {
            log.info("Sending prompt to OpenAI Vision:\n{}", systemPrompt);
        }
        return webClientBuilder
                .build()
                .post()
                .uri(OPENAI_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiPayloadBuilder.OpenAiResponse.class)
                .timeout(Duration.ofSeconds(15))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(3)).filter(this::isTransientError))
                .flatMap(this::parseResponse);
    }

    private boolean isTransientError(final Throwable throwable) {
        if (throwable instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError() || wcre.getStatusCode().value() == 429;
        }
        return throwable instanceof java.util.concurrent.TimeoutException;
    }

    private Mono<BodyMeasurementResult> parseResponse(
            final OpenAiPayloadBuilder.OpenAiResponse response) {

        return Mono.fromCallable(
                        () -> {
                            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                                throw new IllegalStateException("Empty response from OpenAI");
                            }
                            final String jsonContent = response.choices().get(0).message().content();
                            return objectMapper.readValue(jsonContent, BodyMeasurementResult.class);
                        })
                .onErrorMap(e -> new RuntimeException("Failed to parse OpenAI Vision output", e));
    }
}
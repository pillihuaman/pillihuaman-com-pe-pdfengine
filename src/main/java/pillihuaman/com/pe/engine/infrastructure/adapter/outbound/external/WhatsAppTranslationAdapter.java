package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pillihuaman.com.pe.engine.application.port.outbound.WhatsAppTranslationPort;
import pillihuaman.com.pe.engine.infrastructure.common.ReqBase;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppTranslationAdapter implements WhatsAppTranslationPort {
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    @Value("${neuro-ia.api.url:http://localhost:8099}")
    private String neuroIaUrl;

    @Override
    public Mono<String> translateText(String text, String sourceLang, String targetLang, String bearerToken) {
        ReqBase<Map<String, String>> request = new ReqBase<>();
        request.setPayload(Map.of(
                "text", text,
                "sourceLanguage", sourceLang,
                "targetLanguage", targetLang,
                "model", "deepseek-chat",
                "domain", "commerce"
        ));
        return webClientBuilder.build().post()
                .uri(neuroIaUrl + "/private/v1/ia/iaService/translate-chat")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .retry(2)
                .map(rawResponse -> {
                    try {
                        RespBase<Map<String, String>> response = objectMapper.readValue(
                                rawResponse, new TypeReference<RespBase<Map<String, String>>>() {
                                });
                        if (response != null && response.getPayload() != null) {
                            return response.getPayload().getOrDefault("translatedText", text);
                        }
                    } catch (Exception e) {
                        log.error("Failed parsing translation response: {}", e.getMessage());
                    }
                    return text;
                })
                .onErrorResume(e -> {
                    log.warn("Translation failed: {}. Falling back to original.", e.getMessage());
                    return Mono.just(text);
                });
    }

    
}
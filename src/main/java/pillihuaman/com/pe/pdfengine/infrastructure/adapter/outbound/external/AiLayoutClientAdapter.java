package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.external;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pillihuaman.com.pe.pdfengine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.pdfengine.domain.model.external.AiClassificationResponse;
import pillihuaman.com.pe.pdfengine.domain.model.external.AiLayoutRequest;
import pillihuaman.com.pe.pdfengine.infrastructure.common.ReqBase;
import pillihuaman.com.pe.pdfengine.infrastructure.common.RespBase;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.reflections.Reflections.log;

@Component
public class AiLayoutClientAdapter {

    private final WebClient webClient;
    private final String neuroIaUrl;
    private final ObjectMapper objectMapper;

    public AiLayoutClientAdapter(WebClient.Builder builder, @Value("${neuro-ia.api.url}") String neuroIaUrl, ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.neuroIaUrl = neuroIaUrl;
        this.objectMapper = objectMapper;
    }


    public Mono<PdfEditableStructure> callNeuroIaToNormalize(PdfEditableStructure rawData, String promptTemplate, String bearerToken) {

        AiLayoutRequest payload = AiLayoutRequest.builder().systemPrompt(promptTemplate).rawPdfData(rawData).build();

        ReqBase<AiLayoutRequest> request = new ReqBase<>();
        request.setPayload(payload);

        return webClient.post().uri(neuroIaUrl + "/private/v1/ia/iaService/normalize-pdf-layout").header(HttpHeaders.AUTHORIZATION, bearerToken).contentType(MediaType.APPLICATION_JSON).bodyValue(request)

                // 🔥 LOG RESPONSE RAW GLOBAL
                .retrieve().bodyToMono(String.class)

                .doOnNext(rawResponse -> {
                    log.info("========== [IA RESPONSE RAW - WebClient] ==========");
                    log.info("Respuesta cruda IA:\n{}", rawResponse);
                })

                .flatMap(rawResponse -> {
                    try {

                        String cleanJson = rawResponse.trim();

                        if (cleanJson.contains("```json")) {
                            cleanJson = cleanJson.substring(cleanJson.indexOf("```json") + 7);
                            cleanJson = cleanJson.substring(0, cleanJson.lastIndexOf("```"));
                        } else if (cleanJson.contains("```")) {
                            cleanJson = cleanJson.substring(cleanJson.indexOf("```") + 3);
                            cleanJson = cleanJson.substring(0, cleanJson.lastIndexOf("```"));
                        }

                        // 🔥 LOG CLEAN JSON
                        log.info("========== [IA CLEAN JSON] ==========");
                        log.info("JSON limpio:\n{}", cleanJson);

                        RespBase<PdfEditableStructure> response = objectMapper.readValue(cleanJson, new TypeReference<RespBase<PdfEditableStructure>>() {
                        });

                        if (response != null && response.getStatus().getSuccess() && response.getPayload() != null) {

                            log.info("========== [IA SUCCESS] ==========");
                            return Mono.just(response.getPayload());
                        }

                        log.error("AI Response success but payload is empty. Raw: {}", cleanJson);
                        return Mono.error(new RuntimeException("NeuroIA returned empty payload"));

                    } catch (Exception e) {
                        log.error("Failed to map AI response: ", e);
                        return Mono.error(e);
                    }
                });
    }

    public Mono<List<AiClassificationResponse.ClassificationItem>> callNeuroIaToClassify(
            List<Map<String, String>> minimalElements,
            String promptTemplate,
            String bearerToken) {

        // Construimos el request con el campo minimalData
        AiLayoutRequest payload = AiLayoutRequest.builder()
                .systemPrompt(promptTemplate)
                .minimalData(minimalElements) // Enviamos solo ID y Texto
                .build();

        ReqBase<AiLayoutRequest> request = new ReqBase<>();
        request.setPayload(payload);

        return webClient.post()
                .uri(neuroIaUrl + "/private/v1/ia/iaService/normalize-pdf-layout")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<RespBase<AiClassificationResponse>>() {
                })
                .flatMap(response -> {
                    // Validación de seguridad de la respuesta
                    if (response != null && response.getStatus().getSuccess() && response.getPayload() != null) {
                        return Mono.just(response.getPayload().classifications());
                    }
                    return Mono.error(new RuntimeException("NeuroIA returned invalid classification payload"));
                });
    }
}


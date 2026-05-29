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

import java.util.HashMap;
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

        AiLayoutRequest payload = AiLayoutRequest.builder()
                .systemPrompt(promptTemplate)
                .minimalData(minimalElements)
                .build();

        ReqBase<AiLayoutRequest> request = new ReqBase<>();
        request.setPayload(payload);

        return webClient.post()
                .uri(neuroIaUrl + "/private/v1/ia/iaService/normalize-pdf-layout")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(rawResponse -> {
                    try {
                        String cleanJson = rawResponse.trim();
                        // Limpieza de Markdown si la IA lo devuelve
                        if (cleanJson.contains("```json")) {
                            cleanJson = cleanJson.substring(cleanJson.indexOf("```json") + 7);
                            cleanJson = cleanJson.substring(0, cleanJson.lastIndexOf("```"));
                        }

                        // >>> CHANGE: Mapeo a la estructura de clasificación, NO a PdfEditableStructure
                        RespBase<AiClassificationResponse> response = objectMapper.readValue(
                                cleanJson, new TypeReference<RespBase<AiClassificationResponse>>() {
                                });

                        if (response != null && response.getStatus().getSuccess() && response.getPayload() != null) {
                            return Mono.just(response.getPayload().classifications());
                        }
                        return Mono.error(new RuntimeException("IA Response payload is null"));
                    } catch (Exception e) {
                        log.error("Failed to parse IA classification: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }


    public Mono<PdfEditableStructure> callNeuroIaToRefineVisuals(
            PdfEditableStructure layout,
            String base64Image,
            String promptTemplate, // >>> CHANGE: Template from support-ms
            String bearerToken) {

        // >>> CHANGE
        // Construimos un payload que incluya el systemPrompt y el contexto visual
        // Reutilizamos el objeto ReqBase para consistencia en el protocolo
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("systemPrompt", promptTemplate);
        payloadMap.put("rawPdfData", layout);
        payloadMap.put("screenshot", base64Image);

        ReqBase<Map<String, Object>> request = new ReqBase<>();
        request.setPayload(payloadMap);

        return webClient.post()
                .uri(neuroIaUrl + "/private/v1/ia/iaService/normalize-pdf-layout")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(rawResponse -> {
                    try {
                        String cleanJson = cleanMarkdown(rawResponse);
                        log.debug("NeuroIA Refined JSON: {}", cleanJson);

                        RespBase<PdfEditableStructure> response = objectMapper.readValue(
                                cleanJson, new TypeReference<RespBase<PdfEditableStructure>>() {
                                });

                        if (response != null && response.getPayload() != null) {
                            return Mono.just(response.getPayload());
                        }
                        return Mono.error(new RuntimeException("Fidelity Refinement returned empty payload"));
                    } catch (Exception e) {
                        log.error("Failed to parse Fidelity Refinement response: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }

    private String cleanMarkdown(String json) {
        if (json.contains("```json")) {
            return json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```")).trim();
        }
        return json.trim();
    }
}


package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PromptExternalPort;
import pillihuaman.com.pe.pdfengine.domain.model.external.PromptAndVersionDTO;
import pillihuaman.com.pe.pdfengine.domain.model.external.ReqPrompt;
import pillihuaman.com.pe.pdfengine.infrastructure.common.ReqBase;
import pillihuaman.com.pe.pdfengine.infrastructure.common.RespBase;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class PromptRestAdapter implements PromptExternalPort {

    private final WebClient webClient;
    private final String supportServiceUrl;


    public PromptRestAdapter(
            WebClient.Builder webClientBuilder, // <<< ESTE ES EL BEAN QUE FALTABA
            @Value("${external-api.support-ms.url}") String supportServiceUrl) {

        // >>> CHANGE
        this.webClient = webClientBuilder.build();
        // <<< CHANGE
        this.supportServiceUrl = supportServiceUrl;
    }

    @Override
    public Mono<PromptAndVersionDTO> getPromptByCharacteristics(ReqPrompt payload, String bearerToken) {
        // REGION: Request Construction
        ReqBase<ReqPrompt> request = new ReqBase<>();
        request.setPayload(payload);

        return webClient.post()
                .uri(supportServiceUrl + "/private/v1/support/prompt/characteristics")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<RespBase<PromptAndVersionDTO>>() {
                })
                .flatMap(response -> {
                    if (response != null && response.getStatus().getSuccess()) {
                        return Mono.just(response.getPayload());
                    }
                    return Mono.error(new RuntimeException("Error calling support service: " +
                            (response != null ? response.getStatus().getError().getMessages() : "Unknown error")));
                })
                .doOnSuccess(dto -> log.info("Successfully retrieved prompt template for code: {}", payload.getCode()))
                .doOnError(e -> log.error("Failed to retrieve prompt from support service: {}", e.getMessage()));
    }
}
package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pillihuaman.com.pe.engine.application.port.outbound.PromptExternalPort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.exception.ResourceNotFoundException;
import pillihuaman.com.pe.engine.domain.model.external.PromptAndVersionDTO;
import pillihuaman.com.pe.engine.domain.model.external.ReqPrompt;
import pillihuaman.com.pe.engine.infrastructure.common.ReqBase;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
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
    public Mono<PromptAndVersionDTO> getPromptByCharacteristics(
            final ReqPrompt payload, final String bearerToken) {
        final ReqBase<ReqPrompt> request = new ReqBase<>();
        request.setPayload(payload);

        return webClient
                .post()
                .uri(supportServiceUrl + "/private/v1/support/prompt/characteristics")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        response ->
                                Mono.error(
                                        new ResourceNotFoundException(
                                                "Prompt not found with the given characteristics: " + payload.getCode())))
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response ->
                                response
                                        .bodyToMono(String.class)
                                        .flatMap(
                                                body ->
                                                        Mono.error(
                                                                new BusinessLogicException("Support service error: " + body))))
                .bodyToMono(new ParameterizedTypeReference<RespBase<PromptAndVersionDTO>>() {
                })
                .flatMap(
                        response -> {
                            if (response != null && Boolean.TRUE.equals(response.getStatus().getSuccess())) {
                                return Mono.just(response.getPayload());
                            }
                            final String errorMsg =
                                    (response != null && response.getStatus().getError() != null)
                                            ? String.join(", ", response.getStatus().getError().getMessages())
                                            : "Unknown error";
                            return Mono.error(
                                    new BusinessLogicException("Error calling support service: " + errorMsg));
                        })
                .doOnSuccess(
                        dto -> log.info("Successfully retrieved prompt template for code: {}", payload.getCode()))
                .doOnError(
                        e -> log.error("Failed to retrieve prompt from support service: {}", e.getMessage()));
    }
}
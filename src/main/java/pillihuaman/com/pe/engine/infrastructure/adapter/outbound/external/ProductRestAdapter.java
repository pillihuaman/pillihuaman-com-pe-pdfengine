package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pillihuaman.com.pe.engine.application.port.outbound.ProductExternalPort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.exception.ResourceNotFoundException;
import pillihuaman.com.pe.engine.domain.model.measurement.Garment;
import pillihuaman.com.pe.engine.domain.model.measurement.ProductMeasurementModel;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enterprise WebClient adapter to communicate with the Support Microservice. Follows Port & Adapters
 * pattern by implementing ProductExternalPort to fetch garments.
 */
@Slf4j
@Component
public class ProductRestAdapter implements ProductExternalPort {

    private final WebClient webClient;
    private final String supportServiceUrl;

    public ProductRestAdapter(
            final WebClient.Builder webClientBuilder,
            @Value("${external-api.support-ms.url}") final String supportServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.supportServiceUrl = supportServiceUrl;
    }

    @Override
    public Mono<Garment> getProductById(final String productId, final String bearerToken) {
        return webClient
                .get()
                .uri(supportServiceUrl + "/private/v1/support/product/" + productId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        response ->
                                Mono.error(
                                        new ResourceNotFoundException("Product not found in Support MS: " + productId)))
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response ->
                                response
                                        .bodyToMono(String.class)
                                        .flatMap(
                                                body -> Mono.error(new BusinessLogicException("Support API error: " + body))))
                .bodyToMono(new ParameterizedTypeReference<RespBase<Map<String, Object>>>() {
                })
                .flatMap(
                        response -> {
                            if (response != null
                                    && Boolean.TRUE.equals(response.getStatus().getSuccess())
                                    && response.getPayload() != null) {
                                return Mono.just(mapToGarment(response.getPayload()));
                            }
                            return Mono.error(
                                    new BusinessLogicException("Invalid response for product: " + productId));
                        })
                .doOnSuccess(
                        g -> log.info("Successfully retrieved garment {} from Support MS", productId))
                .doOnError(e -> log.error("Failed to retrieve garment {}: {}", productId, e.getMessage()));
    }

    @SuppressWarnings("unchecked")
    private Garment mapToGarment(final Map<String, Object> payload) {
        final String id = payload.containsKey("id") ? String.valueOf(payload.get("id")) : "UNKNOWN";
        final String category =
                payload.containsKey("category") ? String.valueOf(payload.get("category")) : "Desconocida";

        List<ProductMeasurementModel> measurements = Collections.emptyList();

        if (payload.containsKey("measurements") && payload.get("measurements") instanceof List) {
            final List<Map<String, Object>> measList =
                    (List<Map<String, Object>>) payload.get("measurements");

            measurements =
                    measList.stream()
                            .map(
                                    m ->
                                            new ProductMeasurementModel(
                                                    m.containsKey("size") ? String.valueOf(m.get("size")) : "N/A",
                                                    parseDouble(m.get("chestContour")),
                                                    parseDouble(m.get("shoulderWidth")),
                                                    parseDouble(m.get("totalLength")),
                                                    parseDouble(m.get("sleeveLength"))))
                            .collect(Collectors.toList());
        }

        return new Garment(id, category, measurements);
    }

    private Double parseDouble(final Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
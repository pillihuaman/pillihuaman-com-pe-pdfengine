package pillihuaman.com.pe.engine.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import pillihuaman.com.pe.engine.application.dto.UserMeasurementMetadata;
import pillihuaman.com.pe.engine.application.port.inbound.MeasurementEngineUseCase;
import pillihuaman.com.pe.engine.domain.model.measurement.MeasurementHistory;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import reactor.core.publisher.Mono;


@Slf4j
@RestController
@RequestMapping("/private/v1/measurements")
@RequiredArgsConstructor
public class VirtualMeasurementController {
    private final MeasurementEngineUseCase measurementEngineUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/ephemeral-analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RespBase<MeasurementHistory>> analyzeEphemeral(
            @RequestHeader(name = "X-Tenant-ID", defaultValue = "DEFAULT") final String tenantId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) final String bearerToken,
            @RequestParam("userId") final String userId,
            @RequestParam("garmentId") final String garmentId,
            @RequestBody final UserMeasurementMetadata metadata) {

        log.info("Receiving ZERO-IMAGE anthropometric request for user: {} | Garment: {} | Tenant: {}", userId, garmentId, tenantId);

        return Mono.defer(() -> {
            try {
                // Al ser un endpoint 100% JSON y determinístico, enviamos directamente la metadata
                return measurementEngineUseCase.processEphemeralMeasurement(
                        tenantId,
                        userId,
                        garmentId,
                        metadata,
                        bearerToken);
            } catch (Exception e) {
                log.error("Error delegando flujo Edge AI: {}", e.getMessage());
                return Mono.error(new RuntimeException("Error en procesamiento biométrico: " + e.getMessage()));
            }
        }).map(history -> new RespBase<MeasurementHistory>().ok(history));
    }
}
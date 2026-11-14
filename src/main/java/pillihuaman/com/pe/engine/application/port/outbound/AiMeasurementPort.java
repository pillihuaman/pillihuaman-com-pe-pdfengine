package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.application.dto.UserMeasurementMetadata;
import pillihuaman.com.pe.engine.domain.model.measurement.BodyMeasurementResult;
import reactor.core.publisher.Mono;

public interface AiMeasurementPort {
    Mono<BodyMeasurementResult> extractMeasurements(

            final String systemPrompt,
            final UserMeasurementMetadata metadata,
            final String bearerToken);
}

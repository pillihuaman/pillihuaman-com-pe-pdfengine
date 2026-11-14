package pillihuaman.com.pe.engine.application.port.inbound;

import pillihuaman.com.pe.engine.application.dto.UserMeasurementMetadata;
import pillihuaman.com.pe.engine.domain.model.measurement.MeasurementHistory;
import reactor.core.publisher.Mono;

public interface MeasurementEngineUseCase {

    Mono<MeasurementHistory> processEphemeralMeasurement(
            String tenantId,
            String userId,
            String garmentId,
            UserMeasurementMetadata metadata,
            String bearerToken);

}
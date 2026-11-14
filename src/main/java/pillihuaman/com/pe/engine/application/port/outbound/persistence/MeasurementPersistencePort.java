package pillihuaman.com.pe.engine.application.port.outbound.persistence;

import pillihuaman.com.pe.engine.domain.model.measurement.MeasurementHistory;
import reactor.core.publisher.Mono;

public interface MeasurementPersistencePort {
    Mono<MeasurementHistory> save(MeasurementHistory history);
}

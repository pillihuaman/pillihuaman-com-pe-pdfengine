package pillihuaman.com.pe.engine.application.port.outbound.persistence;

import pillihuaman.com.pe.engine.domain.model.measurement.Garment;
import reactor.core.publisher.Mono;

public interface GarmentPersistencePort {
    Mono<Garment> findById(String garmentId);
}
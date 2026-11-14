package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.persistence.GarmentPersistencePort;
import pillihuaman.com.pe.engine.domain.model.measurement.Garment;
import pillihuaman.com.pe.engine.domain.model.measurement.ProductMeasurementModel;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GarmentMongoAdapter implements GarmentPersistencePort {

    private final GarmentRepository repository;

    @Override
    public Mono<Garment> findById(final String garmentId) {
        return repository.findById(garmentId)
                .map(entity -> new Garment(
                        entity.id(),
                        entity.category(),
                        entity.measurements() != null ? entity.measurements().stream()
                                .map(m -> new ProductMeasurementModel(
                                        m.size(),
                                        m.chestContour(),
                                        m.shoulderWidth(),
                                        m.totalLength(),
                                        m.sleeveLength()
                                ))
                                .collect(Collectors.toList()) : Collections.emptyList()
                ));
    }
}
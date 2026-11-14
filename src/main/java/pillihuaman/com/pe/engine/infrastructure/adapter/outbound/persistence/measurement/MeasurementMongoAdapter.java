package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.persistence.MeasurementPersistencePort;
import pillihuaman.com.pe.engine.domain.model.measurement.MeasurementHistory;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MeasurementMongoAdapter implements MeasurementPersistencePort {

    private final MeasurementHistoryRepository repository;

    @Override
    public Mono<MeasurementHistory> save(final MeasurementHistory history) {
        final MeasurementHistoryEntity entity = new MeasurementHistoryEntity(
                history.id(),
                history.userId(),
                history.garmentId(),
                history.chest(),
                history.waist(),
                history.hips(),
                history.recommendedSize(),
                // >>> CHANGE
                history.matchSuccess(),
                history.explanation(),
                history.warningMessage(),
                // <<< CHANGE
                history.createdAt()
        );

        return repository.save(entity)
                .map(saved -> new MeasurementHistory(
                        saved.id(),
                        saved.userId(),
                        saved.garmentId(),
                        saved.chest(),
                        saved.waist(),
                        saved.hips(),
                        saved.recommendedSize(),
                        saved.matchSuccess(),
                        saved.explanation(),
                        saved.warningMessage(),
                        saved.createdAt()
                ));
    }
}
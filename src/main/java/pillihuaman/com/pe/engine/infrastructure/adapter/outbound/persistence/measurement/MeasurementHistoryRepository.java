package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeasurementHistoryRepository extends ReactiveMongoRepository<MeasurementHistoryEntity, String> {
}

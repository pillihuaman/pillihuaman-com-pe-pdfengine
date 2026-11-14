package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "garments")
public record GarmentEntity(
        @Id String id,
        String category,
        List<ProductMeasurementEntity> measurements
) {
}
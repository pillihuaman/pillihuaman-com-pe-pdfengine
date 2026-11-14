package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "measurement_history")
public record MeasurementHistoryEntity(
        @Id String id,
        String userId,
        String garmentId,
        double chest,
        double waist,
        double hips,
        String recommendedSize,
        boolean matchSuccess,
        String explanation,
        String warningMessage,
        Instant createdAt

) {
}

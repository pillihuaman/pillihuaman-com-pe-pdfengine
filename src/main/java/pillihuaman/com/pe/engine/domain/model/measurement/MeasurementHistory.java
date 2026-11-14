package pillihuaman.com.pe.engine.domain.model.measurement;

import java.io.Serializable;
import java.time.Instant;

public record MeasurementHistory(
        String id,
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

) implements Serializable {
}
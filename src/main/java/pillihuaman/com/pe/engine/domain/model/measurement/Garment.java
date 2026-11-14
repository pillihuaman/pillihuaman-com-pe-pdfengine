package pillihuaman.com.pe.engine.domain.model.measurement;

import java.io.Serializable;
import java.util.List;

public record Garment(
        String id,
        String category,
        List<ProductMeasurementModel> measurements
) implements Serializable {
}
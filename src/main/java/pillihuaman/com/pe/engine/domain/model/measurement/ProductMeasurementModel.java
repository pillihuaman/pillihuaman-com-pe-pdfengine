package pillihuaman.com.pe.engine.domain.model.measurement;

import java.io.Serializable;

public record ProductMeasurementModel(
        String size,
        Double chestContour,
        Double shoulderWidth,
        Double totalLength,
        Double sleeveLength
) implements Serializable {
}
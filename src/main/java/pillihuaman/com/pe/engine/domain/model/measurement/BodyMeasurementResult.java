package pillihuaman.com.pe.engine.domain.model.measurement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BodyMeasurementResult(
        double chest,
        double waist,
        double hips,
        String clothingValidation,
        String status,
        Double shoulderWidthCm,
        Double armLengthCm
) implements Serializable {
}
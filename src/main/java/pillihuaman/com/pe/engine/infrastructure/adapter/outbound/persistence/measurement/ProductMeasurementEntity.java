package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

public record ProductMeasurementEntity(
        String size,
        Double chestContour,
        Double shoulderWidth,
        Double totalLength,
        Double sleeveLength
) {
}
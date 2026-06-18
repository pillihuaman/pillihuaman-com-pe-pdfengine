package pillihuaman.com.pe.engine.domain.model.external;


import java.util.List;

public record AiClassificationResponse(
        List<ClassificationItem> classifications
) {
    public record ClassificationItem(String id, String type) {
    }
}
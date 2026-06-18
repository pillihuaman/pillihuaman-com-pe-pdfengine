package pillihuaman.com.pe.engine.domain.model.external;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespPrompt {
    private JsonNode id;
    private String code;
    private String name;
    private String category;
    private String activeVersionId;
}
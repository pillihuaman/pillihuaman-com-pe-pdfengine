package pillihuaman.com.pe.engine.domain.model.external;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVersion {
    private String version;
    private String template;
    private List<String> variables;

    private JsonNode id;
    private JsonNode promptId;
}
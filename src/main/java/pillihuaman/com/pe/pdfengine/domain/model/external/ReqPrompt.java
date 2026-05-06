package pillihuaman.com.pe.pdfengine.domain.model.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqPrompt {
    private String id;
    private String code;
    private String name;
    private String category;
    private ModelConfigDTO modelConfig;

    @Data
    public static class ModelConfigDTO {
        private Double temperature;
        private Integer maxTokens;
        private String modelName;
    }
}
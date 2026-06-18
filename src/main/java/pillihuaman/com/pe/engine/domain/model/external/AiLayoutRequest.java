package pillihuaman.com.pe.engine.domain.model.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLayoutRequest {
    private String systemPrompt;
    private PdfEditableStructure rawPdfData;
    private List<Map<String, String>> minimalData;
}
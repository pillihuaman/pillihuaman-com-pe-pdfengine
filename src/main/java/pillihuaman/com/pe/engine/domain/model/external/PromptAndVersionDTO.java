package pillihuaman.com.pe.engine.domain.model.external;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PromptAndVersionDTO {
    private RespPrompt prompt;
    private PromptVersion finalVersion;
}
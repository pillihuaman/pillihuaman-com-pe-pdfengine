package pillihuaman.com.pe.engine.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfFidelityRequest(
        @JsonProperty("layout")
        PdfEditableStructure layout,
        @JsonProperty("screenshot")
        String screenshot
) {
}
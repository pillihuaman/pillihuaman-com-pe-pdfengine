package pillihuaman.com.pe.pdfengine.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pillihuaman.com.pe.pdfengine.domain.model.PdfEditableStructure;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfFidelityRequest(
        @JsonProperty("layout")
        PdfEditableStructure layout,
        @JsonProperty("screenshot")
        String screenshot
) {
}
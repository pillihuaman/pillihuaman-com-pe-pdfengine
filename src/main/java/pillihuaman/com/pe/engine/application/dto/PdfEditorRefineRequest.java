package pillihuaman.com.pe.engine.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;

import java.util.List;

/**
 * Petición para refinar la fidelidad del documento con IA.
 *
 * @param document    documento editable actual
 * @param screenshots capturas (data URI base64) de cada página, para comparación visual
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfEditorRefineRequest(
        @JsonProperty("document") PdfEditorDocument document,
        @JsonProperty("screenshots") List<String> screenshots
) {
}

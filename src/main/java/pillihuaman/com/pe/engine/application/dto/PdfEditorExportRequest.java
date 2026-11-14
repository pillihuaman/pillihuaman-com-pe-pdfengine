package pillihuaman.com.pe.engine.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Petición para exportar el documento editado a PDF.
 *
 * @param fileName  nombre opcional del archivo resultante
 * @param pageHtmls HTML completo (ya editado) por página, en orden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfEditorExportRequest(
        @JsonProperty("fileName") String fileName,
        @JsonProperty("pages") List<String> pageHtmls
) {
}

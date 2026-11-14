package pillihuaman.com.pe.engine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * Una página editable del documento PDF.
 *
 * @param pageNumber número de página (1-based)
 * @param widthPt    ancho de la página en puntos (pt)
 * @param heightPt   alto de la página en puntos (pt)
 * @param html       HTML editable completo de la página (posicionamiento absoluto fiel)
 * @param elements   vista estructurada de los elementos (para edición semántica y para la IA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PdfEditorPage(
        int pageNumber,
        float widthPt,
        float heightPt,
        String html,
        List<PdfEditorElement> elements
) implements Serializable {
}

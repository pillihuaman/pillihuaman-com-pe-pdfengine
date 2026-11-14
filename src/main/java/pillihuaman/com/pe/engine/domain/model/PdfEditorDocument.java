package pillihuaman.com.pe.engine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * Documento PDF importado al editor: colección de páginas editables.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PdfEditorDocument(
        String documentId,
        List<PdfEditorPage> pages,
        PdfMetadata metadata
) implements Serializable {
}

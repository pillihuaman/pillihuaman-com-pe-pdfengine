package pillihuaman.com.pe.engine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Elemento editable dentro de una página. Todas las coordenadas y medidas están en puntos (pt).
 * La posición (x, y) es la esquina superior izquierda (origen CSS).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PdfEditorElement(
        String id,
        PdfEditorElementType type,
        float x,
        float y,
        float width,
        float height,
        /** Contenido de texto (para TEXT y TABLE_CELL). */
        String text,
        /** Propiedades CSS clave (fontFamily, fontSize, color, fontWeight, fontStyle, ...). */
        Map<String, String> style,
        /** data URI para IMAGE. */
        String src,
        /** Hijos: celdas de una TABLE, o elementos anidados. */
        List<PdfEditorElement> children,
        /** Tipo semántico asignado por la IA (ej. heading, amount, table-header). Opcional. */
        String semanticType
) implements Serializable {
}

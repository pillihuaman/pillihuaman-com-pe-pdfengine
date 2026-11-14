package pillihuaman.com.pe.engine.domain.model;

/**
 * Tipos de elemento del editor PDF.
 * Se usan como contrato estable entre la conversión determinística, el frontend y la IA.
 */
public enum PdfEditorElementType {
    /** Texto editable (palabra o línea). */
    TEXT,
    /** Imagen incrustada (data URI en {@code src}). */
    IMAGE,
    /** Rectángulo / línea vectorial. */
    RECT,
    /** Tabla reconstruida (hijos = celdas). */
    TABLE,
    /** Celda de una tabla. */
    TABLE_CELL
}

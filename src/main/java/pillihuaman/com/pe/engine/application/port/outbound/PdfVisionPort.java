package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Puerto de salida: mejora del documento editable mediante IA (tablas, orden de lectura,
 * reconstrucción de bloques semánticos y refinamiento de fidelidad).
 *
 * <p>Implementaciones: OpenAI gpt-4o, NeuroIA (servicio interno) o NoOp.
 * La selección se realiza tras un único bean {@code @Primary} configurado por
 * {@code application.pdf.editor.ai-provider}.</p>
 */
public interface PdfVisionPort {

    /**
     * Enriquece el documento determinístico con estructura semántica obtenida por IA.
     * Debe ser tolerante a fallos: si la IA no está disponible, retorna el documento sin cambios.
     */
    Mono<PdfEditorDocument> enhance(PdfEditorDocument document, String bearerToken);

    /**
     * Refina la fidelidad del documento editable comparando cada página con su captura
     * (screenshot). Debe ser tolerante a fallos: si no hay capturas o la IA falla,
     * retorna el documento sin cambios.
     */
    Mono<PdfEditorDocument> refine(PdfEditorDocument document, List<String> screenshots, String bearerToken);
}

package pillihuaman.com.pe.engine.application.port.inbound;

import pillihuaman.com.pe.engine.application.dto.PdfEditorExportRequest;
import pillihuaman.com.pe.engine.application.dto.PdfEditorRefineRequest;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import reactor.core.publisher.Mono;

/**
 * Puerto de entrada del editor PDF: importar (PDF→HTML editable), exportar (HTML→PDF)
 * y refinar fidelidad con IA.
 */
public interface PdfEditorUseCase {

    /**
     * Convierte un PDF en un documento editable (HTML + estructura), enriqueciéndolo con IA.
     */
    Mono<PdfEditorDocument> importPdf(byte[] pdfBytes, String bearerToken);

    /**
     * Regenera el PDF a partir del HTML editado por página.
     */
    Mono<byte[]> exportPdf(PdfEditorExportRequest request);

    /**
     * Refina la fidelidad del documento editable usando IA.
     */
    Mono<PdfEditorDocument> refine(PdfEditorRefineRequest request, String bearerToken);
}

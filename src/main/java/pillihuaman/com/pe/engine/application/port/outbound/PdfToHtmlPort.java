package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida: conversión PDF → HTML editable (determinística, vía PDFBox).
 */
public interface PdfToHtmlPort {

    Mono<PdfEditorDocument> toHtml(byte[] pdfBytes);
}

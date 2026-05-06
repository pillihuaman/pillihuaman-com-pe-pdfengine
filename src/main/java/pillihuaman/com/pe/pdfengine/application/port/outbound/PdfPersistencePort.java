package pillihuaman.com.pe.pdfengine.application.port.outbound;

import pillihuaman.com.pe.pdfengine.domain.model.PdfDocument;
import reactor.core.publisher.Mono;

/**
 * Outbound Port for PDF metadata persistence.
 */
public interface PdfPersistencePort {
    Mono<PdfDocument> saveMetadata(PdfDocument document);
}

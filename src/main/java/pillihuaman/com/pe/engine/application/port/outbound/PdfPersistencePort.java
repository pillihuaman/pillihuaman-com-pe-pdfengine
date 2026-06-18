package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import reactor.core.publisher.Mono;

/**
 * Outbound Port for PDF metadata persistence.
 */
public interface PdfPersistencePort {
    Mono<PdfDocument> saveMetadata(PdfDocument document);
}

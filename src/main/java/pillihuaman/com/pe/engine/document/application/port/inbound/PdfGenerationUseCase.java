package pillihuaman.com.pe.engine.document.application.port.inbound;

import pillihuaman.com.pe.engine.document.domain.model.PdfGenerationRequest;
import reactor.core.publisher.Mono;

/**
 * Puerto de entrada: caso de uso de generación de documentos PDF.
 */
public interface PdfGenerationUseCase {

    Mono<byte[]> generatePdf(
            PdfGenerationRequest request,
            String bearerToken,
            String tenantId);
}

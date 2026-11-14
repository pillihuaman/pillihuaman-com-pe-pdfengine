package pillihuaman.com.pe.engine.document.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.document.application.port.inbound.PdfGenerationUseCase;
import pillihuaman.com.pe.engine.document.application.port.outbound.PdfRendererPort;
import pillihuaman.com.pe.engine.document.domain.model.PdfGenerationRequest;
import reactor.core.publisher.Mono;

/**
 * Orquestador trivial: recibe un HTML completo y lo delega al renderer.
 * Sin fetch de plantillas, sin Thymeleaf, sin conocimiento de tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerationService implements PdfGenerationUseCase {

    private final PdfRendererPort pdfRendererPort;

    @Override
    public Mono<byte[]> generatePdf(
            final PdfGenerationRequest request,
            final String bearerToken,
            final String tenantId) {

        return Mono.justOrEmpty(request)
                .switchIfEmpty(Mono.error(
                        new BusinessLogicException("PdfGenerationRequest es requerido")))
                .filter(req -> req.html() != null && !req.html().isBlank())
                .switchIfEmpty(Mono.error(
                        new BusinessLogicException("El campo 'html' es obligatorio y no puede estar vacío")))
                .flatMap(req -> pdfRendererPort.render(req.html()))
                .doOnSuccess(bytes -> log.info(
                        "PDF generado correctamente: {} bytes (tenant={})",
                        bytes.length, tenantId))
                .doOnError(err -> log.error(
                        "Error generando PDF (tenant={}): {}",
                        tenantId, err.getMessage()));
    }
}

package pillihuaman.com.pe.engine.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.engine.application.dto.PdfEditorExportRequest;
import pillihuaman.com.pe.engine.application.dto.PdfEditorRefineRequest;
import pillihuaman.com.pe.engine.application.port.inbound.PdfEditorUseCase;
import pillihuaman.com.pe.engine.application.port.outbound.PdfProcessingPort;
import pillihuaman.com.pe.engine.application.port.outbound.PdfToHtmlPort;
import pillihuaman.com.pe.engine.application.port.outbound.PdfVisionPort;
import pillihuaman.com.pe.engine.document.application.port.outbound.PdfRendererPort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Orquestador del editor PDF.
 *
 * <ul>
 *   <li><b>importar</b>: PDF → HTML editable (determinístico) → enriquecimiento con IA (tolerante a fallos).</li>
 *   <li><b>exportar</b>: HTML por página → PDF por página (openhtmltopdf) → merge (PDFBox).</li>
 *   <li><b>refinar</b>: mejora de fidelidad del documento editable vía IA.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfEditorService implements PdfEditorUseCase {

    private final PdfToHtmlPort pdfToHtmlPort;
    private final PdfVisionPort pdfVisionPort;
    private final PdfRendererPort pdfRendererPort;
    private final PdfProcessingPort pdfProcessingPort;

    @Override
    public Mono<PdfEditorDocument> importPdf(final byte[] pdfBytes, final String bearerToken) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return Mono.error(new BusinessLogicException("El PDF proporcionado está vacío."));
        }

        return pdfToHtmlPort.toHtml(pdfBytes)
                .flatMap(doc -> pdfVisionPort.enhance(doc, bearerToken)
                        .onErrorResume(e -> {
                            log.warn("IA no disponible; se devuelve el documento determinístico: {}",
                                    e.getMessage());
                            return Mono.just(doc);
                        }))
                .doOnSuccess(doc -> log.info("PDF importado al editor: {} páginas (documentId={})",
                        doc.pages() == null ? 0 : doc.pages().size(), doc.documentId()));
    }

    @Override
    public Mono<byte[]> exportPdf(final PdfEditorExportRequest request) {
        if (request == null || request.pageHtmls() == null || request.pageHtmls().isEmpty()) {
            return Mono.error(new BusinessLogicException("No se proporcionaron páginas para exportar."));
        }

        return Flux.fromIterable(request.pageHtmls())
                .flatMapSequential(html -> pdfRendererPort.render(html)
                        .map(bytes -> new PdfDocument(
                                "page.pdf", bytes, bytes.length, null, Map.of(), List.of())))
                .collectList()
                .flatMap(pdfProcessingPort::merge)
                .map(PdfDocument::content)
                .doOnSuccess(bytes -> log.info("PDF exportado desde el editor: {} bytes", bytes.length))
                .doOnError(e -> log.error("Error exportando PDF desde el editor: {}", e.getMessage(), e));
    }

    @Override
    public Mono<PdfEditorDocument> refine(final PdfEditorRefineRequest request, final String bearerToken) {
        if (request == null || request.document() == null) {
            return Mono.error(new BusinessLogicException("Se requiere el documento para refinar."));
        }
        return pdfVisionPort.refine(request.document(), request.screenshots(), bearerToken);
    }
}

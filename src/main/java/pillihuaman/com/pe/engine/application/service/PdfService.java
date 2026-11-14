package pillihuaman.com.pe.engine.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.engine.application.dto.PdfEditableRequest;
import pillihuaman.com.pe.engine.application.port.inbound.PdfUseCase;
import pillihuaman.com.pe.engine.application.port.outbound.PdfAnalyzerPort;
import pillihuaman.com.pe.engine.application.port.outbound.PdfProcessingPort;
import pillihuaman.com.pe.engine.application.port.outbound.PromptExternalPort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.engine.domain.model.PdfPageContent;
import pillihuaman.com.pe.engine.domain.model.TranslatedDocument;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external.AiLayoutClientAdapter;
import pillihuaman.com.pe.engine.infrastructure.security.TenantWebFilter;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService implements PdfUseCase {

    private final PdfProcessingPort pdfProcessingPort;

    private final PdfAnalyzerPort pdfAnalyzerPort;
    private final PromptExternalPort promptExternalPort;
    private final AiLayoutClientAdapter aiLayoutClientAdapter;

    @Value("${application.pdf.ai-prompt-code}")
    private String aiPromptCode;

    @Value("${application.pdf.merge.max-files:100}")
    private int maxMergeFiles;

    @Value("${application.pdf.merge.max-file-bytes:52428800}")
    private long maxMergeFileBytes;

    @Value("${application.pdf.merge.max-total-bytes:262144000}")
    private long maxMergeTotalBytes;

    @Override
    public Mono<PdfDocument> mergeDocuments(List<PdfDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Mono.error(new BusinessLogicException("No se proporcionaron archivos para combinar."));
        }

        if (documents.size() > maxMergeFiles) {
            return Mono.error(new BusinessLogicException(
                    "Se excedió el número máximo de archivos permitidos (" + maxMergeFiles + ")."));
        }

        long totalBytes = documents.stream()
                .map(PdfDocument::content)
                .mapToLong(content -> content == null ? 0L : content.length)
                .sum();

        for (PdfDocument document : documents) {
            byte[] content = document.content();
            if (content == null || content.length == 0) {
                return Mono.error(new BusinessLogicException(
                        "El archivo '" + document.fileName() + "' está vacío."));
            }
            if (content.length > maxMergeFileBytes) {
                return Mono.error(new BusinessLogicException(
                        "El archivo '" + document.fileName() + "' excede el tamaño máximo permitido."));
            }
        }

        if (totalBytes > maxMergeTotalBytes) {
            return Mono.error(new BusinessLogicException(
                    "El tamaño total de los archivos excede el límite permitido."));
        }

        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "UNKNOWN");
            log.info("Starting Merge Operation for Tenant: {} | Total Files: {} | Total Bytes: {}",
                    tenantId, documents.size(), totalBytes);

            return pdfProcessingPort.merge(documents)
                    .map(merged -> new PdfDocument(
                            merged.fileName(),
                            merged.content(),
                            merged.size(),
                            tenantId,
                            Map.of(
                                    "processedAt", String.valueOf(System.currentTimeMillis()),
                                    "sourceFiles", String.valueOf(documents.size()),
                                    "totalBytes", String.valueOf(totalBytes)
                            ),
                            List.of()))
                    .doOnSuccess(doc -> log.info("PDF merge completed: {} ({} bytes)",
                            doc.fileName(), doc.size()))
                    .doOnError(e -> log.error("Critical error during PDF merge: {}", e.getMessage(), e));
        });
    }

    @Override
    public Mono<byte[]> updateDocumentContent(PdfEditableRequest request) {
        return Mono.fromCallable(
                        () -> {
                            String base64 = request.base64OriginalPdf();
                            if (base64 != null && base64.contains(",")) {
                                base64 = base64.split(",")[1];
                            }
                            return (base64 == null || base64.isEmpty()) ? new byte[0] : Base64.getDecoder().decode(base64);
                        })
                .flatMap(bytes -> {
                    // null para documentId porque ya es stateless
                    PdfEditableStructure structure = new PdfEditableStructure(null, request.pages(), null);
                    return pdfProcessingPort.redrawWithTemplate(structure, bytes);
                })
                .doOnSuccess(r -> log.info("PDF stateless overlay successful"))
                .doOnError(e -> log.error("Overlay error: {}", e.getMessage()));
    }

    @Override
    public Mono<PdfEditableStructure> refineFidelity(PdfEditableStructure currentStructure, String base64Image, String bearerToken) {
        log.info("Starting Extreme Mathematical Alignment pass (Logical Snapping)...");

        return aiLayoutClientAdapter.callNeuroIaToRefineVisuals(
                currentStructure,
                null, // Screenshot es null para alineación lógica matemática
                "PDF_VIS_AL_FIDELITY_V1",
                bearerToken
        ).map(refinedStructure -> {
            List<PdfPageContent> mergedPages = new ArrayList<>();
            for (PdfPageContent refinedPage : refinedStructure.pages()) {
                PdfPageContent originalPage = currentStructure.pages().stream()
                        .filter(p -> p.pageNumber() == refinedPage.pageNumber())
                        .findFirst()
                        .orElse(null);

                if (originalPage != null) {
                    mergedPages.add(new PdfPageContent(
                            refinedPage.pageNumber(),
                            refinedPage.width(),
                            refinedPage.height(),
                            refinedPage.units(),
                            refinedPage.rotation(),
                            refinedPage.elements(),
                            originalPage.graphics(), // Safely Preserve Native Graphic Elements
                            originalPage.rawText(),  // Preserve RawText
                            originalPage.hasImages(),
                            originalPage.fontCount()
                    ));
                } else {
                    mergedPages.add(refinedPage);
                }
            }
            return new PdfEditableStructure(refinedStructure.documentId(), mergedPages, refinedStructure.info());
        }).doOnSuccess(res -> log.info("Logical Snapping completed successfully"));
    }

    @Override
    public Mono<PdfEditableStructure> analyzeAndOptimize(byte[] pdfBytes, String bearerToken) {
        // ETAPA 1: extracción geométrica pura, sin llamadas a la IA.
        return pdfAnalyzerPort.analyze(pdfBytes);
    }

    @Override
    public Mono<TranslatedDocument> translatePdf(byte[] pdfBytes, String bearerToken) {
        return pdfAnalyzerPort.analyzeTranslated(pdfBytes);
    }


}

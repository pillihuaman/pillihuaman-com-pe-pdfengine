package pillihuaman.com.pe.pdfengine.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.pdfengine.application.dto.PdfEditableRequest;
import pillihuaman.com.pe.pdfengine.application.port.inbound.PdfUseCase;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PdfAnalyzerPort;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PdfProcessingPort;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PdfStoragePort;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PromptExternalPort;
import pillihuaman.com.pe.pdfengine.domain.model.PdfDocument;
import pillihuaman.com.pe.pdfengine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.pdfengine.domain.model.PdfPageContent;
import pillihuaman.com.pe.pdfengine.domain.model.TextElement;
import pillihuaman.com.pe.pdfengine.domain.model.external.AiClassificationResponse;
import pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.external.AiLayoutClientAdapter;
import pillihuaman.com.pe.pdfengine.infrastructure.security.TenantWebFilter;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService implements PdfUseCase {

    private final PdfProcessingPort pdfProcessingPort;
    private final PdfStoragePort pdfStoragePort;

    private final PdfAnalyzerPort pdfAnalyzerPort;
    private final PromptExternalPort promptExternalPort;
    private final AiLayoutClientAdapter aiLayoutClientAdapter;
    @Value("${application.pdf.ai-prompt-code}")
    private String aiPromptCode;

    @Override
    public Mono<String> mergeDocuments(List<PdfDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Mono.error(new IllegalArgumentException("No documents provided for merging"));
        }

        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "UNKNOWN");
            log.info("Starting Merge Operation for Tenant: {} | Total Docs: {}", tenantId, documents.size());

            return pdfProcessingPort.merge(documents).flatMap(mergedDoc -> {

                PdfDocument docWithTenant = new PdfDocument("merged_" + System.currentTimeMillis() + ".pdf", mergedDoc.content(), mergedDoc.size(), tenantId, Map.of("processedAt", String.valueOf(System.currentTimeMillis())), Collections.emptyList());

                return pdfStoragePort.upload(docWithTenant);
            }).doOnSuccess(fileId -> log.info("Successfully merged and stored PDF: {}", fileId)).doOnError(e -> log.error("Critical error during PDF merge: {}", e.getMessage()));
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
    public Mono<PdfEditableStructure> analyzeAndOptimize(byte[] pdfBytes, String bearerToken) {
        log.info("Starting structural analysis for provided PDF bytes");
        return pdfAnalyzerPort.analyze(pdfBytes)
                .doOnSuccess(structure -> log.info("PDF Analysis completed: {} pages", structure.pages().size()));
    }

    private PdfEditableStructure performSemanticJoin(PdfEditableStructure geometry, List<AiClassificationResponse.ClassificationItem> classifications) {

        // Mapa ID -> TYPE
        Map<String, String> typeMap = classifications.stream().collect(Collectors.toMap(AiClassificationResponse.ClassificationItem::id, AiClassificationResponse.ClassificationItem::type, (existing, replacement) -> existing));

        List<PdfPageContent> enrichedPages = geometry.pages().stream().map(page -> {
            // >>> CHANGE
            List<TextElement> enrichedElements = page.elements().stream().map(e -> new TextElement(
                    e.id(), e.text(), e.left(), e.top(), e.width(), e.height(),
                    e.fontSize(), e.fontName(), e.fontWeight(), e.isItalic(),
                    typeMap.getOrDefault(e.id(), "text"), e.resourceReference()
            )).collect(Collectors.toList());
            // <<< CHANGE

            return new PdfPageContent(page.pageNumber(), page.width(), page.height(), page.units(), page.rotation(), enrichedElements, page.graphics(), page.rawText(), page.hasImages(), page.fontCount());
        }).collect(Collectors.toList());

        return new PdfEditableStructure(geometry.documentId(), enrichedPages, geometry.info());
    }

}

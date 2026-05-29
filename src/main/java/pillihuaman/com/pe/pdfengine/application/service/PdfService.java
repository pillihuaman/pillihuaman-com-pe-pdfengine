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

import java.util.*;
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
        return pdfAnalyzerPort.analyze(pdfBytes)
                .flatMap(rawStructure -> {
                    // Extraer data mínima (ID y Texto) para la IA
                    List<Map<String, String>> minimalData = rawStructure.pages().stream()
                            .flatMap(p -> p.elements().stream())
                            .map(e -> Map.of("id", e.id(), "text", e.text()))
                            .collect(Collectors.toList());

                    // Llamamos a la IA y unimos los resultados
                    return aiLayoutClientAdapter.callNeuroIaToClassify(minimalData, "PDF_LAYOUT_AI_GEOMETRY_COMPILER_V2", bearerToken)
                            .map(classifications -> performSemanticJoin(rawStructure, classifications))
                            .onErrorReturn(rawStructure); // Fallback seguro
                });
    }

    private PdfEditableStructure performSemanticJoin(PdfEditableStructure geometry, List<AiClassificationResponse.ClassificationItem> classifications) {
        Map<String, String> typeMap = classifications.stream()
                .collect(Collectors.toMap(AiClassificationResponse.ClassificationItem::id, AiClassificationResponse.ClassificationItem::type, (a, b) -> a));

        List<PdfPageContent> enrichedPages = geometry.pages().stream().map(page -> {
            List<TextElement> enrichedElements = page.elements().stream().map(e -> new TextElement(
                    e.id(), e.text(), e.left(), e.top(), e.width(), e.height(),
                    e.fontSize(), e.fontName(), e.fontWeight(), e.isItalic(),
                    typeMap.getOrDefault(e.id(), "text"), // <--- AQUÍ SE ASIGNA EL TIPO DE LA IA
                    e.resourceReference(), null, // letterSpacing
                    null, // lineHeight
                    null, // textAlign
                    null
            )).collect(Collectors.toList());

            return new PdfPageContent(page.pageNumber(), page.width(), page.height(), page.units(), page.rotation(), enrichedElements, page.graphics(), page.rawText(), page.hasImages(), page.fontCount());
        }).collect(Collectors.toList());

        return new PdfEditableStructure(geometry.documentId(), enrichedPages, geometry.info());
    }


}

package pillihuaman.com.pe.engine.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.engine.application.port.outbound.PdfAnalyzerPort;
import pillihuaman.com.pe.engine.domain.model.TranslatedDocument;
import pillihuaman.com.pe.engine.domain.model.TranslatedPage;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external.AiLayoutClientAdapter;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfTranslationOrchestrator {

    private static final String PROMPT_DELTA_CORRECTOR = "PDF_CSS_DELTA_CORRECTOR_V1";
    private static final String PDFJS_TEXTLAYER = "PDFJS_TEXTLAYER";

    private final PdfAnalyzerPort pdfAnalyzerPort;
    private final AiLayoutClientAdapter aiLayoutClientAdapter;
    private final TranslationConfidenceEvaluator confidenceEvaluator;

    public Mono<TranslatedDocument> translate(byte[] pdfBytes, String screenshotBase64, boolean skipAI, String bearerToken) {
        return pdfAnalyzerPort.analyzeTranslated(pdfBytes)
                .flatMap(n1Doc -> {
                    if (skipAI || screenshotBase64 == null || screenshotBase64.isBlank()) {
                        return Mono.just(n1Doc);
                    }
                    return aiLayoutClientAdapter.correctVisualDeviations(
                                    n1Doc, screenshotBase64, PROMPT_DELTA_CORRECTOR, bearerToken)
                            .onErrorResume(e -> {
                                log.warn("Fallo N2, usando N1: {}", e.getMessage());
                                return Mono.just(n1Doc);
                            });
                })
                .flatMap(n2Doc -> {
                    String strategy = confidenceEvaluator.decide(n2Doc, pdfBytes);
                    if (PDFJS_TEXTLAYER.equals(strategy)) {
                        return Mono.just(toPdfJsFallback(n2Doc));
                    }
                    return Mono.just(n2Doc);
                });
    }

    private TranslatedDocument toPdfJsFallback(TranslatedDocument doc) {
        List<TranslatedPage> pages = new ArrayList<>();
        for (TranslatedPage page : doc.pages()) {
            pages.add(new TranslatedPage(page.pageNumber(), page.widthPx(), page.heightPx(),
                    page.backgroundImageBase64(), List.of()));
        }
        return new TranslatedDocument(doc.documentId(), PDFJS_TEXTLAYER, pages);
    }
}

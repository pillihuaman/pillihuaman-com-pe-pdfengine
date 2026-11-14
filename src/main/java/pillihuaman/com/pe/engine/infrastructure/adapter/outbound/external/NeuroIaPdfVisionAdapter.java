package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.PdfVisionPort;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditorElement;
import pillihuaman.com.pe.engine.domain.model.PdfEditorElementType;
import pillihuaman.com.pe.engine.domain.model.PdfEditorPage;
import pillihuaman.com.pe.engine.domain.model.external.AiClassificationResponse;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.PdfEditorHtmlBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proveedor de IA basado en NeuroIA (servicio interno).
 *
 * <p>{@code enhance} clasifica semánticamente cada elemento de texto (tipo: heading, amount,
 * table-header, ...) usando {@link AiLayoutClientAdapter#callNeuroIaToClassify} y anota el resultado
 * en {@code PdfEditorElement.semanticType}, regenerando el HTML con {@code data-semantic}.</p>
 *
 * <p>{@code refine} (comparación visual) requiere un puente hacia {@code PdfEditableStructure},
 * pendiente de implementar.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NeuroIaPdfVisionAdapter implements PdfVisionPort {

    private final AiLayoutClientAdapter aiLayoutClientAdapter;

    @Value("${application.pdf.editor.neuroia.prompt-code:PDF_LAYOUT_AI_GEOMETRY_COMPILER_V2}")
    private String promptCode;

    @Override
    public Mono<PdfEditorDocument> enhance(final PdfEditorDocument document, final String bearerToken) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) {
            return Mono.justOrEmpty(document);
        }

        return Flux.fromIterable(document.pages())
                .flatMapSequential(page -> enhancePage(page, bearerToken))
                .collectList()
                .map(pages -> new PdfEditorDocument(document.documentId(), pages, document.metadata()))
                .onErrorReturn(document);
    }

    @Override
    public Mono<PdfEditorDocument> refine(
            final PdfEditorDocument document, final List<String> screenshots, final String bearerToken) {
        // El refinamiento visual de NeuroIA trabaja sobre PdfEditableStructure (bridge pendiente).
        return Mono.justOrEmpty(document);
    }

    private Mono<PdfEditorPage> enhancePage(final PdfEditorPage page, final String bearerToken) {
        final List<PdfEditorElement> words = page.elements() == null
                ? List.of()
                : page.elements().stream()
                        .filter(e -> e.type() == PdfEditorElementType.TEXT)
                        .toList();

        if (words.isEmpty()) {
            return Mono.just(page);
        }

        final List<Map<String, String>> minimalData = words.stream()
                .map(w -> Map.of("id", w.id(), "text", w.text() == null ? "" : w.text()))
                .toList();

        return aiLayoutClientAdapter.callNeuroIaToClassify(minimalData, promptCode, bearerToken)
                .map(classifications -> applySemantic(page, classifications))
                .onErrorReturn(page);
    }

    private PdfEditorPage applySemantic(
            final PdfEditorPage page,
            final List<AiClassificationResponse.ClassificationItem> classifications) {
        if (classifications == null || classifications.isEmpty()) {
            return page;
        }

        final Map<String, String> typeById = new HashMap<>();
        for (AiClassificationResponse.ClassificationItem item : classifications) {
            typeById.put(item.id(), item.type());
        }

        final List<PdfEditorElement> elements = new ArrayList<>();
        for (PdfEditorElement e : page.elements()) {
            final String type = typeById.get(e.id());
            if (type != null && e.type() == PdfEditorElementType.TEXT) {
                elements.add(new PdfEditorElement(
                        e.id(), e.type(), e.x(), e.y(), e.width(), e.height(),
                        e.text(), e.style(), e.src(), e.children(), type));
            } else {
                elements.add(e);
            }
        }

        final String html = PdfEditorHtmlBuilder.buildPageHtml(
                page.pageNumber(), page.widthPt(), page.heightPt(), elements);
        return new PdfEditorPage(page.pageNumber(), page.widthPt(), page.heightPt(), html, elements);
    }
}

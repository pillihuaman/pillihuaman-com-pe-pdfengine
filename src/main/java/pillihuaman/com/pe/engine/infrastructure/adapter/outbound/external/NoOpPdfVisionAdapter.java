package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.PdfVisionPort;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Implementación sin IA: devuelve el documento determinístico sin cambios.
 * Usada cuando {@code application.pdf.editor.ai-provider=none}.
 */
@Component
public class NoOpPdfVisionAdapter implements PdfVisionPort {

    @Override
    public Mono<PdfEditorDocument> enhance(final PdfEditorDocument document, final String bearerToken) {
        return Mono.justOrEmpty(document);
    }

    @Override
    public Mono<PdfEditorDocument> refine(
            final PdfEditorDocument document, final List<String> screenshots, final String bearerToken) {
        return Mono.justOrEmpty(document);
    }
}

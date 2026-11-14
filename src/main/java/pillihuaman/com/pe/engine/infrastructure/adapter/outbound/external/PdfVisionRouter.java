package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.PdfVisionPort;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Selecciona el proveedor de IA del editor en tiempo de arranque según
 * {@code application.pdf.editor.ai-provider} (valores: {@code openai}, {@code neuroia}, {@code none}).
 *
 * <p>Es el único bean {@code @Primary} de {@link PdfVisionPort}, por lo que {@link pillihuaman.com.pe.engine.application.service.PdfEditorService}
 * inyecta siempre este router sin ambigüedad.</p>
 */
@Primary
@Component
public class PdfVisionRouter implements PdfVisionPort {

    private final PdfVisionPort delegate;

    public PdfVisionRouter(
            @Qualifier("openAiPdfVisionAdapter") final PdfVisionPort openAi,
            @Qualifier("neuroIaPdfVisionAdapter") final PdfVisionPort neuroIa,
            @Qualifier("noOpPdfVisionAdapter") final PdfVisionPort noOp,
            @Value("${application.pdf.editor.ai-provider:openai}") final String provider) {

        this.delegate = switch (provider.toLowerCase()) {
            case "neuroia" -> neuroIa;
            case "none", "off", "noop" -> noOp;
            default -> openAi;
        };
    }

    @Override
    public Mono<PdfEditorDocument> enhance(final PdfEditorDocument document, final String bearerToken) {
        return delegate.enhance(document, bearerToken);
    }

    @Override
    public Mono<PdfEditorDocument> refine(
            final PdfEditorDocument document, final List<String> screenshots, final String bearerToken) {
        return delegate.refine(document, screenshots, bearerToken);
    }
}

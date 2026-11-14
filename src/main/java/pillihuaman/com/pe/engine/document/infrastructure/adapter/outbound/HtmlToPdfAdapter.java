package pillihuaman.com.pe.engine.document.infrastructure.adapter.outbound;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.document.application.port.outbound.PdfRendererPort;
import pillihuaman.com.pe.engine.document.domain.exception.PdfGenerationException;
import pillihuaman.com.pe.engine.document.infrastructure.config.PdfRendererConfig;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adaptador de salida que convierte HTML a PDF con OpenHTMLtoPDF (PDFBox 3).
 * Operación síncrona aislada en boundedElastic para no bloquear el Event Loop.
 */
@Slf4j
@Component
public class HtmlToPdfAdapter implements PdfRendererPort {

    private final String baseUri;

    public HtmlToPdfAdapter(final PdfRendererConfig.PdfRendererSettings settings) {
        this.baseUri = settings.baseUri();
    }
    @Override
    public Mono<byte[]> render(final String processedHtml) {

        if (processedHtml == null || processedHtml.isBlank()) {
            return Mono.error(new PdfGenerationException("HTML content is null or empty"));
        }

        String safeHtml = sanitizeHtmlForXmlParser(processedHtml);

        return Mono.fromCallable(() -> {
                    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    try {
                        final PdfRendererBuilder builder = new PdfRendererBuilder();
                        builder.useFastMode();
                        builder.withHtmlContent(safeHtml, null);
                        builder.toStream(outputStream);
                        builder.run();
                        return outputStream.toByteArray();
                    } finally {
                        try {
                            outputStream.close();
                        } catch (Exception ignored) {
                        }
                    }
                })
                .onErrorMap(ex -> {
                    if (ex instanceof PdfGenerationException) {
                        return ex;
                    }
                    String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                    if (ex.getClass().getName().contains("XRRuntimeException") || errorMsg.contains("SAXParseException")) {
                        return new PdfGenerationException(
                                "Malformed XHTML. OpenHTMLtoPDF requires strict XML. Check for unclosed tags or unescaped '&'. Details: "
                                        + errorMsg,
                                ex);
                    }
                    return new PdfGenerationException("Error rendering HTML to PDF: " + errorMsg, ex);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String sanitizeHtmlForXmlParser(final String rawHtml) {
        String cleaned = rawHtml;

        // 1. Arreglar el error del payload: <style>{ -> <style>body {
        cleaned = cleaned.replaceAll("(?i)<style>\\s*\\{", "<style>body {");

        // 2. Fix invalid CSS in the client payload: @page ( ... ) -> @page { ... }
        cleaned = cleaned.replaceAll("@page\\s*\\(([^)]*)\\)", "@page {$1}");
        cleaned = cleaned.replaceAll("(?i)min-height\\s*:\\s*auto", "min-height: 100%");
        cleaned = cleaned.replaceAll("(?i)<br\\s*>", "<br/>");
        cleaned = cleaned.replaceAll("(?i)<hr\\s*>", "<hr/>");
        cleaned = cleaned.replaceAll("(?i)<img([^>]*?)(?<!/)>", "<img$1/>");

        // 5. Extraer todos los <style> del body
        StringBuilder styles = new StringBuilder();
        Pattern stylePattern = Pattern.compile("(?is)<style[^>]*>.*?</style>");
        Matcher matcher = stylePattern.matcher(cleaned);
        while (matcher.find()) {
            styles.append(matcher.group());
        }

        // Remover los <style> del body para que no se impriman como texto
        cleaned = matcher.replaceAll("");

        // 6. Empaquetar todo en un documento XHTML estricto (estilos en el head)
        String lower = cleaned.toLowerCase().trim();
        if (!lower.startsWith("<!doctype") && !lower.startsWith("<html")) {
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>"
                    + styles.toString()
                    + "</head><body>"
                    + cleaned
                    + "</body></html>";
        }

        // Si ya viene con <html>, inyectamos los estilos al inicio
        return cleaned.replaceFirst("(?i)<head>", "<head>" + styles.toString());
    }
}

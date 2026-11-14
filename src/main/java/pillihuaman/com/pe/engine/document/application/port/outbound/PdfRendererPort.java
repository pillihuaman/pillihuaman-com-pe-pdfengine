package pillihuaman.com.pe.engine.document.application.port.outbound;

import reactor.core.publisher.Mono;

/**
 * Puerto de salida: renderizado de HTML a PDF.
 */
public interface PdfRendererPort {

    Mono<byte[]> render(String processedHtml);
}

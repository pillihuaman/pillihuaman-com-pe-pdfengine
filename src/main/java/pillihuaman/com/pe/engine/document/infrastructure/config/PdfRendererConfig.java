package pillihuaman.com.pe.engine.document.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del renderizador HTML → PDF (OpenHTMLtoPDF).
 * Expone el baseUri usado para resolver recursos relativos (CSS/logo/imágenes).
 */
@Configuration
public class PdfRendererConfig {

    public static final String DEFAULT_BASE_URI = "https://alamodaperu.online/";
    @Bean
    public PdfRendererSettings pdfRendererSettings(
            @Value("${application.pdf-document.base-uri:https://alamodaperu.online/}") final String baseUri) {
        final String resolved = (baseUri == null || baseUri.isBlank()) ? DEFAULT_BASE_URI : baseUri;
        return new PdfRendererSettings(resolved);
    }

    public record PdfRendererSettings(String baseUri) {
    }
}

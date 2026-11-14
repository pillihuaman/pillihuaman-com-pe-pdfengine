package pillihuaman.com.pe.engine.document.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contrato de entrada para la generación de un PDF a partir de un HTML
 * completamente renderizado por el servicio consumidor (e.g. support-ms).
 *
 * El Engine es STATELESS: no consulta plantillas, no conoce tenants, no
 * interpreta el negocio. Solo transforma el HTML recibido en bytes PDF.
 *
 * @param html     HTML completo (DOCTYPE + head + style + body), ya
 *                 renderizado. Incluye el CSS embebido en {@code <style>}.
 * @param fileName Nombre opcional del archivo PDF. Si es null o blank,
 *                 el Controller genera {@code document_yyyyMMdd_HHmmss.pdf}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfGenerationRequest(
        String html,
        String fileName
) {
}

package pillihuaman.com.pe.engine.document.infrastructure.adapter.inbound.rest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pillihuaman.com.pe.engine.document.application.port.inbound.PdfGenerationUseCase;
import pillihuaman.com.pe.engine.document.domain.model.PdfGenerationRequest;
import pillihuaman.com.pe.engine.infrastructure.security.TenantWebFilter;
import reactor.core.publisher.Mono;

/**
 * Endpoint REST de generación de documentos PDF.
 * El cliente (support-ms) envía el HTML completo; el Engine solo lo renderiza.
 *
 * Cubierto por:
 *  - /private/v1/pdf/** (JWT) en SecurityConfig
 *  - TenantWebFilter (X-Tenant-ID obligatorio en /private/**)
 */
@Slf4j
@RestController
@RequestMapping("/private/v1/pdf/generate")
@RequiredArgsConstructor
public class PdfGenerationController {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String FALLBACK_FILE_NAME_PREFIX = "document_";

    private final PdfGenerationUseCase pdfGenerationUseCase;

    @PostMapping(value = "/document", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<byte[]>> generateDocument(
            @RequestBody final PdfGenerationRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) final String bearerToken) {

        return Mono.deferContextual(ctx -> {
            final String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");

            return pdfGenerationUseCase.generatePdf(request, bearerToken, tenantId)
                    .map(bytes -> {
                        final String fileName = resolveFileName(request);
                        log.info("Documento generado: {} ({} bytes, tenant={})",
                                fileName, bytes.length, tenantId);

                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_PDF)
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + fileName + "\"")
                                .body(bytes);
                    });
        });
    }

    private String resolveFileName(final PdfGenerationRequest request) {
        if (request != null && request.fileName() != null && !request.fileName().isBlank()) {
            return sanitizeFileName(request.fileName());
        }
        return FALLBACK_FILE_NAME_PREFIX
                + LocalDateTime.now().format(TIMESTAMP_FORMAT)
                + ".pdf";
    }

    private String sanitizeFileName(final String raw) {
        final String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").trim();
        return cleaned.toLowerCase().endsWith(".pdf") ? cleaned : cleaned + ".pdf";
    }
}

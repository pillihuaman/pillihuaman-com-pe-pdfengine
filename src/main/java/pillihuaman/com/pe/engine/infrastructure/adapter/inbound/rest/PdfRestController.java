package pillihuaman.com.pe.engine.infrastructure.adapter.inbound.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import pillihuaman.com.pe.engine.application.dto.PdfEditableRequest;
import pillihuaman.com.pe.engine.application.dto.PdfFidelityRequest;
import pillihuaman.com.pe.engine.application.port.inbound.PdfUseCase;
import pillihuaman.com.pe.engine.application.port.outbound.PdfAnalyzerPort;
import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.engine.domain.model.TranslatedDocument;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import pillihuaman.com.pe.engine.infrastructure.security.TenantWebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Slf4j
@RestController
@RequestMapping("/private/v1/pdf")
@RequiredArgsConstructor
public class PdfRestController {
    private final PdfUseCase pdfUseCase;
    private final PdfAnalyzerPort pdfAnalyzerPort;

    /**
     * Combina múltiples PDFs e imágenes y devuelve un único PDF descargable.
     */
    @PostMapping(value = "/merge",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<byte[]>> mergePdfs(@RequestPart("files") Flux<FilePart> files) {
        return files
                .flatMap(this::mapToFilePart)
                .collectList()
                .flatMap(pdfUseCase::mergeDocuments)
                .map(merged -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(merged.fileName()).build().toString())
                        .contentLength(merged.content().length)
                        .body(merged.content()));
    }

    /**
     * Analyzes PDF for structural editing and wraps in RespBase.
     */
    @PostMapping(value = "/analyze-for-edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<RespBase<PdfEditableStructure>> analyzeForEdit(
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return filePartMono
                .flatMap(this::mapToFileBytes)
                .flatMap(bytes -> pdfUseCase.analyzeAndOptimize(bytes, bearerToken))
                .map(result -> new RespBase<PdfEditableStructure>().ok(result));
    }

    @PostMapping(value = "/translate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<RespBase<TranslatedDocument>> translate(
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestPart(value = "screenshot", required = false) String screenshotBase64,
            @RequestParam(value = "skipAI", defaultValue = "false") boolean skipAI,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            log.info("PDF translate requested for tenant: {}", tenantId);
            return filePartMono
                    .flatMap(this::mapToFileBytes)
                    .flatMap(bytes -> pdfUseCase.translatePdf(bytes, screenshotBase64, skipAI, bearerToken))
                    .map(result -> new RespBase<TranslatedDocument>().ok(result));
        });
    }


    @PutMapping(value = "/update-content",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<byte[]>> updateContent(@RequestBody PdfEditableRequest request) {
        return pdfUseCase.updateDocumentContent(request)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                        .body(bytes));
    }

    private Mono<byte[]> mapToFileBytes(FilePart filePart) {
        // >>> CHANGE
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                });
        // <<< CHANGE
    }

    private Mono<PdfDocument> mapToFilePart(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new PdfDocument(
                            filePart.filename(),
                            bytes,
                            (long) bytes.length,
                            null,
                            Collections.emptyMap(),
                            Collections.emptyList()
                    );
                });
    }

    @PostMapping("/refine-fidelity")
    public Mono<RespBase<PdfEditableStructure>> refineFidelity(
            @RequestBody PdfFidelityRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {

        return pdfUseCase.refineFidelity(request.layout(), request.screenshot(), token)
                .map(result -> new RespBase<PdfEditableStructure>().ok(result));
    }
    public record RefineN2Request(TranslatedDocument base, String screenshot) {}

    @PostMapping(value = "/refine-n2", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RespBase<TranslatedDocument>> refineN2(
            @RequestBody RefineN2Request request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {

        return pdfUseCase.refineTranslatedDocument(request.base(), request.screenshot(), bearerToken)
                .map(result -> new RespBase<TranslatedDocument>().ok(result));
    }
}
package pillihuaman.com.pe.engine.infrastructure.adapter.inbound.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import pillihuaman.com.pe.engine.application.dto.PdfEditorExportRequest;
import pillihuaman.com.pe.engine.application.dto.PdfEditorRefineRequest;
import pillihuaman.com.pe.engine.application.port.inbound.PdfEditorUseCase;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import reactor.core.publisher.Mono;

/**
 * Endpoints del editor PDF (pipeline PDF → HTML editable → PDF).
 */
@RestController
@RequestMapping("/private/v1/pdf/editor")
@RequiredArgsConstructor
public class PdfEditorController {

    private static final String DEFAULT_EXPORT_NAME = "edited.pdf";

    private final PdfEditorUseCase pdfEditorUseCase;

    /**
     * Importa un PDF y devuelve su representación editable (HTML + estructura), enriquecida con IA.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<RespBase<PdfEditorDocument>> importPdf(
            @RequestPart("file") Mono<FilePart> filePart,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return filePart
                .flatMap(this::readBytes)
                .flatMap(bytes -> pdfEditorUseCase.importPdf(bytes, bearerToken))
                .map(result -> new RespBase<PdfEditorDocument>().ok(result));
    }

    /**
     * Exporta el HTML editado a un único PDF descargable.
     */
    @PostMapping(value = "/export", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<byte[]>> exportPdf(@RequestBody PdfEditorExportRequest request) {
        return pdfEditorUseCase.exportPdf(request)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(resolveFileName(request)).build().toString())
                        .contentLength(bytes.length)
                        .body(bytes));
    }

    /**
     * Refina la fidelidad del documento editable mediante IA.
     */
    @PostMapping("/refine")
    public Mono<RespBase<PdfEditorDocument>> refine(
            @RequestBody PdfEditorRefineRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return pdfEditorUseCase.refine(request, bearerToken)
                .map(result -> new RespBase<PdfEditorDocument>().ok(result));
    }

    private Mono<byte[]> readBytes(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                });
    }

    private String resolveFileName(PdfEditorExportRequest request) {
        if (request != null && request.fileName() != null && !request.fileName().isBlank()) {
            String name = request.fileName().trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
            return name.toLowerCase().endsWith(".pdf") ? name : name + ".pdf";
        }
        return DEFAULT_EXPORT_NAME;
    }
}

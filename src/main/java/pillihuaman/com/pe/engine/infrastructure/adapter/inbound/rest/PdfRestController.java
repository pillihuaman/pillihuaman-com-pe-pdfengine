package pillihuaman.com.pe.engine.infrastructure.adapter.inbound.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;

@RestController
@RequestMapping("/private/v1/pdf")
@RequiredArgsConstructor
public class PdfRestController {
    private final PdfUseCase pdfUseCase;
    private final PdfAnalyzerPort pdfAnalyzerPort;

    /**
     * Merges multiple PDFs and wraps result in RespBase.
     */
    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<RespBase<String>> mergePdfs(@RequestPart("files") Flux<FilePart> files) {
        return files
                .flatMap(this::mapToFilePart)
                .collectList()
                .flatMap(pdfUseCase::mergeDocuments)
                .map(result -> new RespBase<String>().ok(result));
    }

    /**
     * Analyzes PDF for structural editing and wraps in RespBase.
     */
    @PostMapping(value = "/analyze-for-edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<RespBase<PdfEditableStructure>> analyzeForEdit(
            @RequestPart("file") Mono<FilePart> filePartMono,
            // >>> CHANGE
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        // <<< CHANGE

        // >>> CHANGE
        return filePartMono
                .flatMap(this::mapToFileBytes)
                .flatMap(bytes -> pdfUseCase.analyzeAndOptimize(bytes, bearerToken))
                .map(result -> new RespBase<PdfEditableStructure>().ok(result));
        // <<< CHANGE
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
}
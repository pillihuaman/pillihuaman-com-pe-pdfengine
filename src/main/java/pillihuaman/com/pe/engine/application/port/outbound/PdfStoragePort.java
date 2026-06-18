package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import reactor.core.publisher.Mono;

public interface PdfStoragePort {
    Mono<String> upload(PdfDocument document);

    Mono<PdfDocument> download(String fileId);
}

package pillihuaman.com.pe.pdfengine.application.port.outbound;

import pillihuaman.com.pe.pdfengine.domain.model.PdfDocument;
import reactor.core.publisher.Mono;

public interface PdfStoragePort {
  Mono<String> upload(PdfDocument document);

  Mono<PdfDocument> download(String fileId);
}

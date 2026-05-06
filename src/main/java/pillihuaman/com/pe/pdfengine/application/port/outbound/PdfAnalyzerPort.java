package pillihuaman.com.pe.pdfengine.application.port.outbound;

import pillihuaman.com.pe.pdfengine.domain.model.PdfEditableStructure;
import reactor.core.publisher.Mono;

public interface PdfAnalyzerPort {
  Mono<PdfEditableStructure> analyze(byte[] pdfBytes);
}

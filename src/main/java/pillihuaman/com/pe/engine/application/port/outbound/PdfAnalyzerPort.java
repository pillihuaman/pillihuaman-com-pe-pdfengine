package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import reactor.core.publisher.Mono;

public interface PdfAnalyzerPort {
    Mono<PdfEditableStructure> analyze(byte[] pdfBytes);
}

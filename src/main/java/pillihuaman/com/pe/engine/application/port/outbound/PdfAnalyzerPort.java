package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.engine.domain.model.TranslatedDocument;
import reactor.core.publisher.Mono;

public interface PdfAnalyzerPort {
    Mono<PdfEditableStructure> analyze(byte[] pdfBytes);

    Mono<TranslatedDocument> analyzeTranslated(byte[] pdfBytes);
}

package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PdfProcessingPort {
    Mono<PdfDocument> merge(List<PdfDocument> documents);

    Mono<PdfDocument> compress(PdfDocument document);

    Mono<byte[]> redraw(PdfEditableStructure structure);

    Mono<byte[]> redrawWithTemplate(PdfEditableStructure structure, byte[] templateBytes);
}

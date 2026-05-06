package pillihuaman.com.pe.pdfengine.application.port.inbound;

import pillihuaman.com.pe.pdfengine.application.dto.PdfEditableRequest;
import pillihuaman.com.pe.pdfengine.domain.model.PdfDocument;
import pillihuaman.com.pe.pdfengine.domain.model.PdfEditableStructure;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PdfUseCase {

    Mono<String> mergeDocuments(List<PdfDocument> documents);


    Mono<PdfEditableStructure> analyzeAndOptimize(byte[] pdfBytes, String bearerToken);

    Mono<byte[]> updateDocumentContent(PdfEditableRequest request);
}

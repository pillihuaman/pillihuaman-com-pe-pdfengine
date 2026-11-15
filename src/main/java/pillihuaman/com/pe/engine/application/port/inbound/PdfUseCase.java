package pillihuaman.com.pe.engine.application.port.inbound;

import pillihuaman.com.pe.engine.application.dto.PdfEditableRequest;
import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.engine.domain.model.TranslatedDocument;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PdfUseCase {

    Mono<PdfDocument> mergeDocuments(List<PdfDocument> documents);


    Mono<PdfEditableStructure> analyzeAndOptimize(byte[] pdfBytes, String bearerToken);

    Mono<TranslatedDocument> translatePdf(byte[] pdfBytes, String screenshotBase64, boolean skipAI, String bearerToken);

    Mono<TranslatedDocument> refineTranslatedDocument(TranslatedDocument base, String screenshotBase64, String bearerToken);

    Mono<byte[]> updateDocumentContent(PdfEditableRequest request);


    Mono<PdfEditableStructure> refineFidelity(PdfEditableStructure currentStructure, String base64Image, String bearerToken);

}

package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.external;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PdfStoragePort;
import pillihuaman.com.pe.pdfengine.domain.model.PdfDocument;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PdfNeuroiaStorageAdapter implements PdfStoragePort {
    private final Map<String, PdfDocument> storage = new ConcurrentHashMap<>();


    @Override
    public Mono<String> upload(PdfDocument document) {
        return Mono.fromCallable(() -> {
            String id = "ID-" + System.currentTimeMillis();
            storage.put(id, document);
            log.info("Stored document {} with id {}", document.fileName(), id);
            return id;
        });
    }

    @Override
    public Mono<PdfDocument> download(String fileId) {
        return Mono.fromCallable(() -> {
            PdfDocument doc = storage.get(fileId);

            if (doc == null) {
                throw new IllegalStateException("Document not found for id: " + fileId);
            }

            return doc;
        });
    }
}
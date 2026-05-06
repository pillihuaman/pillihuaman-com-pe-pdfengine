package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PdfPersistencePort;
import pillihuaman.com.pe.pdfengine.domain.model.PdfDocument;
import reactor.core.publisher.Mono;

/** MongoDB Adapter implementation. */
@Component
@RequiredArgsConstructor
public class PdfMongoAdapter implements PdfPersistencePort {
  private final PdfMetadataRepository repository;

  @Override
  public Mono<PdfDocument> saveMetadata(PdfDocument document) {
    PdfMetadataEntity entity =
        PdfMetadataEntity.builder()
            .fileName(document.fileName())
            .size(document.size())
            .tenantId(document.tenantId())
            .metadata(document.metadata())
            .build();
    return repository.save(entity).thenReturn(document);
  }
}

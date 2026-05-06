package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PdfMetadataRepository extends ReactiveMongoRepository<PdfMetadataEntity, String> {}

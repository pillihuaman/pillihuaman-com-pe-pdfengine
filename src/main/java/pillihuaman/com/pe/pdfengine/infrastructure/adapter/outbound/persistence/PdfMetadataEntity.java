package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.persistence;

import java.util.Map;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB Entity for PDF Metadata. */
@Builder
@Document(collection = "pdf_metadata")
public record PdfMetadataEntity(
    @Id String id,
    String fileName,
    long size,
    String tenantId,
    Map<String, String> metadata,
    long processedA) {}

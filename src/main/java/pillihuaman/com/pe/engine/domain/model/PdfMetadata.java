package pillihuaman.com.pe.engine.domain.model;

import java.time.Instant;

public record PdfMetadata(
        String title,
        String author,
        String creator,
        Instant creationDate,
        String producer,
        int pageCount) {
}

package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;
import java.util.List;

public record TranslatedDocument(
        String documentId,
        String renderStrategy,
        List<TranslatedPage> pages
) implements Serializable {
}

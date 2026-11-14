package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;
import java.util.List;

public record TranslatedPage(
        int pageNumber,
        float widthPx,
        float heightPx,
        String backgroundImageBase64,
        List<TranslatedElement> elements
) implements Serializable {
}

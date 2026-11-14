package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;

public record TranslatedElement(
        String id,
        String text,
        String type,
        float x,
        float y,
        float width,
        float height,
        float fontSize,
        float lineHeight,
        String fontFamily,
        String fontWeight,
        boolean isItalic,
        String resourceReference
) implements Serializable {
}

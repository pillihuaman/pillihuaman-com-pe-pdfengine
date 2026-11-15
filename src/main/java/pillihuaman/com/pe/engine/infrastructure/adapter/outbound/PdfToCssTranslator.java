package pillihuaman.com.pe.engine.infrastructure.adapter.outbound;

import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.domain.model.TextElement;
import pillihuaman.com.pe.engine.domain.model.TranslatedElement;

@Component
public class PdfToCssTranslator {

    private static final float PT_TO_PX = 1.333333f;
    private static final float ASCENT_FACTOR = 0.75f;
    private static final float BOX_HEIGHT_RATIO = 1.3f;
    private static final float LINE_HEIGHT_RATIO = 1.15f;

    public TranslatedElement translate(TextElement raw, float pageHeightPt) {
        float cssFontSize = raw.fontSize() * PT_TO_PX;

        // CORRECCIÓN: raw.top() ya es Top-Down (0 = borde superior).
        // Solo lo pasamos a píxeles y le restamos el ajuste de la fuente.
        float cssTop = (raw.top() * PT_TO_PX) - (cssFontSize * ASCENT_FACTOR);

        float cssLeft = raw.left() * PT_TO_PX;
        float cssWidth = raw.width() * PT_TO_PX;
        float cssHeight = cssFontSize * BOX_HEIGHT_RATIO;
        float cssLineHeight = cssFontSize * LINE_HEIGHT_RATIO;

        return new TranslatedElement(
                raw.id(),
                raw.text(),
                "text",
                cssLeft,
                cssTop,
                cssWidth,
                cssHeight,
                cssFontSize,
                cssLineHeight,
                raw.fontName(),
                raw.fontWeight(),
                raw.isItalic(),
                raw.resourceReference()
        );
    }
}
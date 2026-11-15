package pillihuaman.com.pe.engine.application.service;

import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.domain.model.TranslatedDocument;
import pillihuaman.com.pe.engine.domain.model.TranslatedElement;
import pillihuaman.com.pe.engine.domain.model.TranslatedPage;

@Component
public class TranslationConfidenceEvaluator {

    private static final String HTML_CSS = "HTML_CSS";
    private static final String PDFJS_TEXTLAYER = "PDFJS_TEXTLAYER";

    public String decide(TranslatedDocument doc, byte[] originalPdfBytes) {
        if (doc == null || doc.pages() == null || doc.pages().isEmpty()) {
            return PDFJS_TEXTLAYER;
        }

        boolean hasAnyElement = false;
        for (TranslatedPage page : doc.pages()) {
            for (TranslatedElement element : page.elements()) {
                hasAnyElement = true;
                if (element.x() < 0 || element.y() < 0
                        || element.x() > page.widthPx() || element.y() > page.heightPx()) {
                    return PDFJS_TEXTLAYER;
                }
            }
        }

        return hasAnyElement ? HTML_CSS : PDFJS_TEXTLAYER;
    }
}

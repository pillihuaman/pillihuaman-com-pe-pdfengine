package pillihuaman.com.pe.engine.infrastructure.adapter.outbound;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.PdfAnalyzerPort;
import pillihuaman.com.pe.engine.domain.model.GraphicElement;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.engine.domain.model.PdfPageContent;
import pillihuaman.com.pe.engine.domain.model.TextElement;
import pillihuaman.com.pe.engine.domain.model.TranslatedDocument;
import pillihuaman.com.pe.engine.domain.model.TranslatedElement;
import pillihuaman.com.pe.engine.domain.model.TranslatedPage;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.List;

/**
 * Senior AI Geometry Compiler & Layout Restorer.
 * Especializado en reconstrucción de layouts perfectos para edición web desde PDFBox 3.0.3.
 */
@Component
@RequiredArgsConstructor
public class PdfBoxAnalyzerAdapter implements PdfAnalyzerPort {

    private final PdfToCssTranslator pdfToCssTranslator;
    private final PdfPageRenderer pdfPageRenderer;

    // Constantes Determinísticas de Compilación
    private static final float FACTOR_ESCALA = 1.333333f; // 72 DPI -> 96 DPI (CSS Pixels)
    private static final float WORD_GAP_THRESHOLD = 2.0f; // Separación mínima entre palabras (pt)

    @Override
    public Mono<PdfEditableStructure> analyze(final byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
                final List<PdfPageContent> pages = new ArrayList<>();

                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    final PDPage page = document.getPage(i);
                    final PDRectangle mediaBox = page.getMediaBox();
                    final float pageHeight = mediaBox.getHeight();

                    // Fase 1: Extracción Cruda de Elementos (pt) y escalado a px.
                    List<TextElement> rawElements = scaleToPx(extractRawElements(document, i + 1));

                    // Fase 2: Extracción de Gráficos Vectoriales e Imágenes
                    List<GraphicElement> graphics = extractGraphics(page, pageHeight);

                    pages.add(new PdfPageContent(i + 1, mediaBox.getWidth() * FACTOR_ESCALA, mediaBox.getHeight() * FACTOR_ESCALA, "px", page.getRotation(), rawElements, graphics, "", !graphics.isEmpty(), rawElements.size()));
                }
                return new PdfEditableStructure("doc_" + System.currentTimeMillis(), pages, null);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<TranslatedDocument> analyzeTranslated(final byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
                final List<TranslatedPage> pages = new ArrayList<>();

                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    final PDPage page = document.getPage(i);
                    final PDRectangle mediaBox = page.getMediaBox();
                    if (mediaBox == null) {
                        continue;
                    }
                    final float pageHeightPt = mediaBox.getHeight();
                    final float widthPx = mediaBox.getWidth() * FACTOR_ESCALA;
                    final float heightPx = pageHeightPt * FACTOR_ESCALA;

                    final List<TranslatedElement> translatedElements = new ArrayList<>();
                    for (TextElement raw : extractRawElements(document, i + 1)) {
                        translatedElements.add(pdfToCssTranslator.translate(raw, pageHeightPt));
                    }

                    final String backgroundImage = pdfPageRenderer.renderPageToBase64Png(document, i);

                    pages.add(new TranslatedPage(i + 1, widthPx, heightPx, backgroundImage, translatedElements));
                }
                return new TranslatedDocument("doc_" + System.currentTimeMillis(), "HTML_CSS", pages);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<TextElement> extractRawElements(PDDocument doc, int pageNum) throws IOException {
        final List<TextElement> elements = new ArrayList<>();

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String string, List<TextPosition> textPositions) {
                if (textPositions == null || textPositions.isEmpty()) return;
                List<TextPosition> sortedPositions = new ArrayList<>(textPositions);
                // Ordenamos estrictamente por coordenada X para asegurar lectura visual correcta
                sortedPositions.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));

                List<TextPosition> word = new ArrayList<>();
                TextPosition previous = null;

                for (TextPosition tp : sortedPositions) {
                    if (previous != null) {
                        float gap = tp.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());

                        // Separar si el gap es mayor al umbral O si hay un salto negativo (texto retrocede)
                        if (gap > WORD_GAP_THRESHOLD || gap < -WORD_GAP_THRESHOLD) {
                            addWordElement(elements, word);
                            word = new ArrayList<>();
                        }
                    }
                    word.add(tp);
                    previous = tp;
                }
                addWordElement(elements, word);
            }
        };
        stripper.setStartPage(pageNum);
        stripper.setEndPage(pageNum);
       stripper.setSortByPosition(true);
        stripper.writeText(doc, new Writer() {
            @Override public void write(char[] cbuf, int off, int len) {}
            @Override public void flush() {}
            @Override public void close() {}
        });

        return elements;
    }
    private void addWordElement(List<TextElement> elements, List<TextPosition> word) {
        if (word == null || word.isEmpty()) return;
        TextPosition first = word.get(0);

        // Métricas nativas en puntos (pt). La conversión pt -> px la hace PdfToCssTranslator.
        StringBuilder text = new StringBuilder();
        float widthPt = 0f;
        for (TextPosition tp : word) {
            if (tp.getUnicode() != null) text.append(tp.getUnicode());
            widthPt += tp.getWidthDirAdj();
        }
        String fontName = (first.getFont() != null && first.getFont().getName() != null)
                ? first.getFont().getName() : "Helvetica";

        elements.add(new TextElement(
                UUID.randomUUID().toString(),
                text.toString().trim(),
                first.getXDirAdj(),        // left (pt)
                first.getYDirAdj(),        // top = baseline (pt)
                widthPt,                   // width (pt)
                first.getHeightDir(),      // height (pt)
                first.getFontSizeInPt(),   // fontSize (pt)
                fontName,
                detectWeight(first),
                isItalic(first),
                "text",
                null,
                null, null, null, null
        ));
    }

    private List<TextElement> scaleToPx(List<TextElement> rawElements) {
        List<TextElement> px = new ArrayList<>(rawElements.size());
        for (TextElement e : rawElements) {
            px.add(new TextElement(
                    e.id(), e.text(),
                    e.left() * FACTOR_ESCALA,
                    (e.top() - e.height()) * FACTOR_ESCALA,
                    e.width() * FACTOR_ESCALA,
                    e.height() * FACTOR_ESCALA,
                    e.fontSize() * FACTOR_ESCALA,
                    e.fontName(), e.fontWeight(), e.isItalic(),
                    e.type(), e.resourceReference(), e.letterSpacing(), e.lineHeight(), e.textAlign(), e.cssTransform()));
        }
        return px;
    }


    private List<GraphicElement> extractGraphics(final PDPage page, final float pageHeight) throws IOException {
        final List<GraphicElement> graphics = new ArrayList<>();
        final PDFGraphicsStreamEngine engine = new PDFGraphicsStreamEngine(page) {
            private final Point2D lastPoint = new Point2D.Float(0, 0);


            @Override
            public void drawImage(final PDImage pdImage) throws IOException {
                processImageResource(pdImage, getGraphicsState().getCurrentTransformationMatrix());
            }

            // >>> CHANGE: Helper para procesar imágenes tanto normales como dentro de Forms
            private void processImageResource(PDImage pdImage, Matrix ctm) throws IOException {
                String base64Image = "";
                try {
                    BufferedImage bi = pdImage.getImage();
                    if (bi != null) {
                        BufferedImage standardized = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2d = standardized.createGraphics();
                        g2d.drawImage(bi, 0, 0, null);
                        g2d.dispose();

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(standardized, "png", baos);
                        base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
                    }
                } catch (Exception e) {
                    base64Image = "IMG_ERR";
                }

                if (!base64Image.isEmpty() && !base64Image.equals("IMG_ERR")) {
                    float x = ctm.getTranslateX() * FACTOR_ESCALA;
                    float width = ctm.getScalingFactorX() * FACTOR_ESCALA;
                    float height = ctm.getScalingFactorY() * FACTOR_ESCALA;
                    float y = (pageHeight - (ctm.getTranslateY() + ctm.getScalingFactorY())) * FACTOR_ESCALA;
                    graphics.add(new GraphicElement("IMAGE", x, y, width, height, "", base64Image));
                }
            }


            @Override
            public void showForm(PDFormXObject form) throws IOException {
                // El motor entra en el sub-stream del objeto para buscar imágenes dentro
                processChildStream(form);
            }

            private void processChildStream(PDFormXObject form) throws IOException {
                // Recursividad controlada para extraer recursos de FormXObjects
                Iterable<COSName> xObjects = form.getResources().getXObjectNames();
                for (COSName name : xObjects) {
                    if (form.getResources().isImageXObject(name)) {
                        PDImage img = (PDImage) form.getResources().getXObject(name);
                        processImageResource(img, getGraphicsState().getCurrentTransformationMatrix());
                    }
                }
            }


            @Override
            public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
                float x = (float) Math.min(p0.getX(), p2.getX()) * FACTOR_ESCALA;
                float pdfY = (float) Math.max(p0.getY(), p2.getY());
                float width = (float) Math.abs(p0.getX() - p2.getX()) * FACTOR_ESCALA;
                float height = (float) Math.abs(p0.getY() - p2.getY()) * FACTOR_ESCALA;
                graphics.add(new GraphicElement("RECT", x, (pageHeight - pdfY) * FACTOR_ESCALA, width, height, "#000000", null));
            }

            @Override
            public void moveTo(float x, float y) {
                lastPoint.setLocation(x, y);
            }

            @Override
            public void lineTo(float x, float y) {
                float x1 = (float) lastPoint.getX() * FACTOR_ESCALA;
                float y1 = (pageHeight - (float) lastPoint.getY()) * FACTOR_ESCALA;
                float x2 = x * FACTOR_ESCALA;
                float y2 = (pageHeight - y) * FACTOR_ESCALA;
                graphics.add(new GraphicElement("RECT", Math.min(x1, x2), Math.min(y1, y2), Math.max(1, Math.abs(x2 - x1)), Math.max(1, Math.abs(y2 - y1)), "#000000", null));
                lastPoint.setLocation(x, y);
            }

            @Override
            public void clip(int i) {
            }

            @Override
            public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
                lastPoint.setLocation(x3, y3);
            }

            @Override
            public Point2D getCurrentPoint() {
                return lastPoint;
            }

            @Override
            public void closePath() {
            }

            @Override
            public void endPath() {
            }

            @Override
            public void strokePath() {
            }

            @Override
            public void fillPath(int i) {
            }

            @Override
            public void fillAndStrokePath(int i) {
            }

            @Override
            public void shadingFill(COSName shadingName) {
            }
        };
        engine.processPage(page);
        return graphics;
    }

    private boolean isItalic(TextPosition tp) {
        if (tp.getFont() == null) return false;
        boolean descriptorItalic = tp.getFont().getFontDescriptor() != null && tp.getFont().getFontDescriptor().isItalic();
        String name = tp.getFont().getName();
        return descriptorItalic || (name != null && (name.toLowerCase().contains("italic") || name.toLowerCase().contains("oblique")));
    }

    private String detectWeight(TextPosition tp) {
        String name = (tp.getFont() != null && tp.getFont().getName() != null) ? tp.getFont().getName().toLowerCase() : "";
        return (name.contains("bold") || name.contains("black") || name.contains("w7")) ? "bold" : "normal";
    }

}
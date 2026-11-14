package pillihuaman.com.pe.engine.infrastructure.adapter.outbound;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.PdfToHtmlPort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditorElement;
import pillihuaman.com.pe.engine.domain.model.PdfEditorElementType;
import pillihuaman.com.pe.engine.domain.model.PdfEditorPage;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversor determinístico PDF → HTML editable usando Apache PDFBox 3.
 *
 * <p>Genera, por cada página, un HTML con posicionamiento absoluto (fiel al layout original),
 * preservando texto (palabra a palabra con fuente, tamaño, color y estilo) e imágenes.
 * Todas las unidades se expresan en puntos (pt), coincidiendo con el espacio de PDFBox y de
 * openhtmltopdf, lo que evita conversiones y pérdida de precisión.</p>
 */
@Slf4j
@Component
public class PdfBoxToHtmlAdapter implements PdfToHtmlPort {

    private static final float ASCENT_RATIO = 0.8f;   // aproximación de la altura de la ascendente
    private static final float LINE_HEIGHT_RATIO = 1.2f; // altura de línea aproximada

    @Override
    public Mono<PdfEditorDocument> toHtml(final byte[] pdfBytes) {
        return Mono.fromCallable(() -> convert(pdfBytes))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private PdfEditorDocument convert(final byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BusinessLogicException("El PDF proporcionado está vacío.");
        }

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
            final List<PdfEditorPage> pages = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                pages.add(convertPage(document, i));
            }
            return new PdfEditorDocument("doc_" + System.currentTimeMillis(), pages, null);
        }
    }

    private PdfEditorPage convertPage(final PDDocument document, final int pageIndex) throws IOException {
        final PDPage page = document.getPage(pageIndex);
        final PDRectangle box = page.getMediaBox();
        final float width = box.getWidth();
        final float height = box.getHeight();

        final List<PdfEditorElement> elements = new ArrayList<>();
        elements.addAll(extractTextElements(document, pageIndex, box));
        elements.addAll(extractImageElements(document, pageIndex, height));

        final String html = PdfEditorHtmlBuilder.buildPageHtml(pageIndex + 1, width, height, elements);
        return new PdfEditorPage(pageIndex + 1, width, height, html, elements);
    }

    // =====================================================================================
    // Extracción de texto (palabra a palabra, con fuente/tamaño/color)
    // =====================================================================================

    private List<PdfEditorElement> extractTextElements(
            final PDDocument document, final int pageIndex, final PDRectangle box) throws IOException {

        final float originX = box.getLowerLeftX();
        final float originY = box.getLowerLeftY();
        final float pageHeight = box.getHeight();

        final List<Word> words = new ArrayList<>();
        final IdentityHashMap<TextPosition, Integer> colorByPosition = new IdentityHashMap<>();

        final PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(final TextPosition text) {
                super.processTextPosition(text);
                try {
                    final PDColor color = getGraphicsState().getNonStrokingColor();
                    colorByPosition.put(text, color == null ? null : color.toRGB());
                } catch (Exception e) {
                    colorByPosition.put(text, null);
                }
            }

            @Override
            protected void writeString(final String string, final List<TextPosition> textPositions) {
                if (string == null || string.trim().isEmpty()
                        || textPositions == null || textPositions.isEmpty()) {
                    return;
                }
                final TextPosition first = textPositions.get(0);
                final float x = first.getXDirAdj() - originX;
                final float baselineY = first.getYDirAdj() - originY;
                final float fontSize = first.getFontSizeInPt();

                float width = 0f;
                for (TextPosition tp : textPositions) {
                    width += tp.getWidthDirAdj();
                }

                final Integer rgb = colorByPosition.get(textPositions.get(textPositions.size() - 1));
                words.add(new Word(string.trim(), x, baselineY, width, fontSize,
                        first.getFont().getName(), rgb));
            }
        };

        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.writeText(document, Writer.nullWriter());

        // Orden de lectura: de arriba hacia abajo y, dentro de la misma línea, de izquierda a derecha.
        words.sort(Comparator.comparingDouble(Word::baselineY).reversed()
                .thenComparingDouble(Word::x));

        final List<PdfEditorElement> elements = new ArrayList<>(words.size());
        for (Word w : words) {
            final float top = pageHeight - w.baselineY() - (w.fontSize() * ASCENT_RATIO);
            final float height = w.fontSize() * LINE_HEIGHT_RATIO;
            elements.add(new PdfEditorElement(
                    UUID.randomUUID().toString(),
                    PdfEditorElementType.TEXT,
                    w.x(), top, w.width(), height,
                    w.text(), buildTextStyle(w), null, null, null));
        }
        return elements;
    }

    private Map<String, String> buildTextStyle(final Word word) {
        final Map<String, String> style = new LinkedHashMap<>();
        style.put("fontFamily", toCssFont(word.fontName()));
        style.put("fontSize", PdfEditorHtmlBuilder.formatPt(word.fontSize()));
        style.put("color", toCssColor(word.color()));

        final String name = word.fontName() == null ? "" : word.fontName().toLowerCase();
        final boolean bold = name.contains("bold") || name.contains("black") || name.contains("heavy");
        final boolean italic = name.contains("italic") || name.contains("oblique");
        style.put("fontWeight", bold ? "bold" : "normal");
        style.put("fontStyle", italic ? "italic" : "normal");
        return style;
    }

    private String toCssFont(final String fontName) {
        if (fontName == null) {
            return "Helvetica";
        }
        final String name = fontName.toLowerCase();
        if (name.contains("courier") || name.contains("mono")) {
            return "Courier";
        }
        if (name.contains("times") || name.contains("serif")) {
            return "Times New Roman";
        }
        return "Helvetica";
    }

    private String toCssColor(final Integer rgb) {
        if (rgb == null) {
            return "#000000";
        }
        // rgb es el valor RGB empaquetado de PDColor.toRGB(); enmascaramos a 24 bits (RRGGBB).
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    // =====================================================================================
    // Extracción de imágenes (posiciones + data URI PNG)
    // =====================================================================================

    private List<PdfEditorElement> extractImageElements(
            final PDDocument document, final int pageIndex, final float pageHeight) throws IOException {

        final PDPage page = document.getPage(pageIndex);
        final List<PdfEditorElement> images = new ArrayList<>();

        final PDFGraphicsStreamEngine engine = new PDFGraphicsStreamEngine(page) {
            @Override
            public void drawImage(final PDImage pdImage) throws IOException {
                processImage(pdImage, getGraphicsState().getCurrentTransformationMatrix());
            }

            private void processImage(final PDImage pdImage, final Matrix ctm) {
                try {
                    final BufferedImage source = pdImage.getImage();
                    if (source == null) {
                        return;
                    }
                    // Normalizar a ARGB para asegurar que ImageIO pueda codificar a PNG.
                    final BufferedImage argb = new BufferedImage(
                            source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    final Graphics2D g = argb.createGraphics();
                    g.drawImage(source, 0, 0, null);
                    g.dispose();

                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    if (!ImageIO.write(argb, "png", out)) {
                        return;
                    }
                    final String dataUri = "data:image/png;base64,"
                            + Base64.getEncoder().encodeToString(out.toByteArray());

                    final float width = ctm.getScalingFactorX();
                    final float height = ctm.getScalingFactorY();
                    final float x = ctm.getTranslateX();
                    final float yTop = pageHeight - (ctm.getTranslateY() + height);

                    images.add(new PdfEditorElement(
                            UUID.randomUUID().toString(),
                            PdfEditorElementType.IMAGE,
                            x, yTop, width, height,
                            null, Map.of(), dataUri, null, null));
                } catch (Exception e) {
                    log.warn("No se pudo extraer una imagen de la página {}: {}",
                            pageIndex + 1, e.getMessage());
                }
            }

            @Override
            public void showForm(final PDFormXObject form) throws IOException {
                final Iterable<COSName> xObjects = form.getResources().getXObjectNames();
                for (COSName name : xObjects) {
                    if (form.getResources().isImageXObject(name)) {
                        processImage((PDImage) form.getResources().getXObject(name),
                                getGraphicsState().getCurrentTransformationMatrix());
                    }
                }
            }

            // Operadores de gráficos no necesarios en esta fase (solo texto e imágenes).
            @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) { }
            @Override public void moveTo(float x, float y) { }
            @Override public void lineTo(float x, float y) { }
            @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) { }
            @Override public Point2D getCurrentPoint() { return new Point2D.Float(0, 0); }
            @Override public void closePath() { }
            @Override public void endPath() { }
            @Override public void strokePath() { }
            @Override public void fillPath(int windingRule) { }
            @Override public void fillAndStrokePath(int windingRule) { }
            @Override public void shadingFill(COSName shadingName) { }
            @Override public void clip(int windingRule) { }
        };

        engine.processPage(page);
        return images;
    }

    /** Palabra extraída con su geometría y estilo. */
    private record Word(
            String text,
            float x,
            float baselineY,
            float width,
            float fontSize,
            String fontName,
            Integer color
    ) {
    }
}

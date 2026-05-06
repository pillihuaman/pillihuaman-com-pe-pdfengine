package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound;

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
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PdfAnalyzerPort;
import pillihuaman.com.pe.pdfengine.domain.model.GraphicElement;
import pillihuaman.com.pe.pdfengine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.pdfengine.domain.model.PdfPageContent;
import pillihuaman.com.pe.pdfengine.domain.model.TextElement;
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
import java.util.stream.Collectors;

/**
 * Senior AI Geometry Compiler & Layout Restorer.
 * Especializado en reconstrucción de layouts perfectos para edición web desde PDFBox 3.0.3.
 */
@Component
public class PdfBoxAnalyzerAdapter implements PdfAnalyzerPort {

    // Constantes Determinísticas de Compilación
    private static final float FACTOR_ESCALA = 1.333333f; // 72 DPI -> 96 DPI (CSS Pixels)
    private static final float WIDTH_RATIO = 0.55f;       // Factor de corrección de ancho de fuente
    private static final float HEIGHT_RATIO = 1.2f;       // Line-height para editabilidad
    private static final float Y_THRESHOLD = 3.0f;        // Tolerancia de alineación vertical (puntos)

    @Override
    public Mono<PdfEditableStructure> analyze(final byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
                final List<PdfPageContent> pages = new ArrayList<>();

                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    final PDPage page = document.getPage(i);
                    final PDRectangle mediaBox = page.getMediaBox();
                    final float pageHeight = mediaBox.getHeight();

                    // Fase 1: Extracción Cruda de Elementos con Atributos
                    List<TextElement> rawElements = extractRawElements(document, i + 1);

                    // Fase 2: Compilación Geométrica Heurística (Agrupación y Normalización)
                    List<TextElement> compiledElements = compileGeometry(rawElements);

                    // Fase 3: Extracción de Gráficos Vectoriales e Imágenes
                    List<GraphicElement> graphics = extractGraphics(page, pageHeight);

                    pages.add(new PdfPageContent(i + 1, mediaBox.getWidth() * FACTOR_ESCALA, mediaBox.getHeight() * FACTOR_ESCALA, "px", page.getRotation(), compiledElements, graphics, "", !graphics.isEmpty(), compiledElements.size()));
                }
                return new PdfEditableStructure("doc_" + System.currentTimeMillis(), pages, null);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<TextElement> compileGeometry(List<TextElement> rawElements) {
        if (rawElements.isEmpty()) return rawElements;

        // Agrupación por proximidad en el eje Y (Líneas)
        Map<Integer, List<TextElement>> lineGroups = rawElements.stream().collect(Collectors.groupingBy(e -> Math.round(e.top() / Y_THRESHOLD)));

        List<TextElement> compiledList = new ArrayList<>();

        lineGroups.keySet().stream().sorted().forEach(yKey -> {
            List<TextElement> line = lineGroups.get(yKey);
            line.sort(Comparator.comparingDouble(TextElement::left));

            TextElement current = null;

            for (TextElement next : line) {
                if (current == null) {
                    current = next;
                    continue;
                }

                // Heurística de Fusión: Si la distancia X es menor que el tamaño de fuente, unir.
                float distanceX = next.left() - (current.left() + current.width());
                if (distanceX < current.fontSize() * 0.8f) {
                    current = mergeElements(current, next);
                } else {
                    compiledList.add(applyGeometricRepair(current));
                    current = next;
                }
            }
            if (current != null) {
                compiledList.add(applyGeometricRepair(current));
            }
        });

        return compiledList;
    }

    private TextElement mergeElements(TextElement a, TextElement b) {
        // >>> CHANGE
        return new TextElement(UUID.randomUUID().toString(), a.text() + " " + b.text(), a.left(), a.top(), (b.left() + b.width()) - a.left(), Math.max(a.height(), b.height()), a.fontSize(), a.fontName(), a.fontWeight(), a.isItalic(), "text", null);
        // <<< CHANGE
    }


    private TextElement applyGeometricRepair(TextElement e) {
        float scaledLeft = e.left() * FACTOR_ESCALA;
        float scaledTop = (e.top() * FACTOR_ESCALA) - (e.fontSize() * 0.1f);
        float repairedWidth = (e.text().length() * e.fontSize() * WIDTH_RATIO) * FACTOR_ESCALA;
        float repairedHeight = (e.fontSize() * HEIGHT_RATIO) * FACTOR_ESCALA;

        // Ajuste: Redondear las dimensiones para evitar deformaciones
        repairedWidth = Math.round(repairedWidth);
        repairedHeight = Math.round(repairedHeight);

        // >>> CHANGE
        return new TextElement(e.id(), e.text(), scaledLeft, scaledTop, repairedWidth, repairedHeight, e.fontSize() * FACTOR_ESCALA, normalizeFontName(e.fontName()), e.fontWeight(), e.isItalic(), "text", e.resourceReference());
        // <<< CHANGE
    }

    private List<TextElement> extractRawElements(PDDocument doc, int pageNum) throws IOException {
        final List<TextElement> elements = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String string, List<TextPosition> textPositions) {
                if (textPositions == null || textPositions.isEmpty()) return;
                TextPosition first = textPositions.get(0);
                float webTopY = first.getYDirAdj() - first.getHeightDir();
                // >>> CHANGE
                elements.add(new TextElement(UUID.randomUUID().toString(), string.trim(), first.getXDirAdj(), webTopY, calculateTotalWidth(textPositions), first.getHeightDir(), first.getFontSizeInPt(), first.getFont().getName(), detectWeight(first), isItalic(first), "text", null));
                // <<< CHANGE
            }
        };
        stripper.setStartPage(pageNum);
        stripper.setEndPage(pageNum);
        stripper.writeText(doc, new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) {
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return elements;
    }

    private String normalizeFontName(String font) {
        if (font == null) return "Arial";
        String f = font.toLowerCase();
        if (f.contains("arial")) return "Arial";
        if (f.contains("helvetica")) return "Helvetica";
        if (f.contains("times")) return "Times New Roman";
        return "Arial";
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
        boolean descriptorItalic = tp.getFont().getFontDescriptor() != null && tp.getFont().getFontDescriptor().isItalic();
        return descriptorItalic || tp.getFont().getName().toLowerCase().contains("italic") || tp.getFont().getName().toLowerCase().contains("oblique");
    }

    private String detectWeight(TextPosition tp) {
        String name = tp.getFont().getName().toLowerCase();
        return (name.contains("bold") || name.contains("black") || name.contains("w7")) ? "bold" : "normal";
    }

    private float calculateTotalWidth(List<TextPosition> positions) {
        float width = 0;
        for (TextPosition tp : positions) width += tp.getWidthDirAdj();
        return width;
    }


}
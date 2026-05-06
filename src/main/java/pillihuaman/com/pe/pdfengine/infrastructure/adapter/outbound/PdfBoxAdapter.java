package pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.pdfengine.application.port.outbound.PdfProcessingPort;
import pillihuaman.com.pe.pdfengine.domain.model.GraphicElement;
import pillihuaman.com.pe.pdfengine.domain.model.PdfDocument;
import pillihuaman.com.pe.pdfengine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.pdfengine.domain.model.TextElement;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Enterprise PDF Processing Adapter using Apache PDFBox 3.0.3.
 * Optimized for High-Fidelity coordinate mapping and cross-platform font rendering.
 */
@Slf4j
@Component
public class PdfBoxAdapter implements PdfProcessingPort {

    private static final float PX_TO_PT = 0.75f; // 72 DPI (PDF) / 96 DPI (Web)
    private static final Map<String, Standard14Fonts.FontName> FONT_MAP =
            Map.of(
                    "Arial", Standard14Fonts.FontName.HELVETICA,
                    "Helvetica", Standard14Fonts.FontName.HELVETICA,
                    "Times New Roman", Standard14Fonts.FontName.TIMES_ROMAN,
                    "Courier New", Standard14Fonts.FontName.COURIER);


    private void renderPrecisionImageFromGraphic(
            PDDocument doc, PDPageContentStream stream, GraphicElement graphic, float pageHeight) {
        String ref = graphic.resourceReference();
        if (ref == null || ref.isEmpty() || "IMG_DATA_STUB".equals(ref) || !ref.contains(",")) return;

        try {
            String base64Content = ref.substring(ref.indexOf(",") + 1).trim();
            byte[] imageBytes = Base64.getMimeDecoder().decode(base64Content);

            String uniqueImgName = "img_" + java.util.UUID.randomUUID().toString();
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imageBytes, uniqueImgName);

            float w = graphic.width() * PX_TO_PT;
            float h = graphic.height() * PX_TO_PT;
            if (w <= 0) w = 100 * PX_TO_PT;
            if (h <= 0) h = 100 * PX_TO_PT;

            float x = graphic.x() * PX_TO_PT;
            float y = pageHeight - (graphic.y() * PX_TO_PT) - h;

            stream.drawImage(pdImage, x, y, w, h);
        } catch (Exception e) {
            log.error("Image rendering failed for graphic type {}: {}", graphic.type(), e.getMessage(), e);
        }
    }


    @Override
    public Mono<byte[]> redrawWithTemplate(PdfEditableStructure structure, byte[] templateBytes) {
        return Mono.fromCallable(() -> {
            boolean isNewDoc = templateBytes == null || templateBytes.length == 0;
            try (PDDocument document = isNewDoc ? new PDDocument() : Loader.loadPDF(templateBytes)) {
                for (var pageData : structure.pages()) {
                    int pageIndex = pageData.pageNumber() - 1;
                    if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) continue;
                    PDPage page = document.getPage(pageIndex);
                    float pageHeight = page.getMediaBox().getHeight();

                    try (PDPageContentStream stream = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {

                        // Procesar todos los elementos de la capa interactiva
                        if (pageData.elements() != null) {
                            for (TextElement el : pageData.elements()) {
                                // >>> CHANGE: Switch por tipo de elemento
                                String type = el.type() != null ? el.type().toLowerCase() : "text";

                                switch (type) {
                                    case "rect":
                                        drawVectorRect(stream, el, pageHeight);
                                        break;
                                    case "image":
                                        drawSafeImage(document, stream, el, pageHeight);
                                        break;
                                    default:
                                        renderPrecisionText(stream, el, pageHeight);
                                        break;
                                }
                                // <<< CHANGE
                            }
                        }
                    }
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.save(out);
                return out.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void drawVectorRect(PDPageContentStream stream, TextElement el, float pageHeight) throws IOException {
        stream.setStrokingColor(Color.BLACK);
        float x = el.left() * PX_TO_PT;
        float w = el.width() * PX_TO_PT;
        float h = el.height() * PX_TO_PT;
        float y = pageHeight - (el.top() * PX_TO_PT) - h;
        stream.addRect(x, y, w, h);
        stream.stroke();
    }

    private void drawSafeImage(PDDocument doc, PDPageContentStream stream, TextElement el, float pageHeight) {
        String ref = el.resourceReference();
        if (ref == null || !ref.contains(",")) return;
        try {
            byte[] imageBytes = Base64.getMimeDecoder().decode(ref.split(",")[1]);
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) return;
            BufferedImage rgbImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = rgbImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "jpg", baos); // Usamos JPG para asegurar que no hay alpha

            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, baos.toByteArray(), "img_" + el.id());
            float w = el.width() * PX_TO_PT;
            float h = el.height() * PX_TO_PT;
            float x = el.left() * PX_TO_PT;
            float y = pageHeight - (el.top() * PX_TO_PT) - h;

            stream.drawImage(pdImage, x, y, w, h);
            // <<< CHANGE
        } catch (Exception e) {
            log.error("Fallo al exportar imagen {}: {}", el.id(), e.getMessage());
        }
    }

    private void renderPrecisionText(PDPageContentStream stream, TextElement el, float pageHeight) throws IOException {
        if (el.text() == null || el.text().isBlank()) return;
        PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSizePt = el.fontSize() * PX_TO_PT;

        stream.beginText();
        stream.setFont(font, fontSizePt);
        // Ajuste de Baseline para que coincida con la vista web
        float y = pageHeight - (el.top() * PX_TO_PT) - (fontSizePt * 0.9f);
        stream.newLineAtOffset(el.left() * PX_TO_PT, y);
        stream.showText(el.text().trim());
        stream.endText();
    }


    private void renderPrecisionImageFromElement(
            PDDocument doc, PDPageContentStream stream, TextElement el, float pageHeight) {
        // 
        String ref = el.resourceReference();
        if (ref == null || ref.isEmpty() || !ref.contains(",")) return;

        try {
            // Remover el encabezado (ej: data:image/jpeg;base64,)
            String base64Content = ref.substring(ref.indexOf(",") + 1).trim();

            // Decodificar a bytes (MimeDecoder tolera saltos de línea y espacios)
            byte[] imageBytes = Base64.getMimeDecoder().decode(base64Content);

            // Nombre único para indexación y caché interna de PDFBox
            String uniqueImgName = "img_" + java.util.UUID.randomUUID().toString();
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imageBytes, uniqueImgName);

            // Escalar de pixeles (frontend) a puntos (PDF)
            float w = el.width() * PX_TO_PT;
            float h = el.height() * PX_TO_PT;

            // Medidas de seguridad por si las dimensiones llegan en 0
            if (w <= 0) w = 100 * PX_TO_PT;
            if (h <= 0) h = 100 * PX_TO_PT;

            // Extraer left y top nativos y escalar
            float x = el.left() * PX_TO_PT;

            // Conversión de origen: Top-Left (Frontend) -> Bottom-Left (PDFBox)
            float y = pageHeight - (el.top() * PX_TO_PT) - h;

            stream.drawImage(pdImage, x, y, w, h);
        } catch (Exception e) {
            log.error("Image rendering failed for element ID {}: {}", el.id(), e.getMessage(), e);
        }
        // <<< CHANGE
    }

    private void renderPrecisionImage(
            PDDocument doc, PDPageContentStream stream, GraphicElement graphic, float pageHeight)
            throws IOException {
        // 
        String ref = graphic.resourceReference();
        if (ref == null || ref.isEmpty() || "IMG_DATA_STUB".equals(ref)) return;

        try {
            // Logic to strip "data:image/png;base64," if present
            String base64Content = ref.contains(",") ? ref.split(",")[1] : ref;
            byte[] imageBytes = Base64.getDecoder().decode(base64Content.trim());

            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imageBytes, "overlay_img");

            float x = graphic.x() * PX_TO_PT;
            float w = graphic.width() * PX_TO_PT;
            float h = graphic.height() * PX_TO_PT;
            float y = pageHeight - (graphic.y() * PX_TO_PT) - h;

            stream.drawImage(pdImage, x, y, w, h);
        } catch (Exception e) {
            log.error("Image rendering failed for type: {}", graphic.type());
        }
        // <<< CHANGE
    }
    // =====================================================
    // 🟢🟢🟢 CHANGE END 🟢🟢🟢
    // =====================================================

    @Override
    public Mono<PdfDocument> merge(List<PdfDocument> docs) {
        return Mono.fromCallable(() -> {
            return new PdfDocument("merged.pdf", new byte[0], 0, "SYSTEM", Map.of(), List.of());
        });
    }

    @Override
    public Mono<byte[]> redraw(PdfEditableStructure structure) {
        return Mono.just(new byte[0]);
    }

    @Override
    public Mono<PdfDocument> compress(PdfDocument document) {
        return Mono.just(document);
    }
}
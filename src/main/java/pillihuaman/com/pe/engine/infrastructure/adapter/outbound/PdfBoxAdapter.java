package pillihuaman.com.pe.engine.infrastructure.adapter.outbound;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.PdfProcessingPort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.model.GraphicElement;
import pillihuaman.com.pe.engine.domain.model.PdfDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditableStructure;
import pillihuaman.com.pe.engine.domain.model.TextElement;
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
import java.util.UUID;

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
    public Mono<byte[]> redrawWithTemplate(
            PdfEditableStructure structure,
            byte[] templateBytes
    ) {

        return Mono.fromCallable(() -> {

                    try (PDDocument doc = Loader.loadPDF(templateBytes)) {

                        for (var pageData : structure.pages()) {

                            PDPage page = doc.getPage(pageData.pageNumber() - 1);

                            float pageHeight = page.getMediaBox().getHeight();

                            try (PDPageContentStream stream =
                                         new PDPageContentStream(
                                                 doc,
                                                 page,
                                                 PDPageContentStream.AppendMode.APPEND,
                                                 true,
                                                 true
                                         )) {

                                for (TextElement el : pageData.elements()) {

                                    stream.beginText();

                                    stream.setFont(
                                            new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                                            el.fontSize() * 0.75f
                                    );

                                    stream.newLineAtOffset(
                                            el.left() * 0.75f,
                                            pageHeight - (el.top() * 0.75f)
                                    );

                                    stream.showText(
                                            el.text() != null ? el.text() : ""
                                    );

                                    stream.endText();
                                }
                            }
                        }

                        ByteArrayOutputStream out = new ByteArrayOutputStream();

                        doc.save(out);

                        return out.toByteArray();
                    }
                })

                .subscribeOn(Schedulers.boundedElastic());
    }

    private void drawOriginalGraphic(PDPageContentStream stream, GraphicElement graph, float pageHeight) throws IOException {
        if ("RECT".equalsIgnoreCase(graph.type())) {
            stream.setStrokingColor(Color.decode(graph.strokeColor() != null ? graph.strokeColor() : "#000000"));
            float x = graph.x() * PX_TO_PT;
            float w = graph.width() * PX_TO_PT;
            float h = graph.height() * PX_TO_PT;
            float y = pageHeight - (graph.y() * PX_TO_PT) - h;
            stream.addRect(x, y, w, h);
            stream.stroke();
        }
    }

    private void drawVectorRectFromElement(PDPageContentStream stream, TextElement el, float pageHeight) throws IOException {
        stream.setStrokingColor(Color.BLACK);
        float x = el.left() * PX_TO_PT;
        float w = el.width() * PX_TO_PT;
        float h = el.height() * PX_TO_PT;
        float y = pageHeight - (el.top() * PX_TO_PT) - h;
        stream.addRect(x, y, w, h);
        stream.stroke();
    }

    private void renderFidelityText(PDPageContentStream stream, TextElement el, float pageHeight) throws IOException {
        if (el.text() == null || el.text().isBlank()) return;
        PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSizePt = el.fontSize() * PX_TO_PT;

        stream.beginText();
        stream.setFont(font, fontSizePt);

        // Ajuste de interlineado (lineHeight) si la IA lo proporcionó
        float leading = (el.lineHeight() != null) ? Float.parseFloat(el.lineHeight()) : fontSizePt;
        stream.setLeading(leading);

        // Posicionamiento con ajuste de Baseline
        float y = pageHeight - (el.top() * PX_TO_PT) - (fontSizePt * 0.9f);
        stream.newLineAtOffset(el.left() * PX_TO_PT, y);

        // Aquí podrías implementar el letterSpacing si es necesario con stream.setCharacterSpacing()
        if (el.letterSpacing() != null) {
            stream.setCharacterSpacing(Float.parseFloat(el.letterSpacing()));
        }

        stream.showText(el.text().trim());
        stream.endText();
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
        return Mono.fromCallable(() -> mergeSync(docs))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Combina múltiples PDFs e imágenes en un único documento PDF.
     * La detección de tipo se realiza por firma de contenido (magic bytes) y no por el MIME declarado,
     * lo que hace el proceso robusto frente a nombres de archivo o cabeceras incorrectas.
     */
    private PdfDocument mergeSync(List<PdfDocument> docs) throws IOException {
        if (docs == null || docs.isEmpty()) {
            throw new BusinessLogicException("No se proporcionaron archivos para combinar.");
        }

        try (PDDocument target = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            int processedFiles = 0;

            for (PdfDocument doc : docs) {
                byte[] content = doc.content();
                String fileName = (doc.fileName() == null || doc.fileName().isBlank())
                        ? "archivo-desconocido"
                        : doc.fileName();

                if (content == null || content.length == 0) {
                    throw new BusinessLogicException("El archivo '" + fileName + "' está vacío.");
                }

                if (isPdf(content)) {
                    appendPdf(target, merger, content, fileName);
                } else if (isImage(content)) {
                    imageToPage(target, content, fileName);
                } else {
                    throw new BusinessLogicException(
                            "Formato no soportado para el archivo '" + fileName
                                    + "'. Solo se admiten PDF e imágenes (PNG, JPEG, GIF, BMP, TIFF).");
                }
                processedFiles++;
            }

            if (target.getNumberOfPages() == 0) {
                throw new BusinessLogicException(
                        "No se pudo generar ninguna página a partir de los archivos proporcionados.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            target.save(out);
            byte[] merged = out.toByteArray();

            String mergedName = "merged_" + System.currentTimeMillis() + ".pdf";
            log.info("Merge finalizado: {} páginas a partir de {} archivos ({} bytes)",
                    target.getNumberOfPages(), processedFiles, merged.length);

            return new PdfDocument(mergedName, merged, merged.length, null, Map.of(), List.of());
        }
    }

    private void appendPdf(PDDocument target, PDFMergerUtility merger, byte[] content, String fileName) {
        try (PDDocument source = Loader.loadPDF(new RandomAccessReadBuffer(content))) {
            merger.appendDocument(target, source);
        } catch (IOException e) {
            throw new BusinessLogicException(
                    "No se pudo procesar el PDF '" + fileName + "': " + e.getMessage(), e);
        }
    }

    private PDPage imageToPage(PDDocument target, byte[] content, String fileName) throws IOException {
        BufferedImage rgbImage = decodeToRgb(content, fileName);

        ByteArrayOutputStream imageOut = new ByteArrayOutputStream();
        if (!ImageIO.write(rgbImage, "png", imageOut)) {
            throw new BusinessLogicException(
                    "No se pudo codificar la imagen '" + fileName + "' para incluirla en el PDF.");
        }

        PDImageXObject xImage = PDImageXObject.createFromByteArray(
                target, imageOut.toByteArray(), "img_" + UUID.randomUUID());

        PDPage page = new PDPage(PDRectangle.A4);
        target.addPage(page);

        PDRectangle mediaBox = page.getMediaBox();

        float margin = 24f;
        float maxWidth = mediaBox.getWidth() - (2 * margin);
        float maxHeight = mediaBox.getHeight() - (2 * margin);

        float imageWidth = xImage.getWidth();
        float imageHeight = xImage.getHeight();

        // Ajusta la imagen dentro de la página sin ampliar más allá de su resolución nativa.
        float scale = Math.min(1f, Math.min(maxWidth / imageWidth, maxHeight / imageHeight));
        float drawWidth = imageWidth * scale;
        float drawHeight = imageHeight * scale;

        float x = (mediaBox.getWidth() - drawWidth) / 2f;
        float y = (mediaBox.getHeight() - drawHeight) / 2f;

        try (PDPageContentStream stream =
                     new PDPageContentStream(target, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
            stream.drawImage(xImage, x, y, drawWidth, drawHeight);
        }

        return page;
    }

    private BufferedImage decodeToRgb(byte[] content, String fileName) {
        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new BusinessLogicException(
                    "No se pudo decodificar la imagen '" + fileName + "': " + e.getMessage(), e);
        }

        if (original == null) {
            throw new BusinessLogicException(
                    "No se pudo decodificar la imagen '" + fileName + "'. Formato corrupto o no soportado.");
        }

        // Aplana el canal alfa sobre fondo blanco para evitar transparencias problemáticas en PDF.
        BufferedImage rgb = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(original, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    private boolean isImage(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return true; // PNG
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return true; // JPEG
        }
        if (bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return true; // GIF
        }
        if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return true; // BMP
        }
        if (bytes.length >= 4) {
            if (bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 0x2A && bytes[3] == 0x00) {
                return true; // TIFF little-endian
            }
            if (bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0x00 && bytes[3] == 0x2A) {
                return true; // TIFF big-endian
            }
        }
        return false;
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
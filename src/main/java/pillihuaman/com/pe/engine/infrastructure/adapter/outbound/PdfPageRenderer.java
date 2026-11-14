package pillihuaman.com.pe.engine.infrastructure.adapter.outbound;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
@Component
public class PdfPageRenderer {

    private static final float RENDER_DPI = 150f;

    public String renderPageToBase64Png(PDDocument doc, int pageIndex) throws IOException {
        if (doc == null || pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) {
            return null;
        }
        try {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
            if (image == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                return null;
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo renderizar la página {} a PNG: {}", pageIndex + 1, e.getMessage());
            return null;
        }
    }
}

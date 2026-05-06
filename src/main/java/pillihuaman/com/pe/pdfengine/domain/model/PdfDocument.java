package pillihuaman.com.pe.pdfengine.domain.model;

import java.util.List;
import java.util.Map;

public record PdfDocument(
        String fileName,
        byte[] content,
        long size,
        String tenantId,
        Map<String, String> metadata,
        List<String> permissions
) {
}


package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;
import java.time.Instant;

public record WhatsAppMessage(
        String id,
        String sender,
        String recipient,
        String originalText,
        String translatedText,
        String sourceLanguage,
        String targetLanguage,
        Instant timestamp,
        String tenantId,
        boolean outgoing,
        boolean translated
) implements Serializable {
}
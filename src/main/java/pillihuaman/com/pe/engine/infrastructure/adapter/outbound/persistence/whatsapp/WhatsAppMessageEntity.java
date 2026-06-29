package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.whatsapp;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "whatsapp_messages")
public record WhatsAppMessageEntity(
        @Id String id,
        String sender,
        String recipient,
        String originalText,
        String translatedText,
        String sourceLanguage,
        String targetLanguage,
        Instant timestamp,
        String tenantId,
        boolean outgoing,
        boolean translated,
        String status,
        String externalMessageId,
        String failureReason,
        int retryCount,
        String messageKind,
        String mediaUrl,
        String mimeType,
        String caption,
        Boolean hasMedia
) {
}
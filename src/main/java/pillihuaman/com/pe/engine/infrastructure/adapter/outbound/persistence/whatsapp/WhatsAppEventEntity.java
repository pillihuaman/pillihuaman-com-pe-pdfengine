package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.whatsapp;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "whatsapp_events")
public record WhatsAppEventEntity(
        @Id String id,
        String tenantId,
        String eventType,
        String rawPayload,
        Instant receivedAt,
        String status,
        String processingError
) {
}

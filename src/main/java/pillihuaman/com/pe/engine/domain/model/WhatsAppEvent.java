package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;
import java.time.Instant;

public record WhatsAppEvent(
        String id,
        String tenantId,
        String eventType,
        String rawPayload,
        Instant receivedAt,
        String status,
        String processingError
) implements Serializable {
}

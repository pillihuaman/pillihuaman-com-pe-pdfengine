package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;
import java.time.Instant;

public record ChannelStateDTO(
        String channel,
        String status,
        String instanceName,
        Instant lastCheckedAt,
        String details,
        boolean retryable
) implements Serializable {
}
package pillihuaman.com.pe.engine.domain.model.measurement;

import java.io.Serializable;
import java.time.Instant;

public record CustomerBiometricProfile(
        String id,
        String accountId,
        Double height,
        Double weight,
        String fitPreference,
        Double chestCm,
        Double shoulderCm,
        Instant lastUpdated,
        String tenantId
) implements Serializable {
}
package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "customer_biometric_profiles")
public record CustomerBiometricProfileEntity(
        @Id String id,
        String accountId,
        Double height,
        Double weight,
        String fitPreference,
        Double chestCm,
        Double shoulderCm,
        Instant lastUpdated,
        String tenantId
) {
}
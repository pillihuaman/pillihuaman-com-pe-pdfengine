package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;

public record WhatsAppContact(
        String id,
        String phoneNumber,
        String name,
        String preferredLanguage,
        String tenantId
) implements Serializable {
}
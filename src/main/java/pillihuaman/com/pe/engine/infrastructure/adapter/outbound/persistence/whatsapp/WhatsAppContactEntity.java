package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.whatsapp;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "whatsapp_contacts")
public record WhatsAppContactEntity(
        @Id String id,
        String phoneNumber,
        String name,
        String preferredLanguage,
        String tenantId
) {
}
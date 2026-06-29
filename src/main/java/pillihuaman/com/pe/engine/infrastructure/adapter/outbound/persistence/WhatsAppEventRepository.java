package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.whatsapp.WhatsAppEventEntity;

@Repository
public interface WhatsAppEventRepository extends ReactiveMongoRepository<WhatsAppEventEntity, String> {
}
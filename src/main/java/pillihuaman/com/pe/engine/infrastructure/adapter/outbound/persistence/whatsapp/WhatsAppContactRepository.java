package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.whatsapp;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface WhatsAppContactRepository extends ReactiveMongoRepository<WhatsAppContactEntity, String> {
    Mono<WhatsAppContactEntity> findByPhoneNumberAndTenantId(String phoneNumber, String tenantId);

    Flux<WhatsAppContactEntity> findByTenantId(String tenantId);

}
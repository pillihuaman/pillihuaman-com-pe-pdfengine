package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.whatsapp;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface WhatsAppMessageRepository extends ReactiveMongoRepository<WhatsAppMessageEntity, String> {
    Flux<WhatsAppMessageEntity> findByTenantIdOrderByTimestampDesc(String tenantId);

    @Query(value = "{ 'tenantId': ?0, '$or': [ { 'sender': ?1 }, { 'recipient': ?1 } ] }", sort = "{ 'timestamp': 1 }")
    Flux<WhatsAppMessageEntity> findConversationByTenantAndContact(String tenantId, String phoneNumber);

    Mono<WhatsAppMessageEntity> findByTenantIdAndExternalMessageId(String tenantId, String externalMessageId);


}
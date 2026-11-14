package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface CustomerBiometricProfileRepository extends ReactiveMongoRepository<CustomerBiometricProfileEntity, String> {
    Mono<CustomerBiometricProfileEntity> findByAccountIdAndTenantId(String accountId, String tenantId);
}
package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence.measurement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.application.port.outbound.persistence.CustomerBiometricProfilePort;
import pillihuaman.com.pe.engine.domain.model.measurement.CustomerBiometricProfile;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerBiometricProfileMongoAdapter implements CustomerBiometricProfilePort {

    private final CustomerBiometricProfileRepository repository;

    @Override
    public Mono<CustomerBiometricProfile> saveOrUpdate(CustomerBiometricProfile profile) {
        return repository.findByAccountIdAndTenantId(profile.accountId(), profile.tenantId())
                .flatMap(existing -> {
                    CustomerBiometricProfileEntity updated = new CustomerBiometricProfileEntity(
                            existing.id(),
                            existing.accountId(),
                            profile.height() != null ? profile.height() : existing.height(),
                            profile.weight() != null ? profile.weight() : existing.weight(),
                            profile.fitPreference() != null ? profile.fitPreference() : existing.fitPreference(),
                            profile.chestCm() != null ? profile.chestCm() : existing.chestCm(),
                            profile.shoulderCm() != null ? profile.shoulderCm() : existing.shoulderCm(),
                            Instant.now(),
                            existing.tenantId()
                    );
                    return repository.save(updated);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CustomerBiometricProfileEntity newEntity = new CustomerBiometricProfileEntity(
                            UUID.randomUUID().toString(),
                            profile.accountId(),
                            profile.height(),
                            profile.weight(),
                            profile.fitPreference(),
                            profile.chestCm(),
                            profile.shoulderCm(),
                            Instant.now(),
                            profile.tenantId()
                    );
                    return repository.save(newEntity);
                }))
                .map(saved -> new CustomerBiometricProfile(
                        saved.id(),
                        saved.accountId(),
                        saved.height(),
                        saved.weight(),
                        saved.fitPreference(),
                        saved.chestCm(),
                        saved.shoulderCm(),
                        saved.lastUpdated(),
                        saved.tenantId()
                ));
    }
}
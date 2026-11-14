package pillihuaman.com.pe.engine.application.port.outbound.persistence;

import pillihuaman.com.pe.engine.domain.model.measurement.CustomerBiometricProfile;
import reactor.core.publisher.Mono;

public interface CustomerBiometricProfilePort {
    Mono<CustomerBiometricProfile> saveOrUpdate(CustomerBiometricProfile profile);
}
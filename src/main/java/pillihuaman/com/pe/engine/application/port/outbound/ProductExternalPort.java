package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.measurement.Garment;
import reactor.core.publisher.Mono;

public interface ProductExternalPort {
    Mono<Garment> getProductById(String productId, String bearerToken);
}
package pillihuaman.com.pe.engine.application.port.outbound;

import pillihuaman.com.pe.engine.domain.model.external.PromptAndVersionDTO;
import pillihuaman.com.pe.engine.domain.model.external.ReqPrompt;
import reactor.core.publisher.Mono;

public interface PromptExternalPort {
    Mono<PromptAndVersionDTO> getPromptByCharacteristics(ReqPrompt request, String bearerToken);
}
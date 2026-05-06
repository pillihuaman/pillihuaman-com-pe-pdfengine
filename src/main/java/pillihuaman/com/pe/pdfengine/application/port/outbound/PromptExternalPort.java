package pillihuaman.com.pe.pdfengine.application.port.outbound;

import pillihuaman.com.pe.pdfengine.domain.model.external.PromptAndVersionDTO;
import pillihuaman.com.pe.pdfengine.domain.model.external.ReqPrompt;
import reactor.core.publisher.Mono;

public interface PromptExternalPort {
    Mono<PromptAndVersionDTO> getPromptByCharacteristics(ReqPrompt request, String bearerToken);
}
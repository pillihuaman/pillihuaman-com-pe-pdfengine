package pillihuaman.com.pe.engine.application.port.outbound;

import reactor.core.publisher.Mono;

public interface WhatsAppTranslationPort {
    Mono<String> translateText(String text, String sourceLang, String targetLang, String bearerToken);
}
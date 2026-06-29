package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.domain.model.WhatsAppMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WhatsAppWebSocketSessionManager {

    private final Map<String, Sinks.Many<WhatsAppMessage>> tenantSinks =
            new ConcurrentHashMap<>();

    public Sinks.Many<WhatsAppMessage> getSinkForTenant(String tenantId) {

        log.info(
                "SESSION MANAGER INSTANCE = {}",
                System.identityHashCode(this)
        );

        return tenantSinks.computeIfAbsent(
                tenantId,
                key -> {

                    log.info(
                            "CREATING REPLAY SINK FOR TENANT = {}",
                            key
                    );

                    return Sinks.many()
                            .replay()
                            .limit(50);
                }
        );
    }

    public Flux<WhatsAppMessage> getStreamForTenant(String tenantId) {

        log.info(
                "OPENING STREAM FOR TENANT = {}",
                tenantId
        );

        return getSinkForTenant(tenantId)
                .asFlux();
    }

    public void pushMessageToTenant(
            String tenantId,
            WhatsAppMessage message
    ) {

        log.info(
                "SESSION MANAGER INSTANCE = {}",
                System.identityHashCode(this)
        );

        log.info(
                "EMIT TO TENANT = {}",
                tenantId
        );

        log.info(
                "CURRENT SINKS = {}",
                tenantSinks.keySet()
        );

        Sinks.Many<WhatsAppMessage> sink =
                tenantSinks.get(tenantId);

        if (sink == null) {

            log.error(
                    "NO SINK FOUND FOR {}",
                    tenantId
            );

            return;
        }

        Sinks.EmitResult result =
                sink.tryEmitNext(message);

        log.info(
                "EMIT RESULT = {}",
                result
        );
    }
}
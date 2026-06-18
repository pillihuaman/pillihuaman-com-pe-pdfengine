package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppWebSocketHandler implements WebSocketHandler {

    private final WhatsAppWebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @jakarta.annotation.PostConstruct
    public void init() {

        log.info(
                "WHATSAPP HANDLER CREATED = {}",
                System.identityHashCode(this)
        );

        log.info(
                "SESSION MANAGER INJECTED = {}",
                System.identityHashCode(sessionManager)
        );
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        log.info(
                "HANDLER INSTANCE = {}",
                System.identityHashCode(this)
        );

        log.info(
                "SESSION MANAGER IN HANDLER = {}",
                System.identityHashCode(sessionManager)
        );

        String query = session.getHandshakeInfo()
                .getUri()
                .getQuery();

        String tenantId = "DEFAULT";

        if (query != null && query.contains("tenantId=")) {
            tenantId = query.split("tenantId=")[1].split("&")[0];
        }

        final String activeTenant = tenantId;

        log.info(
                "WEBSOCKET CONNECTED TENANT = {}",
                activeTenant
        );

        return session.send(

                        sessionManager
                                .getStreamForTenant(activeTenant)
                                .map(msg -> {

                                    try {

                                        log.info(
                                                "SENDING MESSAGE TO TENANT {} -> {}",
                                                activeTenant,
                                                msg.id()
                                        );

                                        return session.textMessage(
                                                objectMapper.writeValueAsString(msg)
                                        );

                                    } catch (Exception e) {

                                        log.error(
                                                "Failed to serialize message",
                                                e
                                        );

                                        return session.textMessage("{}");
                                    }
                                })

                )
                .doOnSubscribe(sub ->
                        log.info(
                                "SUBSCRIBED TO STREAM FOR TENANT {}",
                                activeTenant
                        )
                )
                .doOnError(error ->
                        log.error(
                                "WebSocket error on tenant {}",
                                activeTenant,
                                error
                        )
                )
                .doOnTerminate(() ->
                        log.info(
                                "WebSocket connection closed for Tenant: {}",
                                activeTenant
                        )
                );
    }
}



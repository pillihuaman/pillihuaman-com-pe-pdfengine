package pillihuaman.com.pe.engine.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.websocket.WhatsAppWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.reflections.Reflections.log;

@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final WhatsAppWebSocketHandler handler;


    @Bean
    public HandlerMapping webSocketMapping() {

        log.info("REGISTERING WS PATH /ws/whatsapp");

        Map<String, WebSocketHandler> map = new HashMap<>();

        map.put("/ws/whatsapp", handler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();

        mapping.setOrder(-1);
        mapping.setUrlMap(map);
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.addAllowedOriginPattern("*");
        corsConfig.addAllowedMethod("*");
        corsConfig.addAllowedHeader("*");
        corsConfig.setAllowCredentials(true);
        mapping.setCorsConfigurations(Map.of("/ws/whatsapp", corsConfig));

        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
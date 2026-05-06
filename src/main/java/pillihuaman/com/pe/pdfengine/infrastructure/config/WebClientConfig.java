package pillihuaman.com.pe.pdfengine.infrastructure.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Enterprise WebClient and WebFlux Infrastructure Configuration.
 * Optimized to handle large PDF payloads and binary streams.
 */
@Configuration
public class WebClientConfig implements WebFluxConfigurer {
    private static final int MAX_BUFFER_SIZE = 100 * 1024 * 1024; // 100MB

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

        configurer.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE);

    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(MAX_BUFFER_SIZE))
                        .build());
    }
}
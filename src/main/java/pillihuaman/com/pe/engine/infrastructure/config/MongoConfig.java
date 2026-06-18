package pillihuaman.com.pe.engine.infrastructure.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/**
 * Configuration class for Reactive MongoDB.
 */
@Configuration
@EnableReactiveMongoRepositories(
        basePackages = "pillihuaman.com.pe.engine.infrastructure.adapter.outbound.persistence"
)
public class MongoConfig extends AbstractReactiveMongoConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Override
    protected String getDatabaseName() {
        return "fibertechia";
    }

    @Override
    @Bean
    public MongoClient reactiveMongoClient() {
        return MongoClients.create(mongoUri);

    }
}
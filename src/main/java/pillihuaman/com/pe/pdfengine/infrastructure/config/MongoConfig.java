package pillihuaman.com.pe.pdfengine.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/** Configuration class for Reactive MongoDB. */
@Configuration
@EnableReactiveMongoRepositories(
    basePackages = "pillihuaman.com.pe.pdfengine.infrastructure.adapter.outbound.persistence")
public class MongoConfig extends AbstractReactiveMongoConfiguration {
  @Override
  protected String getDatabaseName() {
    return "fibertechia";
  }
}

package pillihuaman.com.pe.engine.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class EvolutionApiFlowAndCredentialsDiagnostic {

    public boolean isFlowActive() {
        return true;
    }
}
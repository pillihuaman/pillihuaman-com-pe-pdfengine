package pillihuaman.com.pe.pdfengine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application {

    public static void main(final String[] args) {


        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.REACTIVE);
        app.run(args);


        log.info("🚀 PDF Engine running in PURE WEBFLUX mode (NO SERVLET)");
    }
}
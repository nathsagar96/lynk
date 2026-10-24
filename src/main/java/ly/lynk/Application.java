package ly.lynk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Main entry point for the Lynk URL shortener application. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class Application {

    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

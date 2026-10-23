package ly.lynk.config;

import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ValidationConfigurationCustomizer clockValidationCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}

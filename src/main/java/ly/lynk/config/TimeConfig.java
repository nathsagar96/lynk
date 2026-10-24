package ly.lynk.config;

import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides time-related Spring beans and validation configuration. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /**
     * Returns a system UTC {@link Clock} bean.
     *
     * @return the system UTC Clock
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Configures Jakarta Bean Validation to use the application's {@link Clock} provider.
     *
     * @param clock the application Clock bean
     * @return the validation customizer
     */
    @Bean
    ValidationConfigurationCustomizer clockValidationCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}

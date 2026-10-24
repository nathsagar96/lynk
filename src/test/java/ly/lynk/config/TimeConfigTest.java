package ly.lynk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.validation.ClockProvider;
import jakarta.validation.Configuration;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;

class TimeConfigTest {

    private final TimeConfig timeConfig = new TimeConfig();

    @Test
    @DisplayName("clock bean produces a UTC clock")
    void clockProducesUtcClock() {
        Clock clock = timeConfig.clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("clockValidationCustomizer configures the validation clock provider")
    void clockValidationCustomizerSetsClockProvider() {
        Clock clock = timeConfig.clock();
        ValidationConfigurationCustomizer customizer = timeConfig.clockValidationCustomizer(clock);

        @SuppressWarnings("rawtypes")
        Configuration configuration = mock(Configuration.class);
        ArgumentCaptor<ClockProvider> captor = ArgumentCaptor.forClass(ClockProvider.class);

        customizer.customize(configuration);

        verify(configuration).clockProvider(captor.capture());
        assertThat(captor.getValue().getClock()).isSameAs(clock);
    }
}

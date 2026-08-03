package ly.lynk.shortcode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Base62EncoderTest {

    @Test
    void shouldEncodeZeroToAllZeros() {
        String result = Base62Encoder.encode(0L);
        assertThat(result).isEqualTo("00000000000");
    }

    @Test
    void shouldEncodeToExactly11Characters() {
        String result = Base62Encoder.encode(123456789L);
        assertThat(result).hasSize(11);
    }

    @Test
    void shouldEncodeMaxLongTo11Characters() {
        String result = Base62Encoder.encode(Long.MAX_VALUE);
        assertThat(result).hasSize(11);
    }

    @Test
    void shouldProduceUrlSafeCharactersOnly() {
        String result = Base62Encoder.encode(987654321L);
        assertThat(result).matches("[0-9a-zA-Z]{11}");
    }

    @Test
    void shouldProduceDifferentOutputsForDifferentInputs() {
        String a = Base62Encoder.encode(1L);
        String b = Base62Encoder.encode(2L);
        assertThat(a).isNotEqualTo(b);
    }
}

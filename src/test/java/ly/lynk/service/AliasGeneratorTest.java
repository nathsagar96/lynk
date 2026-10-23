package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AliasGeneratorTest {

    private final AliasGenerator generator = new AliasGenerator();

    @Test
    @DisplayName("generated aliases have exactly the requested length")
    void generatesAliasOfRequestedLength() {
        assertThat(generator.generate(7)).hasSize(7);
        assertThat(generator.generate(1)).hasSize(1);
        assertThat(generator.generate(32)).hasSize(32);
    }

    @Test
    @DisplayName("generated aliases only contain Base62 characters")
    void generatesOnlyBase62Characters() {
        for (int i = 0; i < 200; i++) {
            assertThat(generator.generate(10)).matches("^[0-9a-zA-Z]{10}$");
        }
    }

    @Test
    @DisplayName("generated aliases are random enough to avoid collisions")
    void generatesDistinctAliases() {
        Set<String> aliases = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            aliases.add(generator.generate(7));
        }
        assertThat(aliases).hasSize(500);
    }

    @Test
    @DisplayName("a non-positive length is rejected")
    void rejectsNonPositiveLength() {
        assertThatThrownBy(() -> generator.generate(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}

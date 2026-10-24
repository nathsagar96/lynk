package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlMappingWriterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private UrlMappingRepository repository;

    @Test
    @DisplayName("save delegates to repository saveAndFlush and returns the persisted mapping")
    void saveDelegatesToSaveAndFlush() {
        UrlMappingWriter writer = new UrlMappingWriter(repository);
        UrlMapping mapping = new UrlMapping("https://spring.io", "spring", CREATED_AT, null, null);
        when(repository.saveAndFlush(mapping)).thenReturn(mapping);

        UrlMapping result = writer.save(mapping);

        assertThat(result).isSameAs(mapping);
        verify(repository).saveAndFlush(mapping);
    }
}

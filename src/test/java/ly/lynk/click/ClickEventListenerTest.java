package ly.lynk.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickEventListenerTest {

    @Mock
    private ClickRepository clickRepository;

    @InjectMocks
    private ClickEventListener listener;

    @Test
    void shouldBufferClickEvent() {
        var event = new ClickEvent("abc123", "192.168.1.1", "Mozilla/5.0", "https://google.com", Instant.now());

        listener.onClickEvent(event);

        verify(clickRepository, never()).saveAll(any());
    }

    @Test
    void shouldFlushBufferedEvents() {
        var event1 = new ClickEvent("abc123", "192.168.1.1", "Mozilla/5.0", null, Instant.now());
        var event2 = new ClickEvent("def456", "10.0.0.1", "Chrome/120", null, Instant.now());

        listener.onClickEvent(event1);
        listener.onClickEvent(event2);
        listener.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClickEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void shouldAnonymizeIpv4BeforeBuffering() {
        var event = new ClickEvent("abc123", "192.168.1.100", "Mozilla/5.0", null, Instant.now());

        listener.onClickEvent(event);
        listener.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClickEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getIpAddress()).isEqualTo("192.168.1.0");
    }

    @Test
    void shouldAnonymizeIpv6BeforeBuffering() {
        var event =
                new ClickEvent("abc123", "2001:0db8:85a3:0000:0000:0000:0000:0001", "Mozilla/5.0", null, Instant.now());

        listener.onClickEvent(event);
        listener.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClickEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getIpAddress()).isEqualTo("2001:0db8:85a3::");
    }

    @Test
    void shouldHandleNullIpAddress() {
        var event = new ClickEvent("abc123", null, "Mozilla/5.0", null, Instant.now());

        listener.onClickEvent(event);
        listener.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClickEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getIpAddress()).isNull();
    }

    @Test
    void shouldNotFlushWhenBufferIsEmpty() {
        listener.flush();

        verify(clickRepository, never()).saveAll(any());
    }
}

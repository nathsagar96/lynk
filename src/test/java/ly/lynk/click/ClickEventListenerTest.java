package ly.lynk.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
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
    void shouldPersistClickEvent() {
        var event = new ClickEvent("abc123", "192.168.1.1", "Mozilla/5.0", "https://google.com", Instant.now());

        listener.onClickEvent(event);

        verify(clickRepository).save(any(ClickEntity.class));
    }

    @Test
    void shouldAnonymizeIpv4BeforePersisting() {
        var event = new ClickEvent("abc123", "192.168.1.100", "Mozilla/5.0", null, Instant.now());
        var captor = ArgumentCaptor.forClass(ClickEntity.class);

        listener.onClickEvent(event);

        verify(clickRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("192.168.1.0");
    }

    @Test
    void shouldAnonymizeIpv6BeforePersisting() {
        var event =
                new ClickEvent("abc123", "2001:0db8:85a3:0000:0000:0000:0000:0001", "Mozilla/5.0", null, Instant.now());
        var captor = ArgumentCaptor.forClass(ClickEntity.class);

        listener.onClickEvent(event);

        verify(clickRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("2001:0db8:85a3::");
    }

    @Test
    void shouldHandleNullIpAddress() {
        var event = new ClickEvent("abc123", null, "Mozilla/5.0", null, Instant.now());
        var captor = ArgumentCaptor.forClass(ClickEntity.class);

        listener.onClickEvent(event);

        verify(clickRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isNull();
    }

    @Test
    void shouldNotThrowWhenDatabaseFails() {
        var event = new ClickEvent("abc123", "192.168.1.1", "Mozilla/5.0", null, Instant.now());
        doThrow(new RuntimeException("DB down")).when(clickRepository).save(any());

        assertThatCode(() -> listener.onClickEvent(event)).doesNotThrowAnyException();
    }
}

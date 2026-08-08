package ly.lynk.click;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClickEventListener {

    private final ClickRepository clickRepository;

    @Async
    @EventListener
    public void onClickEvent(ClickEvent event) {
        try {
            var clickEntity = ClickEntity.builder()
                    .shortcode(event.shortcode())
                    .ipAddress(IpAnonymizer.anonymize(event.ipAddress()))
                    .userAgent(event.userAgent())
                    .referer(event.referer())
                    .clickedAt(event.clickedAt())
                    .build();
            clickRepository.save(clickEntity);
            log.debug("Persisted click for shortcode: {}", event.shortcode());
        } catch (Exception ex) {
            log.warn("Failed to persist click for shortcode: {}", event.shortcode(), ex);
        }
    }
}

package ly.lynk.click;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClickEventListener {

    private final ClickRepository clickRepository;
    private final Queue<ClickEntity> buffer = new ConcurrentLinkedQueue<>();

    @Async
    @EventListener
    public void onClickEvent(ClickEvent event) {
        var clickEntity = ClickEntity.builder()
                .shortcode(event.shortcode())
                .ipAddress(IpAnonymizer.anonymize(event.ipAddress()))
                .userAgent(event.userAgent())
                .referer(event.referer())
                .clickedAt(event.clickedAt())
                .build();
        buffer.add(clickEntity);
        log.debug("Buffered click for shortcode: {}", event.shortcode());
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void flush() {
        List<ClickEntity> batch = new ArrayList<>();
        ClickEntity entity;
        int maxPerFlush = 5000;
        while ((entity = buffer.poll()) != null && batch.size() < maxPerFlush) {
            batch.add(entity);
        }
        if (!batch.isEmpty()) {
            try {
                clickRepository.saveAll(batch);
                log.debug("Flushed {} click events to database", batch.size());
            } catch (Exception ex) {
                log.error("Failed to flush {} click events, re-enqueuing", batch.size(), ex);
                buffer.addAll(batch);
            }
        }
    }
}

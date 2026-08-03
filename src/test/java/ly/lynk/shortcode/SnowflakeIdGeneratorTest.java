package ly.lynk.shortcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import ly.lynk.common.LynkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

    private SnowflakeIdGenerator generator;

    @BeforeEach
    void setUp() {
        var snowflakeProps = new LynkProperties.SnowflakeProperties(1, Instant.parse("2025-01-01T00:00:00Z"));
        generator = new SnowflakeIdGenerator(snowflakeProps);
    }

    @Test
    void shouldGeneratePositiveId() {
        long id = generator.nextId();
        assertThat(id).isPositive();
    }

    @Test
    void shouldGenerateUniqueIds() {
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void shouldGenerateMonotonicallyIncreasingIds() {
        long prev = generator.nextId();
        for (int i = 0; i < 1_000; i++) {
            long next = generator.nextId();
            assertThat(next).isGreaterThan(prev);
            prev = next;
        }
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 1_000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < idsPerThread; i++) {
                            ids.add(generator.nextId());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        assertThat(ids).hasSize(threadCount * idsPerThread);
    }
}

package ly.lynk.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

    private SnowflakeIdGenerator generator;

    @BeforeEach
    void setUp() {
        var snowflakeProps = new SnowflakeProperties(1, Instant.parse("2025-01-01T00:00:00Z"));
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

    @Test
    void shouldThrowWhenClockMovesBackwards() {
        long t = Instant.parse("2025-01-02T00:00:00Z").toEpochMilli();
        var times = new ArrayDeque<>(List.of(t, t - 100));
        var fake = new FakeClockGenerator(snowflakeProperties(), times);

        fake.nextId();

        assertThatThrownBy(fake::nextId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldKeepGeneratingMonotonicIdsWhenSequenceOverflowsSameMillisecond() {
        long t = Instant.parse("2025-01-02T00:00:00Z").toEpochMilli();
        var times = new ArrayDeque<Long>();
        for (int i = 0; i < 4_097; i++) {
            times.add(t);
        }
        for (int i = 0; i < 1_000; i++) {
            times.add(t + 1);
        }
        var fake = new FakeClockGenerator(snowflakeProperties(), times);

        long prev = fake.nextId();
        for (int i = 0; i < 5_000; i++) {
            long next = fake.nextId();
            assertThat(next).isGreaterThan(prev);
            prev = next;
        }
    }

    @Test
    void shouldRejectFutureEpoch() {
        var futureProps = new SnowflakeProperties(1, Instant.parse("2025-01-01T00:00:00Z"));
        assertThatThrownBy(() -> new PastClockGenerator(futureProps)).isInstanceOf(IllegalArgumentException.class);
    }

    private static SnowflakeProperties snowflakeProperties() {
        return new SnowflakeProperties(1, Instant.parse("2025-01-01T00:00:00Z"));
    }

    private static final class FakeClockGenerator extends SnowflakeIdGenerator {

        private final Deque<Long> times;
        private long lastReturned = -1L;

        FakeClockGenerator(SnowflakeProperties props, Deque<Long> times) {
            super(props);
            this.times = times;
        }

        @Override
        protected long currentTimeMillis() {
            if (times == null) {
                return Instant.parse("2025-01-01T00:00:00Z").toEpochMilli();
            }
            if (times.isEmpty()) {
                return lastReturned;
            }
            lastReturned = times.pollFirst();
            return lastReturned;
        }
    }

    private static final class PastClockGenerator extends SnowflakeIdGenerator {

        private static final long PAST = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();

        PastClockGenerator(SnowflakeProperties props) {
            super(props);
        }

        @Override
        protected long currentTimeMillis() {
            return PAST;
        }
    }
}

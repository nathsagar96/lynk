package ly.lynk.shortcode;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import ly.lynk.common.SnowflakeProperties;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    private static final int MACHINE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1; // 4095
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1; // 1023

    private final long machineId;
    private final long epochMillis;
    private final ReentrantLock lock = new ReentrantLock();

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(SnowflakeProperties snowflakeProperties) {
        this.machineId = snowflakeProperties.machineId();
        this.epochMillis = snowflakeProperties.epoch().toEpochMilli();

        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                    "Machine ID must be between 0 and %d, got: %d".formatted(MAX_MACHINE_ID, machineId));
        }

        long now = currentTimeMillis();
        if (epochMillis > now) {
            throw new IllegalArgumentException("Epoch must be in the past, configured epoch %d is after current time %d"
                    .formatted(epochMillis, now));
        }
    }

    public long nextId() {
        lock.lock();
        try {
            long currentTimestamp = currentTimeMillis();

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    currentTimestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0;
            }

            if (currentTimestamp < lastTimestamp) {
                throw new IllegalStateException("Clock moved backwards. Refusing to generate ID for %d milliseconds"
                        .formatted(lastTimestamp - currentTimestamp));
            }

            lastTimestamp = currentTimestamp;

            return ((currentTimestamp - epochMillis) << (MACHINE_ID_BITS + SEQUENCE_BITS))
                    | (machineId << SEQUENCE_BITS)
                    | sequence;
        } finally {
            lock.unlock();
        }
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    protected long currentTimeMillis() {
        return Instant.now().toEpochMilli();
    }
}

package ly.lynk.click;

import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickPartitionService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * Creates partitions for the next 3 months if they don't exist.
     * Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void createFuturePartitions() {
        YearMonth currentMonth = YearMonth.now(clock);
        log.info("Checking click table partitions starting from {}", currentMonth);

        for (int i = 0; i <= 3; i++) {
            YearMonth partitionMonth = currentMonth.plusMonths(i);
            createPartitionIfNotExists(partitionMonth);
        }
    }

    /**
     * Drops partitions older than the retention period.
     * Default retention: 12 months.
     * Runs weekly on Sunday at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void dropExpiredPartitions() {
        YearMonth retentionCutoff = YearMonth.now(clock).minusMonths(12);
        log.info("Dropping click partitions older than {}", retentionCutoff);

        String sql =
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE ? AND tablename != 'click_default'";

        jdbcTemplate.queryForList(sql, String.class, "click_%").stream()
                .filter(name -> !name.equals("click_default"))
                .filter(name -> parsePartitionDate(name).isPresent())
                .filter(name -> parsePartitionDate(name).get().isBefore(retentionCutoff))
                .forEach(this::dropPartition);
    }

    private void createPartitionIfNotExists(YearMonth month) {
        String partitionName = "click_%d_%02d".formatted(month.getYear(), month.getMonthValue());
        String startDate = month.atDay(1).toString();
        String endDate = month.plusMonths(1).atDay(1).toString();

        String checkSql = """
            SELECT COUNT(*) FROM pg_tables
            WHERE schemaname = 'public' AND tablename = ?
            """;

        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, partitionName);
        if (count != null && count == 0) {
            String createSql = """
                CREATE TABLE %s PARTITION OF click
                FOR VALUES FROM ('%s') TO ('%s')
                """.formatted(partitionName, startDate, endDate);
            jdbcTemplate.execute(createSql);
            log.info("Created partition: {}", partitionName);
        }
    }

    private void dropPartition(String partitionName) {
        try {
            String sql = "DROP TABLE %s".formatted(partitionName);
            jdbcTemplate.execute(sql);
            log.info("Dropped partition: {}", partitionName);
        } catch (Exception e) {
            log.error("Failed to drop partition {}: {}", partitionName, e.getMessage());
        }
    }

    private java.util.Optional<YearMonth> parsePartitionDate(String partitionName) {
        try {
            String[] parts = partitionName.split("_");
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[1]);
                int month = Integer.parseInt(parts[2]);
                return java.util.Optional.of(YearMonth.of(year, month));
            }
        } catch (NumberFormatException e) {
            log.debug("Could not parse partition date from: {}", partitionName);
        }
        return java.util.Optional.empty();
    }
}

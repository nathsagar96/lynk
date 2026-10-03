package com.lynk.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lynk.AbstractIntegrationTestBase;
import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.service.UrlShortenerService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the click counter increment is atomic under concurrent redirects.
 * <p>
 * Each worker holds its own connection and its own transaction, all released onto the same row at a
 * barrier so the updates genuinely overlap. A lost-update implementation (load the entity, add one,
 * save) would land somewhere below {@link #WORKERS}; the single-statement
 * {@code click_count = click_count + 1} must land on exactly {@link #WORKERS}.
 * <p>
 * {@code WORKERS} matches the Hikari pool size configured below, so every worker can hold a
 * connection at the same time instead of deadlocking while waiting for the pool.
 */
@Tag("integration")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=10")
class UrlMappingRepositoryConcurrencyIT extends AbstractIntegrationTestBase {

    /**
     * Matches the Hikari pool size above, so all workers can be in flight at once.
     */
    private static final int WORKERS = 10;

    private static final String DESTINATION = "https://example.com/concurrent";

    private static final String ALIAS = "concurrent-alias";

    @Autowired
    private UrlShortenerService service;

    @Autowired
    private JpaTransactionManager transactionManager;

    /**
     * Runs one increment per worker against the same row and asserts every single one lands.
     */
    @Test
    void incrementClickCount_losesNoUpdates_whenRacingConcurrentTransactions() throws Exception {
        service.shorten(new ShortenUrlRequest(DESTINATION, ALIAS, null));
        CyclicBarrier startLine = new CyclicBarrier(WORKERS);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> inFlight = new ArrayList<>(WORKERS);
            for (int i = 0; i < WORKERS; i++) {
                inFlight.add(workers.submit(() -> runOneIncrement(ALIAS, startLine, failures)));
            }
            for (Future<?> attempt : inFlight) {
                attempt.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(failures).isEmpty();
        UrlMapping reloaded = repository.findByShortCode(ALIAS).orElseThrow();
        assertThat(reloaded.getClickCount()).isEqualTo(WORKERS);
    }

    /**
     * One increment in its own transaction: line up with the other workers at the barrier, then run
     * the update and commit.
     * <p>
     * The barrier sits inside the transaction, before the update, so every worker is holding its
     * connection and its row lock window open at the same moment — the overlap a lost-update
     * implementation would lose counts in. {@code incrementClickCount} is called directly rather
     * than through the service so the read that would precede it in a real redirect cannot
     * serialise the race.
     */
    private void runOneIncrement(String shortCode, CyclicBarrier startLine, List<Throwable> failures) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            transaction.executeWithoutResult(_ -> {
                try {
                    startLine.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                    throw new IllegalStateException("workers failed to line up", e);
                }
                repository.incrementClickCount(shortCode);
            });
        } catch (Throwable t) {
            failures.add(t);
        }
    }
}

package com.lynk;

import com.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Base for tests that need a real PostgreSQL with Flyway migrations applied.
 * <p>
 * The container is a single instance shared by the whole test run, so the table is emptied before
 * each test to keep them order-independent.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "lynk.base-url=http://localhost:8080")
public abstract class AbstractIntegrationTestBase {

    @Autowired
    protected UrlMappingRepository repository;

    @BeforeEach
    void clearMappings() {
        repository.deleteAll();
        repository.flush();
    }
}

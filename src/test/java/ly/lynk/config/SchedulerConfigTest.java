package ly.lynk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchedulerConfigTest {

    @Test
    @DisplayName("creates JdbcTemplateLockProvider from data source")
    void createsLockProvider() {
        DataSource dataSource = mock(DataSource.class);
        SchedulerConfig config = new SchedulerConfig();

        LockProvider lockProvider = config.lockProvider(dataSource);

        assertThat(lockProvider).isNotNull().isInstanceOf(JdbcTemplateLockProvider.class);
    }
}

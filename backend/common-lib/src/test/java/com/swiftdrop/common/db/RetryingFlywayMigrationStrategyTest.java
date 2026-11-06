package com.swiftdrop.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TST-013")
class RetryingFlywayMigrationStrategyTest {

    private final List<Long> pauses = new ArrayList<>();

    private RetryingFlywayMigrationStrategy strategy(Duration max) {
        return new RetryingFlywayMigrationStrategy(max, pauses::add);
    }

    private static FlywayException connectionFailure() {
        return new FlywayException("Unable to obtain connection", new SQLTransientConnectionException("down"));
    }

    @Test
    @DisplayName("TST-013 NFR-REL-003: migration is retried with back-off until the database is up")
    void retriesUntilUp() {
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenThrow(connectionFailure(), connectionFailure()).thenReturn(null);
        strategy(Duration.ofSeconds(90)).migrate(flyway);
        verify(flyway, times(3)).migrate();
        assertThat(pauses).containsExactly(1000L, 2000L);
    }

    @Test
    @DisplayName("TST-013 NFR-REL-003: gives up after the maximum wait")
    void givesUp() {
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenThrow(connectionFailure());
        assertThatThrownBy(() -> strategy(Duration.ofSeconds(5)).migrate(flyway)).isInstanceOf(FlywayException.class);
        assertThat(pauses.stream().mapToLong(Long::longValue).sum()).isGreaterThanOrEqualTo(5000);
    }

    @Test
    @DisplayName("TST-013 a failing migration (not a connection problem) fails immediately")
    void migrationErrorNotRetried() {
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenThrow(new FlywayException("Validate failed: checksum mismatch"));
        assertThatThrownBy(() -> strategy(Duration.ofSeconds(90)).migrate(flyway)).isInstanceOf(FlywayException.class);
        verify(flyway, times(1)).migrate();
        assertThat(pauses).isEmpty();
    }
}

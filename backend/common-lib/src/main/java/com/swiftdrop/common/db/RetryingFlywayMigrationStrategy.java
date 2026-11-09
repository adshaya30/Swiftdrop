package com.swiftdrop.common.db;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

/**
 * Retries the Flyway migration with back-off while the database is not reachable (NFR-REL-003), so services
 * start in any order. Only connection problems are retried; a failing migration fails immediately.
 */
public class RetryingFlywayMigrationStrategy implements FlywayMigrationStrategy {

    public static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(120);

    private static final Logger log = LoggerFactory.getLogger(RetryingFlywayMigrationStrategy.class);

    /** Pause and clock, replaceable in tests. */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Duration maxWait;
    private final Sleeper sleeper;

    public RetryingFlywayMigrationStrategy() {
        this(DEFAULT_MAX_WAIT, Thread::sleep);
    }

    RetryingFlywayMigrationStrategy(Duration maxWait, Sleeper sleeper) {
        this.maxWait = maxWait;
        this.sleeper = sleeper;
    }

    @Override
    public void migrate(Flyway flyway) {
        long waitedMillis = 0;
        long delay = 1000;
        while (true) {
            try {
                flyway.migrate();
                return;
            } catch (FlywayException e) {
                if (!isConnectionProblem(e) || waitedMillis >= maxWait.toMillis()) {
                    throw e;
                }
                log.warn("Database not reachable yet, retrying migration in {} ms", delay);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                waitedMillis += delay;
                delay = Math.min(delay * 2, 10_000);
            }
        }
    }

    static boolean isConnectionProblem(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLTransientConnectionException || t instanceof SQLNonTransientConnectionException
                    || t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof SocketTimeoutException) {
                return true;
            }
            if (t instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("57P0"))) {
                return true;
            }
        }
        return false;
    }
}

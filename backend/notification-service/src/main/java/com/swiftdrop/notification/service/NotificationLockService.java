package com.swiftdrop.notification.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Cross-instance, session-level PostgreSQL lock; network and SMTP work runs without an open transaction. */
@Component
public class NotificationLockService {
    private final DataSource dataSource;

    public NotificationLockService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Lock acquire(UUID notificationId) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            long key = notificationId.getMostSignificantBits() ^ notificationId.getLeastSignificantBits();
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
                statement.setLong(1, key);
                statement.execute();
            }
            return new Lock(connection, key);
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new NotificationProcessingException();
        }
    }

    public static final class Lock implements AutoCloseable {
        private final Connection connection;
        private final long key;

        private Lock(Connection connection, long key) {
            this.connection = connection;
            this.key = key;
        }

        @Override
        public void close() {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                statement.setLong(1, key);
                statement.execute();
            } catch (SQLException ignored) {
                // Closing the PostgreSQL session releases session locks even if explicit unlock fails.
            } finally {
                closeQuietly(connection);
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try { connection.close(); } catch (SQLException ignored) { }
    }
}

package com.swiftdrop.common.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;

/** Behaves like the processed_events insert: 1 row the first time an id is seen, 0 rows afterwards. */
class SeenEventsJdbcTemplate extends JdbcTemplate {

    final Set<UUID> seen = ConcurrentHashMap.newKeySet();
    final List<String> statements = new ArrayList<>();

    @Override
    public int update(String sql, Object... args) {
        statements.add(sql);
        return seen.add((UUID) args[0]) ? 1 : 0;
    }
}

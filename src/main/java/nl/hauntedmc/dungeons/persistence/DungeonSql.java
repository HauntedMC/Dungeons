package nl.hauntedmc.dungeons.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/** Prepared JDBC helpers. SQL text is always application-owned. */
public final class DungeonSql {
    private DungeonSql() {}

    public static int update(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            return statement.executeUpdate();
        }
    }

    public static List<Map<String, Object>> rows(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (var result = statement.executeQuery()) {
                var rows = new ArrayList<Map<String, Object>>();
                var metadata = result.getMetaData();
                while (result.next()) {
                    var row = new LinkedHashMap<String, Object>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        Object value;
                        if (metadata.getColumnType(index) == Types.TIMESTAMP) {
                            var timestamp = result.getTimestamp(index, utcCalendar());
                            value = timestamp == null ? null : timestamp.toInstant();
                        } else value = result.getObject(index);
                        row.put(metadata.getColumnLabel(index), value);
                    }
                    rows.add(Collections.unmodifiableMap(row));
                }
                return List.copyOf(rows);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof UUID id) value = id.toString();
            if (value instanceof Instant instant) statement.setTimestamp(index + 1, Timestamp.from(instant), utcCalendar());
            else statement.setObject(index + 1, value);
        }
    }
    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

}

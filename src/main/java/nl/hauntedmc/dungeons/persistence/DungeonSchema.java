package nl.hauntedmc.dungeons.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;

/** Checksummed, additive MySQL schema initialization on a dedicated worker connection. */
public final class DungeonSchema {
    private static final String HISTORY_SQL = "CREATE TABLE IF NOT EXISTS dungeon_schema_history ("
            + "version INT NOT NULL PRIMARY KEY, checksum CHAR(64) NOT NULL,"
            + " completed BOOLEAN NOT NULL, applied_at DATETIME(3) NOT NULL) ENGINE=InnoDB";
    private static final java.util.List<String> RESOURCES = java.util.List.of(
            "/database/V001__dungeons.sql", "/database/V002__key_reservation_ownership.sql");
    private record Migration(int version, String sql, String checksum) {}
    private record Applied(String checksum, boolean complete) {}
    private record Index(boolean unique, java.util.List<String> columns) {}
    private record ForeignKey(java.util.List<String> columns, String table, java.util.List<String> referenced,
            String onDelete, String onUpdate) {}

    private DungeonSchema() {}

    public static void initialize(Connection connection, boolean migrate) throws SQLException, IOException {
        if (!connection.getAutoCommit()) throw new SQLException("Schema initialization requires a dedicated auto-commit connection");
        var migrations = new java.util.ArrayList<Migration>();
        for (int index = 0; index < RESOURCES.size(); index++) {
            String resource = RESOURCES.get(index);
            try (var stream = DungeonSchema.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing migration " + resource);
                byte[] bytes = stream.readAllBytes();
                migrations.add(new Migration(index + 1, new String(bytes, StandardCharsets.UTF_8), checksum(bytes)));
            }
        }
        String lock;
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT CONCAT('dungeon:',LEFT(SHA2(DATABASE(),256),48))")) {
            if (!result.next() || (lock = result.getString(1)) == null) {
                throw new SQLException("Select the shared DataRegistry database before initialization");
            }
        }
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?,15)")) {
            statement.setString(1, lock);
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) throw new SQLException("Schema migration lock unavailable");
            }
        }
        try {
            // Verify the canonical ID column without querying or modifying player identities.
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT id FROM player_entity WHERE 1=0")) {
                if (result.getMetaData().getColumnType(1) != java.sql.Types.BIGINT || !result.getMetaData().isSigned(1)) {
                    throw new SQLException("DataRegistry player_entity.id must be BIGINT");
                }
            }
            if (migrate) {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate(HISTORY_SQL);
                }
            }
            validateTables(connection, HISTORY_SQL);
            var applied = new java.util.TreeMap<Integer, Applied>();
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT version,checksum,completed FROM dungeon_schema_history ORDER BY version")) {
                while (result.next()) {
                    int version = result.getInt(1);
                    if (version < 1 || version > migrations.size()) throw new SQLException("Unsupported Dungeons schema version " + version);
                    if (version != applied.size() + 1) throw new SQLException("Dungeons migration history has a version gap");
                    if (!migrations.get(version - 1).checksum().equals(result.getString(2)))
                        throw new SQLException("Dungeons migration checksum mismatch for version " + version);
                    applied.put(version, new Applied(result.getString(2), result.getBoolean(3)));
                }
            }
            for (var migration : migrations) {
                Applied previous = applied.get(migration.version());
                if (previous == null || !previous.complete()) {
                    if (!migrate) throw new SQLException("Dungeons schema requires migration " + migration.version());
                    try (var statement = connection.prepareStatement("INSERT INTO dungeon_schema_history"
                            + "(version,checksum,completed,applied_at) VALUES(?,?,FALSE,UTC_TIMESTAMP(3))"
                            + " ON DUPLICATE KEY UPDATE completed=FALSE")) {
                        statement.setInt(1, migration.version());
                        statement.setString(2, migration.checksum());
                        statement.executeUpdate();
                    }
                    // Each bundled statement is independently restart-safe; MySQL DDL commits implicitly.
                    for (String fragment : migration.sql().split(";")) {
                        if (fragment.isBlank()) continue;
                        try (var statement = connection.createStatement()) {
                            statement.executeUpdate(fragment);
                        }
                    }
                    validateTables(connection, migration.sql());
                    try (var statement = connection.prepareStatement("UPDATE dungeon_schema_history"
                            + " SET completed=TRUE,applied_at=UTC_TIMESTAMP(3) WHERE version=?")) {
                        statement.setInt(1, migration.version());
                        statement.executeUpdate();
                    }
                } else validateTables(connection, migration.sql());
            }
        } finally {
            try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, lock);
                statement.execute();
            }
        }
    }

    private static void validateTables(Connection connection, String sql) throws SQLException {
        var matcher = java.util.regex.Pattern.compile(
                "CREATE TABLE IF NOT EXISTS (dungeon_[a-z_]+) \\((.*?)\\) ENGINE=InnoDB",
                java.util.regex.Pattern.DOTALL).matcher(sql);
        while (matcher.find()) {
            requireTransactionalTable(connection, matcher.group(1));
            var fields = new java.util.LinkedHashMap<String, String>();
            var required = new java.util.HashSet<String>();
            // Delimiters inside type widths and key column lists are not column separators.
            int depth = 0;
            int start = 0;
            String body = matcher.group(2) + ",";
            for (int index = 0; index < body.length(); index++) {
                char current = body.charAt(index);
                if (current == '(') depth++;
                if (current == ')') depth--;
                if (current != ',' || depth != 0) continue;
                String definition = body.substring(start, index).strip();
                start = index + 1;
                String[] words = definition.split("\\s+", 3);
                if (words.length < 2 || !words[0].matches("[a-z_]+")) continue;
                fields.put(words[0], words[1]);
                if (definition.contains("NOT NULL") || definition.contains("PRIMARY KEY")) required.add(words[0]);
            }
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT " + String.join(",", fields.keySet())
                            + " FROM " + matcher.group(1) + " WHERE 1=0")) {
                var metadata = result.getMetaData();
                int index = 0;
                for (var field : fields.entrySet()) {
                    int actual = metadata.getColumnType(++index);
                    String expected = field.getValue().replaceAll("\\(.*", "");
                    boolean valid = switch (expected) {
                        case "CHAR" -> actual == java.sql.Types.CHAR;
                        case "VARCHAR" -> actual == java.sql.Types.VARCHAR;
                        case "INT" -> actual == java.sql.Types.INTEGER;
                        case "BIGINT" -> actual == java.sql.Types.BIGINT && metadata.isSigned(index);
                        case "BOOLEAN" -> actual == java.sql.Types.BIT || actual == java.sql.Types.BOOLEAN || actual == java.sql.Types.TINYINT;
                        case "DATETIME" -> actual == java.sql.Types.TIMESTAMP;
                        case "DECIMAL" -> actual == java.sql.Types.DECIMAL;
                        case "TEXT", "MEDIUMTEXT" -> actual == java.sql.Types.LONGVARCHAR;
                        case "MEDIUMBLOB" -> actual == java.sql.Types.LONGVARBINARY;
                        default -> false;
                    };
                    var dimensions = java.util.regex.Pattern.compile("[A-Z]+\\((\\d+)(?:,(\\d+))?\\)").matcher(field.getValue());
                    if (dimensions.matches() && java.util.Set.of("CHAR", "VARCHAR", "DECIMAL").contains(expected)) {
                        valid &= metadata.getPrecision(index) == Integer.parseInt(dimensions.group(1));
                        if (expected.equals("DECIMAL")) valid &= metadata.getScale(index) == Integer.parseInt(dimensions.group(2));
                    }
                    valid &= required.contains(field.getKey())
                            ? metadata.isNullable(index) == java.sql.ResultSetMetaData.columnNoNulls
                            : metadata.isNullable(index) == java.sql.ResultSetMetaData.columnNullable;
                    if (!valid) throw new SQLException("Incompatible column " + matcher.group(1) + "." + field.getKey());
                }
            }
            validateConstraints(connection, matcher.group(1), matcher.group(2));
        }
    }

    private static void validateConstraints(Connection connection, String table, String body) throws SQLException {
        var expectedIndexes = new java.util.LinkedHashMap<String, Index>();
        var inlinePrimary = java.util.regex.Pattern.compile("\\b([a-z_]+) [A-Z]+(?:\\([0-9,]+\\))? NOT NULL PRIMARY KEY").matcher(body);
        while (inlinePrimary.find()) expectedIndexes.put("PRIMARY", new Index(true, java.util.List.of(inlinePrimary.group(1))));
        var indexes = java.util.regex.Pattern.compile("(PRIMARY KEY|UNIQUE KEY ([a-z_]+)|KEY ([a-z_]+))\\s*\\(([a-z_,]+)\\)").matcher(body);
        while (indexes.find()) {
            String name = indexes.group(1).equals("PRIMARY KEY") ? "PRIMARY"
                    : indexes.group(2) != null ? indexes.group(2) : indexes.group(3);
            expectedIndexes.put(name, new Index(indexes.group(3) == null, columns(indexes.group(4))));
        }
        var actualColumns = new java.util.HashMap<String, java.util.List<String>>();
        var actualUnique = new java.util.HashMap<String, Boolean>();
        for (var row : DungeonSql.rows(connection, "SELECT INDEX_NAME AS index_name,NON_UNIQUE AS non_unique,"
                + "COLUMN_NAME AS column_name,SUB_PART AS sub_part,INDEX_TYPE AS index_type,IS_VISIBLE AS is_visible"
                + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY INDEX_NAME,SEQ_IN_INDEX", table)) {
            String name = (String) row.get("index_name");
            if (!expectedIndexes.containsKey(name)) continue;
            if (row.get("sub_part") != null || !"BTREE".equals(row.get("index_type")) || !"YES".equals(row.get("is_visible")))
                throw new SQLException("Incompatible index " + table + "." + name);
            actualColumns.computeIfAbsent(name, ignored -> new java.util.ArrayList<>()).add((String) row.get("column_name"));
            actualUnique.put(name, ((Number) row.get("non_unique")).intValue() == 0);
        }
        for (var expected : expectedIndexes.entrySet()) {
            if (!expected.getValue().columns().equals(actualColumns.get(expected.getKey()))
                    || !Boolean.valueOf(expected.getValue().unique()).equals(actualUnique.get(expected.getKey())))
                throw new SQLException("Incompatible index " + table + "." + expected.getKey());
        }

        var expectedForeignKeys = new java.util.HashSet<ForeignKey>();
        var foreignKeys = java.util.regex.Pattern.compile("FOREIGN KEY\\(([a-z_,]+)\\) REFERENCES ([a-z_]+)\\(([a-z_,]+)\\)"
                + "(?: ON DELETE (CASCADE|SET NULL|RESTRICT|NO ACTION))?(?: ON UPDATE (CASCADE|SET NULL|RESTRICT|NO ACTION))?").matcher(body);
        while (foreignKeys.find()) expectedForeignKeys.add(new ForeignKey(columns(foreignKeys.group(1)), foreignKeys.group(2),
                columns(foreignKeys.group(3)), rule(foreignKeys.group(4)), rule(foreignKeys.group(5))));
        var actualForeignKeys = new java.util.HashMap<String, ForeignKey>();
        for (var row : DungeonSql.rows(connection, "SELECT k.CONSTRAINT_NAME AS constraint_name,k.COLUMN_NAME AS column_name,"
                + "k.REFERENCED_TABLE_NAME AS referenced_table,k.REFERENCED_COLUMN_NAME AS referenced_column,"
                + "r.DELETE_RULE AS delete_rule,r.UPDATE_RULE AS update_rule"
                + " FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r"
                + " ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME"
                + " WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME=? AND k.REFERENCED_TABLE_SCHEMA=DATABASE()"
                + " ORDER BY k.CONSTRAINT_NAME,k.ORDINAL_POSITION", table)) {
            var key = actualForeignKeys.computeIfAbsent((String) row.get("constraint_name"), ignored -> new ForeignKey(
                    new java.util.ArrayList<>(), (String) row.get("referenced_table"), new java.util.ArrayList<>(),
                    rule((String) row.get("delete_rule")), rule((String) row.get("update_rule"))));
            key.columns().add((String) row.get("column_name"));
            key.referenced().add((String) row.get("referenced_column"));
        }
        if (!new java.util.HashSet<>(actualForeignKeys.values()).containsAll(expectedForeignKeys))
            throw new SQLException("Incompatible foreign keys in " + table);
    }

    private static java.util.List<String> columns(String text) { return java.util.List.of(text.split(",")); }
    private static String rule(String value) { return value == null || value.equals("NO ACTION") ? "RESTRICT" : value; }

    private static void requireTransactionalTable(Connection connection, String table) throws SQLException {
        var rows = DungeonSql.rows(connection, "SELECT ENGINE AS storage_engine FROM information_schema.TABLES"
                + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", table);
        if (rows.size() != 1 || !"InnoDB".equalsIgnoreCase((String) rows.getFirst().get("storage_engine")))
            throw new SQLException("Dungeons requires an InnoDB table: " + table);
    }

    public static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

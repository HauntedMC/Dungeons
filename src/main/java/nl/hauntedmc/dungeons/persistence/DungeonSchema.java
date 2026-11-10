package nl.hauntedmc.dungeons.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.Table;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sql.DataSource;

/** DataProvider owns ORM policy. DataRegistry retains ownership of canonical player identities. */
public final class DungeonSchema {
    private DungeonSchema() {}

    public static ORMContext open(DataProviderAPI api, DataSource source, LoggerAdapter logger) throws SQLException {
        // Release this borrow before ORM initialization; even a one-connection pool must work.
        try (var connection = source.getConnection()) {
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT id,uuid FROM player_entity WHERE 1=0")) {
                var metadata = result.getMetaData();
                if (metadata.getColumnType(1) != Types.BIGINT || !metadata.isSigned(1)
                        || metadata.isNullable(1) != ResultSetMetaData.columnNoNulls)
                    throw new SQLException("DataRegistry player_entity.id must be a non-null signed BIGINT");
                int uuidType = metadata.getColumnType(2);
                if ((uuidType != Types.CHAR && uuidType != Types.VARCHAR)
                        || metadata.getPrecision(2) < 36 || metadata.isNullable(2) != ResultSetMetaData.columnNoNulls)
                    throw new SQLException("DataRegistry player_entity.uuid must be a non-null UUID string");
            }
        }
        var context = Objects.requireNonNull(
                api.createConfiguredOrmContext(source, logger, DungeonEntities.types()),
                "DataProvider returned no ORM context");
        try {
            // Hibernate update may log DDL errors and still return a factory. Validate invariants
            // used by JDBC transactions before acquiring a lease or accepting database work.
            context.runInTransaction(session -> session.doReturningWork(connection -> {
                verify(connection);
                return null;
            }));
            return context;
        } catch (RuntimeException | Error failure) {
            try { context.close(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void verify(Connection connection) throws SQLException {
        for (Class<?> entity : DungeonEntities.types()) {
            if (entity == DungeonEntities.PlayerReference.class) continue;
            Table table = entity.getAnnotation(Table.class);
            var engine = DungeonSql.rows(connection, "SELECT ENGINE AS engine FROM information_schema.TABLES"
                    + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", table.name());
            if (engine.size() != 1 || !"InnoDB".equalsIgnoreCase((String) engine.getFirst().get("engine")))
                throw new SQLException("Dungeons requires an InnoDB table: " + table.name());
            var columns = new ArrayList<String>();
            var primary = new HashSet<String>();
            var definitions = new ArrayList<Column>();
            for (var field : entity.getDeclaredFields()) {
                var column = field.getAnnotation(Column.class);
                if (column == null) continue;
                columns.add(column.name());
                definitions.add(column);
                if (field.isAnnotationPresent(Id.class)) primary.add(column.name());
            }
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT " + String.join(",", columns)
                            + " FROM " + table.name() + " WHERE 1=0")) {
                var metadata = result.getMetaData();
                if (metadata.getColumnCount() != columns.size()) throw new SQLException("Incomplete " + table.name());
                for (int index = 0; index < definitions.size(); index++)
                    verifyColumn(metadata, index + 1, definitions.get(index), table.name());
            }
            var indexes = new LinkedHashMap<String, List<String>>();
            var unique = new HashSet<String>();
            for (var row : DungeonSql.rows(connection, "SELECT INDEX_NAME AS name,COLUMN_NAME AS col,NON_UNIQUE AS non_unique"
                    + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?"
                    + " AND SUB_PART IS NULL AND IS_VISIBLE='YES' ORDER BY INDEX_NAME,SEQ_IN_INDEX", table.name())) {
                String name = (String) row.get("name");
                indexes.computeIfAbsent(name, ignored -> new ArrayList<>()).add((String) row.get("col"));
                if (((Number) row.get("non_unique")).intValue() == 0) unique.add(name);
            }
            if (!primary.equals(new HashSet<>(indexes.getOrDefault("PRIMARY", List.of()))))
                throw new SQLException("Incompatible primary key: " + table.name());
            for (var index : table.indexes()) {
                var expected = Arrays.stream(index.columnList().split(",")).map(String::trim).toList();
                if (!expected.equals(indexes.get(index.name())) || index.unique() && !unique.contains(index.name()))
                    throw new SQLException("Missing or incompatible index: " + table.name() + "." + index.name());
            }
            for (var key : table.uniqueConstraints()) {
                if (!unique.contains(key.name()) || !List.of(key.columnNames()).equals(indexes.get(key.name())))
                    throw new SQLException("Missing or incompatible unique constraint: " + table.name() + "." + key.name());
            }
            verifyForeignKeys(connection, entity, table.name());
        }
    }

    private static void verifyColumn(ResultSetMetaData metadata, int index, Column column,
            String table) throws SQLException {
        String definition = column.columnDefinition();
        String type = definition.replaceAll("\\(.*", "");
        int actual = metadata.getColumnType(index);
        boolean valid = switch (type) {
            case "CHAR" -> actual == Types.CHAR;
            case "VARCHAR" -> actual == Types.VARCHAR;
            case "BIGINT" -> actual == Types.BIGINT && metadata.isSigned(index);
            case "INT" -> actual == Types.INTEGER && metadata.isSigned(index);
            case "BOOLEAN" -> actual == Types.BOOLEAN || actual == Types.BIT || actual == Types.TINYINT;
            case "DATETIME" -> actual == Types.TIMESTAMP;
            case "DECIMAL" -> actual == Types.DECIMAL;
            case "TEXT", "MEDIUMTEXT" -> actual == Types.LONGVARCHAR;
            case "MEDIUMBLOB" -> actual == Types.LONGVARBINARY;
            default -> false;
        };
        var dimensions = Pattern.compile("[A-Z]+\\((\\d+)(?:,(\\d+))?\\)").matcher(definition);
        if (dimensions.matches() && Set.of("CHAR", "VARCHAR", "DECIMAL").contains(type)) {
            valid &= metadata.getPrecision(index) == Integer.parseInt(dimensions.group(1));
            if (type.equals("DECIMAL")) valid &= metadata.getScale(index) == Integer.parseInt(dimensions.group(2));
        }
        valid &= metadata.isNullable(index) == (column.nullable()
                ? ResultSetMetaData.columnNullable : ResultSetMetaData.columnNoNulls);
        if (!valid) throw new SQLException("Incompatible column: " + table + "." + column.name());
    }

    private record ForeignKey(List<String> columns, String table, List<String> referenced, String onDelete, String onUpdate) {}

    private static void verifyForeignKeys(Connection connection, Class<?> entity, String table) throws SQLException {
        var actual = new LinkedHashMap<String, ForeignKey>();
        for (var row : DungeonSql.rows(connection, "SELECT k.CONSTRAINT_NAME AS name,k.COLUMN_NAME AS col,"
                + "k.REFERENCED_TABLE_NAME AS target,k.REFERENCED_COLUMN_NAME AS ref,r.DELETE_RULE AS delete_rule,r.UPDATE_RULE AS update_rule"
                + " FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r"
                + " ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME"
                + " WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME=? AND k.REFERENCED_TABLE_SCHEMA=DATABASE()"
                + " ORDER BY k.CONSTRAINT_NAME,k.ORDINAL_POSITION", table)) {
            var key = actual.computeIfAbsent((String) row.get("name"), ignored -> new ForeignKey(
                    new ArrayList<>(), (String) row.get("target"), new ArrayList<>(),
                    row.get("delete_rule").equals("NO ACTION") ? "RESTRICT" : (String) row.get("delete_rule"),
                    row.get("update_rule").equals("NO ACTION") ? "RESTRICT" : (String) row.get("update_rule")));
            key.columns().add((String) row.get("col"));
            key.referenced().add((String) row.get("ref"));
        }
        for (var field : entity.getDeclaredFields()) {
            var joins = field.getAnnotation(JoinColumns.class);
            if (joins == null) continue;
            var target = field.getType().getAnnotation(Table.class).name();
            var columns = Arrays.stream(joins.value()).map(JoinColumn::name).toList();
            var references = Arrays.stream(joins.value()).map(JoinColumn::referencedColumnName).toList();
            var onDelete = field.getAnnotation(OnDelete.class);
            String rule = onDelete != null && onDelete.action() == OnDeleteAction.CASCADE ? "CASCADE" : "RESTRICT";
            if (!actual.containsValue(new ForeignKey(columns, target, references, rule, "RESTRICT")))
                throw new SQLException("Missing or incompatible foreign key: " + table + " -> " + target);
        }
    }

    public static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

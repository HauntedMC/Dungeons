package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import javax.sql.DataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import org.junit.jupiter.api.Test;

class DungeonSchemaReloadTest {
    @Test
    void reloadUsesCurrentEntityClassesAndRestoresTheInheritedLoader() throws Exception {
        var thread = Thread.currentThread();
        var original = thread.getContextClassLoader();
        try (var stale = entityLoader();
                var current = entityLoader()) {
            var schema = current.loadClass(DungeonSchema.class.getName());
            var api = mock(DataProviderAPI.class);
            var context = mock(ORMContext.class);
            when(api.createConfiguredOrmContext(any(), any(), any(Class[].class)))
                    .thenAnswer(
                            call -> {
                                try (var orm =
                                        new nl.hauntedmc.dataprovider.core.orm.ORMContext(
                                                "reload-test",
                                                call.getArgument(0),
                                                call.getArgument(1),
                                                "none",
                                                (Class<?>[]) call.getRawArguments()[2])) {
                                    orm.runInTransaction(
                                            session -> {
                                                assertSame(
                                                        current,
                                                        session.getSessionFactory()
                                                                .getMetamodel()
                                                                .entity(
                                                                        current.loadClass(
                                                                                DungeonEntities
                                                                                        .PlayerReference
                                                                                        .class
                                                                                        .getName()))
                                                                .getJavaType()
                                                                .getClassLoader());
                                                return null;
                                            });
                                }
                                return context;
                            });
            thread.setContextClassLoader(stale);
            assertSame(
                    context,
                    schema.getMethod(
                                    "open",
                                    DataProviderAPI.class,
                                    DataSource.class,
                                    LoggerAdapter.class)
                            .invoke(
                                    null,
                                    api,
                                    source(),
                                    (LoggerAdapter) (level, message, error) -> {}));
            assertSame(stale, thread.getContextClassLoader());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static URLClassLoader entityLoader() {
        return new URLClassLoader(
                new java.net.URL[] {
                    DungeonSchema.class.getProtectionDomain().getCodeSource().getLocation()
                },
                DungeonSchemaReloadTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (!name.startsWith("nl.hauntedmc.dungeons.persistence."))
                    return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    var type = findLoadedClass(name);
                    if (type == null) type = findClass(name);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
    }

    private static DataSource source() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var result = mock(ResultSet.class);
        var metadata = mock(ResultSetMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        var database = mock(java.sql.DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(database);
        when(database.getDatabaseProductName()).thenReturn("MySQL");
        when(database.getDatabaseMajorVersion()).thenReturn(8);
        when(database.getDatabaseMinorVersion()).thenReturn(4);
        when(database.getDatabaseProductVersion()).thenReturn("8.4.0");
        when(database.getSQLKeywords()).thenReturn("");
        when(database.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(result);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnType(1)).thenReturn(Types.BIGINT);
        when(metadata.isSigned(1)).thenReturn(true);
        when(metadata.getColumnType(2)).thenReturn(Types.VARCHAR);
        when(metadata.getPrecision(2)).thenReturn(36);
        when(metadata.isNullable(anyInt())).thenReturn(ResultSetMetaData.columnNoNulls);
        return source;
    }
}

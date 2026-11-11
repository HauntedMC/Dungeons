package nl.hauntedmc.dungeons.persistence;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.DriverManager;
import javax.sql.DataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.core.orm.ORMContext;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import org.testcontainers.mysql.MySQLContainer;

/** Uses DataProvider's real ORM implementation; mode is configured at the provider boundary. */
final class OrmTestSupport {
    static void configure(DataProviderAPI api) {
        configure(api, "update");
    }

    static void configure(DataProviderAPI api, String configuredMode) {
        when(api.createConfiguredOrmContext(
                        any(DataSource.class), any(LoggerAdapter.class), any(Class[].class)))
                .thenAnswer(
                        call ->
                                new ORMContext(
                                        "Dungeons-test",
                                        call.getArgument(0),
                                        call.getArgument(1),
                                        configuredMode,
                                        (Class<?>[]) call.getRawArguments()[2]));
    }

    static void initialize(MySQLContainer mysql, String mode) throws Exception {
        var api = mock(DataProviderAPI.class);
        configure(api, mode);
        var source = mock(DataSource.class);
        when(source.getConnection())
                .thenAnswer(
                        call ->
                                DriverManager.getConnection(
                                        mysql.getJdbcUrl(),
                                        mysql.getUsername(),
                                        mysql.getPassword()));
        try (var _ = DungeonSchema.open(api, source, (level, message, error) -> {})) {}
        verify(api, never()).createOrmContext(any(), any(), anyString(), any(Class[].class));
    }
}

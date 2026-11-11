package nl.hauntedmc.dungeons.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;

/** Owns real disposable backends for all native Paper boots, including cold restart. */
public final class AcceptanceDatabase {
    private AcceptanceDatabase() {}

    public static void main(String[] args) throws Exception {
        try (var mysql =
                        new MySQLContainer("mysql:8.4")
                                .withDatabaseName("minecraft")
                                .withUsername("dungeons")
                                .withPassword("acceptance-mysql");
                var redis =
                        new GenericContainer<>("redis:7.4-alpine")
                                .withExposedPorts(6379)
                                .withCommand("redis-server", "--requirepass", "acceptance-redis")) {
            mysql.start();
            redis.start();
            var process =
                    new ProcessBuilder("bash", Path.of(args[0]).toAbsolutePath().toString())
                            .inheritIO();
            var env = process.environment();
            env.put("DUNGEONS_ACCEPTANCE_DB_HOST", mysql.getHost());
            env.put("DUNGEONS_ACCEPTANCE_DB_PORT", mysql.getMappedPort(3306).toString());
            env.put("DUNGEONS_ACCEPTANCE_REDIS_HOST", redis.getHost());
            env.put("DUNGEONS_ACCEPTANCE_REDIS_PORT", redis.getMappedPort(6379).toString());
            env.put("DUNGEONS_ACCEPTANCE_WORK_DIRECTORY", args[1]);
            var child = process.start();
            boolean databaseStopped = false;
            while (!child.waitFor(1, TimeUnit.SECONDS)) {
                if (!databaseStopped && Files.exists(Path.of(args[1], "stop-database"))) {
                    mysql.stop();
                    databaseStopped = true;
                }
            }
            int result = child.exitValue();
            if (result != 0)
                throw new IllegalStateException("Native database acceptance failed: " + result);
        }
    }
}

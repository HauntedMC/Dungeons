package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Only the server/plugin boundary is mocked; tests construct the real system under test. */
public final class PaperBoundary implements AutoCloseable {
    public final DungeonsPlugin plugin = mock(DungeonsPlugin.class);
    public final YamlConfiguration config = new YamlConfiguration();
    public final DungeonsRuntime runtime = new DungeonsRuntime(plugin);
    public final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    private final MockedStatic<JavaPlugin> javaPlugin = mockStatic(JavaPlugin.class);

    public PaperBoundary() {
        when(plugin.getName()).thenReturn("Dungeons");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("CoreContractTests"));
        runtime.setConfig(config);
        RuntimeContext.initialize(runtime);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        // WorldCreator still consults this Paper implementation boundary internally.
        var unsafe = mock(UnsafeValues.class);
        when(unsafe.getMainLevelName()).thenReturn("world");
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
        var server = mock(Server.class);
        when(server.getLevelDirectory()).thenAnswer(call -> Bukkit.getWorldContainer().toPath().resolve("world"));
        bukkit.when(Bukkit::getServer).thenReturn(server);
        javaPlugin.when(() -> JavaPlugin.getPlugin(DungeonsPlugin.class)).thenReturn(plugin);
    }

    public StaticDungeon dungeon(Path folder, YamlConfiguration settings) throws Exception {
        Files.createDirectories(folder);
        runtime.setDefaultDungeonConfig(new YamlConfiguration());
        bukkit.when(Bukkit::getWorldContainer).thenReturn(folder.getParent().toFile());
        return new StaticDungeon(runtime, folder.toFile(), settings);
    }

    @Override
    public void close() {
        RuntimeContext.clear();
        javaPlugin.close();
        bukkit.close();
    }
}

package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.*;

import java.util.List;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

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
        javaPlugin.when(() -> JavaPlugin.getPlugin(DungeonsPlugin.class)).thenReturn(plugin);
    }

    @Override public void close() {
        RuntimeContext.clear();
        javaPlugin.close();
        bukkit.close();
    }
}

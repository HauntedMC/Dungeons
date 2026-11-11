package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.persistence.DatabaseLifecycle;
import nl.hauntedmc.dungeons.persistence.DungeonDatabase;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.PlayerDataCheckpoint;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Damageable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/** Only the server/plugin boundary is mocked; tests construct the real system under test. */
public final class PaperBoundary implements AutoCloseable {
    public final DungeonsPlugin plugin = mock(DungeonsPlugin.class);
    public final YamlConfiguration config = new YamlConfiguration();
    public final TestStateStore state = new TestStateStore();
    public final DungeonsRuntime runtime = new DungeonsRuntime(plugin);
    public final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    public final MockedStatic<EntityUtils> entities =
            mockStatic(EntityUtils.class, Mockito.CALLS_REAL_METHODS);
    public final MockedStatic<PlayerDataCheckpoint> checkpoints =
            mockStatic(PlayerDataCheckpoint.class);
    private final MockedStatic<ItemStack> items =
            mockStatic(ItemStack.class, Mockito.CALLS_REAL_METHODS);
    private final MockedStatic<JavaPlugin> javaPlugin = mockStatic(JavaPlugin.class);

    // Paper's WorldCreator still calls the deprecated implementation boundary internally.
    @SuppressWarnings("deprecation")
    public PaperBoundary() {
        runtime.setGuiService(new nl.hauntedmc.dungeons.gui.framework.GuiService(plugin));
        NativeItemBytes.decode(items);
        entities.when(() -> EntityUtils.getMaxHealth(Mockito.any(Damageable.class)))
                .thenReturn(20.0);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        when(plugin.getName()).thenReturn("Dungeons");
        when(plugin.namespace()).thenReturn("dungeons");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("CoreContractTests"));
        runtime.setConfig(config);
        runtime.setStateStore(state);
        var database = mock(DatabaseLifecycle.class);
        when(database.ready()).thenReturn(true);
        when(database.state()).thenReturn(DungeonDatabase.State.READY);
        when(database.problem()).thenReturn("");
        runtime.setDatabase(database);
        runtime.setActiveInstances(new ActiveInstanceRegistry(plugin));
        RuntimeContext.initialize(runtime);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        // WorldCreator still consults this Paper implementation boundary internally.
        var unsafe = mock(UnsafeValues.class);
        when(unsafe.getMainLevelName()).thenReturn("world");
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
        var server = mock(Server.class);
        when(server.getLevelDirectory())
                .thenAnswer(call -> Bukkit.getWorldContainer().toPath().resolve("world"));
        bukkit.when(Bukkit::getServer).thenReturn(server);
        javaPlugin.when(() -> JavaPlugin.getPlugin(DungeonsPlugin.class)).thenReturn(plugin);
    }

    public StaticDungeon dungeon(Path folder, YamlConfiguration settings) throws Exception {
        Files.createDirectories(folder);
        runtime.setDefaultDungeonConfig(new YamlConfiguration());
        bukkit.when(Bukkit::getWorldContainer).thenReturn(folder.getParent().toFile());
        var dungeon = new StaticDungeon(runtime, folder.toFile(), settings);
        initializeRepository(dungeon);
        return dungeon;
    }

    public static void initializeRepository(
            nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition dungeon) throws Exception {
        new nl.hauntedmc.dungeons.repository.RepoLocal(
                        dungeon.getDungeonId(),
                        null,
                        nl.hauntedmc.dungeons.repository.RepoVersion.DRAFT,
                        "0".repeat(64))
                .write(dungeon.getFolder().toPath());
        dungeon.refreshRepositoryState();
    }

    @Override
    public void close() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        ConfigurationFile.flushPendingSaves();
        RuntimeContext.clear();
        entities.close();
        checkpoints.close();
        items.close();
        NativeItemBytes.clear();
        javaPlugin.close();
        bukkit.close();
    }
}

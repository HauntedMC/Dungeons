package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/** Real run/session graph with server state at the Paper boundary. Does not bypass run hooks. */
public final class RunFixture implements AutoCloseable {
    public final PaperBoundary paper = new PaperBoundary();
    public final CapturedScheduler scheduler = new CapturedScheduler(paper);
    public final StaticDungeon dungeon;
    public final Run run;
    public final World world = mock(World.class);

    public RunFixture(Path folder, YamlConfiguration settings) throws Exception {
        when(paper.plugin.getDataFolder()).thenReturn(folder.toFile());
        when(paper.plugin.namespace()).thenReturn("dungeons");
        paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        paper.bukkit.when(Bukkit::getDefaultGameMode).thenReturn(GameMode.SURVIVAL);
        paper.runtime.setPreparations(new nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry());
        paper.runtime.setPlayerSessions(new PlayerSessionRegistry(paper.plugin));
        paper.runtime.setActiveInstances(new ActiveInstanceRegistry(paper.plugin));
        when(world.getName()).thenReturn("crypt_0");
        var worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        paper.bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        when(world.key()).thenReturn(new NamespacedKey("dungeons", "crypt_0"));
        when(world.getEntitiesByClass(EnderDragon.class)).thenReturn(List.of());
        dungeon = paper.dungeon(folder.resolve("crypt"), settings);
        run = new Run(dungeon, world);
    }

    public DungeonPlayerSession session(PlayerState state) {
        return paper.runtime.playerSessions().put(state.player);
    }

    @Override
    public void close() {
        paper.close();
    }

    public static final class Run extends StaticInstance {
        public Run(StaticDungeon dungeon, World world) {
            this(dungeon, world, null);
        }

        public Run(StaticDungeon dungeon, World world, CountDownLatch latch) {
            super(dungeon, latch);
            this.instanceWorld = world;
            this.startLocation = new Location(world, 0, 70, 0);
        }

        public void completeInitialization(boolean failed) {
            this.loadFailed = failed;
            this.ready = !failed;
            this.signalInitializationComplete();
        }

        public void elapsed(int value) {
            this.timeElapsed = value;
        }
    }
}

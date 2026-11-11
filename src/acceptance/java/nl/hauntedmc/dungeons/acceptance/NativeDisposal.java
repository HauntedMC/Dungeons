package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import nl.hauntedmc.dungeons.event.DungeonDisposeEvent;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

/** A cancelled native unload must retain capacity and recover through the automatic retry path. */
final class NativeDisposal {
    private NativeDisposal() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_unload_retry", "static");
        dungeon.getConfig().set("runs.cleanup_delay_ticks", 0);
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        run -> {
                            var world = run.getInstanceWorld();
                            var name = world.getName();
                            var folder = world.getWorldFolder().toPath();
                            var cancellations = new AtomicInteger();
                            var disposed = new AtomicInteger();
                            Listener listener =
                                    new Listener() {
                                        @EventHandler
                                        public void blockFirstUnload(WorldUnloadEvent event) {
                                            if (event.getWorld() == world
                                                    && cancellations.getAndIncrement() == 0)
                                                event.setCancelled(true);
                                        }

                                        @EventHandler
                                        public void completed(DungeonDisposeEvent event) {
                                            if (event.getInstance() == run)
                                                disposed.incrementAndGet();
                                        }
                                    };
                            Bukkit.getPluginManager().registerEvents(listener, test);
                            try {
                                require(
                                        !run.disposeAsync().join(),
                                        "Cancelled native unload reported successful disposal");
                                require(
                                        run.getDisposalState()
                                                == DungeonInstance.DisposalState.RETRYABLE_FAILURE,
                                        "Cancelled unload was not retryable");
                                require(
                                        Bukkit.getWorld(name) == world && Files.exists(folder),
                                        "Failed unload lost its live world or files");
                                require(
                                        dungeon.getInstances().contains(run)
                                                && test.runtime()
                                                        .activeInstances()
                                                        .getActiveInstances()
                                                        .contains(run),
                                        "Failed unload released its registration");
                                require(
                                        disposed.get() == 0,
                                        "Failed unload emitted a completion event");
                                return test.await(
                                                "automatic unload retry and safe file cleanup",
                                                () ->
                                                        run.getDisposalState()
                                                                        == DungeonInstance
                                                                                .DisposalState
                                                                                .DISPOSED
                                                                && Bukkit.getWorld(name) == null
                                                                && filesGone(folder))
                                        .thenRun(
                                                () -> {
                                                    require(
                                                            disposed.get() == 1,
                                                            "Disposal completion was not emitted"
                                                                    + " exactly once");
                                                    require(
                                                            !test.runtime()
                                                                    .activeInstances()
                                                                    .getActiveInstances()
                                                                    .contains(run),
                                                            "Successful retry retained active"
                                                                    + " capacity");
                                                    require(
                                                            !dungeon.getInstances().contains(run),
                                                            "Successful retry retained dungeon"
                                                                    + " registration");
                                                })
                                        .whenComplete(
                                                (ignored, failure) ->
                                                        HandlerList.unregisterAll(listener));
                            } catch (Throwable failure) {
                                HandlerList.unregisterAll(listener);
                                throw failure;
                            }
                        });
    }

    private static boolean filesGone(Path folder) {
        if (Files.exists(folder)) return false;
        try (var entries = Files.list(folder.getParent())) {
            String retiredPrefix = folder.getFileName() + ".disposed-";
            return entries.noneMatch(
                    path -> path.getFileName().toString().startsWith(retiredPrefix));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

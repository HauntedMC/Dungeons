package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import nl.hauntedmc.dungeons.content.function.ChunkLoadFunction;
import nl.hauntedmc.dungeons.content.function.RedstoneBlockFunction;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.function.SpawnMobFunction;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.event.RemoteTriggerEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

final class NativeMechanics {
    private NativeMechanics() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_static", "static");
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        run ->
                                verifySignal(test, run)
                                        .thenCompose(ignored -> verifyEffects(test, run)));
    }

    private static CompletionStage<Void> verifySignal(AcceptancePlugin test, PlayableInstance run) {
        var signal = new SetStatusFunction(Map.of("status", "signal-received"));
        signal.setTrigger(
                new RemoteTrigger(
                        Map.of("triggerName", "native-signal", "awaitConditions", false)));
        enable(run, signal, new Location(run.getInstanceWorld(), 1, 65, 1));
        run.setStatus("waiting");
        var cancelled =
                new RemoteTriggerEvent(
                        "native-signal", signal.getTrigger(), run, 0, signal.getLocation(), null);
        cancelled.setCancelled(true);
        Bukkit.getPluginManager().callEvent(cancelled);
        require("waiting".equals(run.getStatus()), "Cancelled signal executed its function");
        Bukkit.getPluginManager()
                .callEvent(
                        new RemoteTriggerEvent(
                                "native-signal",
                                signal.getTrigger(),
                                run,
                                0,
                                signal.getLocation(),
                                null));
        return test.await(
                "registered remote-trigger to function relay",
                () -> "signal-received".equals(run.getStatus()));
    }

    private static CompletionStage<Void> verifyEffects(
            AcceptancePlugin test, PlayableInstance run) {
        var world = run.getInstanceWorld();
        world.setTime(18000);
        // A headless server has no players keeping entity and redstone chunks active.
        var chunks = new ChunkLoadFunction(Map.of("radius", 1));
        enable(run, chunks, new Location(world, 0, 65, 0));
        chunks.runFunction(null, List.of());
        require(world.isChunkForceLoaded(-1, 0), "Native chunk ticket was not acquired");
        var spawner = new SpawnMobFunction(Map.of("mob", "zombie", "maxCount", 1));
        enable(run, spawner, new Location(world, -1, 65, 15));
        spawner.runFunction(null, List.of());
        return test.await(
                        "native zombie spawn",
                        () -> !world.getEntitiesByClass(Zombie.class).isEmpty())
                .thenCompose(ignored -> verifyRedstone(test, run))
                .thenCompose(
                        ignored -> {
                            var mobs = world.getEntitiesByClass(Zombie.class);
                            require(mobs.size() == 1, "Spawner did not honor its finite count");
                            var mob = mobs.iterator().next();
                            require(
                                    mob.getAttribute(Attribute.MAX_HEALTH).getValue() == 20,
                                    "Native mob health attribute was not initialized correctly");
                            require(
                                    mob.getHealth() == 20,
                                    "Spawned mob did not receive full health");
                            var modifierKey =
                                    new NamespacedKey(
                                            test.runtime().environment().plugin(), "dungeon_level");
                            require(
                                    mob
                                            .getAttribute(Attribute.ATTACK_DAMAGE)
                                            .getModifiers()
                                            .stream()
                                            .anyMatch(
                                                    modifier ->
                                                            modifier.getKey().equals(modifierKey)),
                                    "Native mob damage modifier was not applied");
                            require(
                                    run.getEntities().contains(mob),
                                    "Actual entity-spawn listener did not track its owned mob");
                            chunks.disable();
                            run.getFunctions().remove(chunks.getLocation());
                            require(
                                    !world.isChunkForceLoaded(-1, 0),
                                    "Native chunk ticket leaked after disable");
                            return WorldFixtures.dispose(test, run)
                                    .thenRun(
                                            () ->
                                                    require(
                                                            !mob.isValid(),
                                                            "Owned mob survived world disposal"));
                        });
    }

    private static CompletionStage<Void> verifyRedstone(
            AcceptancePlugin test, PlayableInstance run) {
        var redstone = new RedstoneBlockFunction(Map.of("count", 2, "rate", 8));
        var pulse = new Location(run.getInstanceWorld(), 4, 65, 1);
        enable(run, redstone, pulse);
        redstone.runFunction(null, List.of());
        return test.await(
                        "first redstone pulse",
                        () -> pulse.getBlock().getType() == Material.REDSTONE_BLOCK)
                .thenCompose(
                        ignored ->
                                test.await(
                                        "redstone reset between pulses",
                                        () -> pulse.getBlock().getType() == Material.AIR))
                .thenCompose(
                        ignored ->
                                test.await(
                                        "final redstone pulse",
                                        () ->
                                                pulse.getBlock().getType()
                                                        == Material.REDSTONE_BLOCK));
    }

    static void enable(PlayableInstance run, DungeonFunction function, Location location) {
        function.initialize();
        function.enable(run, location);
        run.getFunctions().put(location, function);
    }
}

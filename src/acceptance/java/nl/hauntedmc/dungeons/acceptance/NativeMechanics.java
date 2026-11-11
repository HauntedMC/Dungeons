package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import nl.hauntedmc.dungeons.content.function.ChunkLoadFunction;
import nl.hauntedmc.dungeons.content.function.RedstoneBlockFunction;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.function.SpawnMobFunction;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.event.RemoteTriggerEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;

final class NativeMechanics {
    private NativeMechanics() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_static", "static");
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        run ->
                                verifySignal(test, run)
                                        .thenCompose(ignored -> verifyReactiveMechanics(test, run))
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
        return test.afterTicks(2L)
                .thenCompose(
                        ignored -> {
                            require(
                                    "waiting".equals(run.getStatus()),
                                    "Cancelled signal scheduled its function");
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
                        });
    }

    private static CompletionStage<Void> verifyReactiveMechanics(
            AcceptancePlugin test, PlayableInstance run) {
        var timerEffect = new SetStatusFunction(Map.of("status", "timer-fired"));
        timerEffect.setTrigger(
                new nl.hauntedmc.dungeons.content.trigger.TimerTrigger(
                        Map.of("delay", 1, "interval", 1)));
        enable(run, timerEffect, new Location(run.getInstanceWorld(), 2, 65, 1));
        return test.await("native one-shot timer", () -> "timer-fired".equals(run.getStatus()))
                .thenCompose(
                        ignored -> {
                            run.getInstanceVariables().set("reactive_score", 0);
                            var watcherEffect =
                                    new SetStatusFunction(Map.of("status", "variable-fired"));
                            watcherEffect.setTrigger(
                                    new nl.hauntedmc.dungeons.content.trigger.VariableChangeTrigger(
                                            Map.of("variable", "reactive_score")));
                            enable(
                                    run,
                                    watcherEffect,
                                    new Location(run.getInstanceWorld(), 3, 65, 1));
                            var writer =
                                    new nl.hauntedmc.dungeons.content.function.variable
                                            .InstanceVariableFunction(
                                            Map.of("varName", "reactive_score", "varValue", "1"));
                            enable(run, writer, new Location(run.getInstanceWorld(), 4, 65, 1));
                            writer.runFunction(null, List.of());
                            return test.await(
                                    "native variable-change trigger",
                                    () -> "variable-fired".equals(run.getStatus()));
                        })
                .thenAccept(
                        ignored -> {
                            var conditional =
                                    new nl.hauntedmc.dungeons.content.function.meta
                                            .ConditionalFunction(
                                            Map.of("comparison", "<reactive_score> = 1"));
                            conditional.addFunction(
                                    new SetStatusFunction(Map.of("status", "conditional-fired")));
                            var location = new Location(run.getInstanceWorld(), 5, 65, 1);
                            enable(run, conditional, location);
                            conditional.runFunction(null, List.of());
                            require(
                                    "conditional-fired".equals(run.getStatus()),
                                    "Native conditional group did not execute");
                            var block = location.clone().add(0, -1, 0).getBlock();
                            block.setType(Material.REDSTONE_BLOCK);
                            var condition =
                                    new nl.hauntedmc.dungeons.content.condition.BlockCondition();
                            condition.setInstance(run);
                            condition.setLocation(location);
                            require(
                                    condition.check(null),
                                    "Native block condition did not match its offset material");
                            block.setType(Material.STONE);
                            require(
                                    !condition.check(null),
                                    "Native block condition ignored a material change");
                        });
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

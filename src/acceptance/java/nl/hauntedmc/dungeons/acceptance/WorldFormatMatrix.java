package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/** Exercises current templates and editor persistence in all three environments. */
final class WorldFormatMatrix {
    private WorldFormatMatrix() {}

    static CompletionStage<Void> run(AcceptancePlugin test) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (World.Environment environment :
                List.of(
                        World.Environment.NORMAL,
                        World.Environment.NETHER,
                        World.Environment.THE_END)) {
            chain =
                    chain.thenCompose(
                            ignored -> {
                                try {
                                    var dungeon =
                                            WorldFixtures.definition(
                                                    test,
                                                    "matrix_"
                                                            + environment
                                                                    .name()
                                                                    .toLowerCase(Locale.ROOT),
                                                    "static",
                                                    environment);
                                    return verifyCycle(test, dungeon, environment);
                                } catch (Exception failure) {
                                    throw new CompletionException(failure);
                                }
                            });
        }
        return chain;
    }

    private static CompletableFuture<Void> verifyCycle(
            AcceptancePlugin test, DungeonDefinition dungeon, World.Environment environment) {
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        first -> {
                            require(
                                    first.getInstanceWorld().getEnvironment() == environment,
                                    "Wrong environment before editor save");
                            require(
                                    first.getInstanceWorld().getBlockAt(-1, 64, 15).getType()
                                            == Material.GOLD_BLOCK,
                                    "Terrain lost while loading " + dungeon.getWorldName());
                            return WorldFixtures.dispose(test, first);
                        })
                .thenCompose(
                        ignored -> {
                            var editor = dungeon.createEditSession(new CountDownLatch(1));
                            dungeon.getInstances().add(editor);
                            return test.await(
                                            "editor initialization " + dungeon.getWorldName(),
                                            () -> {
                                                require(
                                                        !editor.hasLoadFailed(),
                                                        "Editor world failed to load");
                                                return editor.isReady();
                                            })
                                    .thenCompose(
                                            ready -> {
                                                editor.getInstanceWorld()
                                                        .getBlockAt(-1, 64, 15)
                                                        .setType(Material.DIAMOND_BLOCK, false);
                                                var world = editor.getInstanceWorld();
                                                world.setChunkForceLoaded(0, 0, true);
                                                var cart =
                                                        world.spawn(
                                                                new org.bukkit.Location(
                                                                        world, 5.5, 70, 5.5),
                                                                org.bukkit.entity.Minecart.class);
                                                var mite =
                                                        world.spawn(
                                                                new org.bukkit.Location(
                                                                        world, 5.5, 70, 5.5),
                                                                org.bukkit.entity.Endermite.class);
                                                mite.setPersistent(true);
                                                mite.setRemoveWhenFarAway(false);
                                                require(
                                                        cart.addPassenger(mite),
                                                        "Could not create persisted farm"
                                                                + " passenger");
                                                return test.afterTicks(2)
                                                        .thenCompose(
                                                                tick ->
                                                                        test.onMain(
                                                                                editor
                                                                                        .saveWorldAsync()))
                                                        .thenCompose(
                                                                saved -> {
                                                                    return WorldFixtures.dispose(
                                                                            test, editor);
                                                                });
                                            });
                        })
                .thenCompose(ignored -> WorldFixtures.start(test, dungeon))
                .thenCompose(
                        reopened -> {
                            require(
                                    reopened.getInstanceWorld().getEnvironment() == environment,
                                    "Wrong environment after editor save");
                            require(
                                    reopened.getInstanceWorld().getBlockAt(-1, 64, 15).getType()
                                            == Material.DIAMOND_BLOCK,
                                    "Editor change lost on replay");
                            reopened.getInstanceWorld().setChunkForceLoaded(0, 0, true);
                            return test.await(
                                            "saved minecart and endermite "
                                                    + dungeon.getWorldName(),
                                            () ->
                                                    reopened
                                                            .getInstanceWorld()
                                                            .getEntitiesByClass(
                                                                    org.bukkit.entity.Minecart
                                                                            .class)
                                                            .stream()
                                                            .anyMatch(
                                                                    cart ->
                                                                            cart
                                                                                    .getPassengers()
                                                                                    .stream()
                                                                                    .anyMatch(
                                                                                            passenger ->
                                                                                                    passenger
                                                                                                            instanceof
                                                                                                            org
                                                                                                                    .bukkit
                                                                                                                    .entity
                                                                                                                    .Endermite)))
                                    .thenCompose(ready -> WorldFixtures.dispose(test, reopened));
                        });
    }
}

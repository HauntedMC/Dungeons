package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPOutputStream;

/** Exercises old templates, modern templates and editor conversion in all three environments. */
final class WorldFormatMatrix {
    private WorldFormatMatrix() {}

    static CompletionStage<Void> run(AcceptancePlugin test) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (World.Environment environment :
                List.of(
                        World.Environment.NORMAL,
                        World.Environment.NETHER,
                        World.Environment.THE_END)) {
            for (boolean legacy : new boolean[] {false, true}) {
                chain =
                        chain.thenCompose(
                                ignored -> {
                                    try {
                                        String name =
                                                "matrix_"
                                                        + environment
                                                                .name()
                                                                .toLowerCase(Locale.ROOT)
                                                        + (legacy ? "_legacy" : "_modern");
                                        var dungeon =
                                                WorldFixtures.definition(
                                                        test, name, "static", environment);
                                        if (legacy)
                                            makeLegacyTemplate(
                                                    dungeon.getFolder().toPath(), environment);
                                        return verifyCycle(test, dungeon, environment);
                                    } catch (Exception failure) {
                                        throw new CompletionException(failure);
                                    }
                                });
            }
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
                                                var cart = world.spawn(new org.bukkit.Location(world, 5.5, 70, 5.5), org.bukkit.entity.Minecart.class);
                                                var mite = world.spawn(new org.bukkit.Location(world, 5.5, 70, 5.5), org.bukkit.entity.Endermite.class);
                                                mite.setPersistent(true);
                                                mite.setRemoveWhenFarAway(false);
                                                require(cart.addPassenger(mite), "Could not create persisted farm passenger");
                                                return test.afterTicks(2).thenCompose(tick -> test.onMain(editor.saveWorldAsync()))
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
                            return test.await("saved minecart and endermite " + dungeon.getWorldName(), () ->
                                    reopened.getInstanceWorld().getEntitiesByClass(org.bukkit.entity.Minecart.class).stream()
                                        .anyMatch(cart -> cart.getPassengers().stream().anyMatch(passenger -> passenger instanceof org.bukkit.entity.Endermite)))
                                    .thenCompose(ready -> WorldFixtures.dispose(test, reopened));
                        });
    }

    private static void makeLegacyTemplate(Path template, World.Environment environment)
            throws Exception {
        String dimension =
                environment == World.Environment.NETHER
                        ? "DIM-1"
                        : environment == World.Environment.THE_END ? "DIM1" : "";
        if (!dimension.isEmpty()) {
            Files.createDirectories(template.resolve(dimension));
            for (String folder : List.of("region", "entities", "poi")) {
                if (Files.exists(template.resolve(folder)))
                    Files.move(
                            template.resolve(folder), template.resolve(dimension).resolve(folder));
            }
        }
        org.apache.commons.io.FileUtils.deleteDirectory(template.resolve("data").toFile());
        // Minimal pre-26.1 level data. Region files retain their native version and contents.
        try (var output =
                new DataOutputStream(
                        new GZIPOutputStream(
                                Files.newOutputStream(template.resolve("level.dat"))))) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(10);
            output.writeUTF("Data");
            output.writeByte(3);
            output.writeUTF("version");
            output.writeInt(19133);
            output.writeByte(3);
            output.writeUTF("DataVersion");
            output.writeInt(4189);
            output.writeByte(10);
            output.writeUTF("Version");
            output.writeByte(3);
            output.writeUTF("Id");
            output.writeInt(4189);
            output.writeByte(8);
            output.writeUTF("Name");
            output.writeUTF("1.21.4");
            output.writeByte(8);
            output.writeUTF("Series");
            output.writeUTF("main");
            output.writeByte(1);
            output.writeUTF("Snapshot");
            output.writeByte(0);
            output.writeByte(0);
            output.writeByte(8);
            output.writeUTF("LevelName");
            output.writeUTF("Legacy fixture");
            output.writeByte(1);
            output.writeUTF("initialized");
            output.writeByte(1);
            output.writeByte(3);
            output.writeUTF("SpawnY");
            output.writeInt(65);
            output.writeByte(0);
            output.writeByte(0);
        }
    }
}

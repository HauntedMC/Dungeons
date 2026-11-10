package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

final class WorldFixtures {
    private WorldFixtures() {}

    static DungeonDefinition definition(AcceptancePlugin test, String name, String type)
            throws Exception {
        return definition(test, name, type, World.Environment.NORMAL);
    }

    static DungeonDefinition definition(
            AcceptancePlugin test, String name, String type, World.Environment environment)
            throws Exception {
        var existing = test.runtime().dungeonCatalog().get(name);
        if (existing != null) return existing;
        var templateRecord =
                test.getDataFolder()
                        .toPath()
                        .resolve("template-path-" + environment.name() + ".txt");
        Path template;
        if (!Files.exists(templateRecord)) {
            var generator =
                    test.runtime()
                            .environment()
                            .plugin()
                            .getDefaultWorldGenerator("acceptance_template", "void");
            require(generator != null, "Packaged void generator was not resolved");
            World world =
                    Bukkit.createWorld(
                            new WorldCreator(
                                            "acceptance_template_"
                                                    + environment
                                                            .name()
                                                            .toLowerCase(Locale.ROOT))
                                    .environment(environment)
                                    .generator(generator)
                                    .generateStructures(false)
                                    .seed(1234));
            require(world != null, "Cannot create the template world");
            world.getBlockAt(-1, 64, 15).setType(Material.GOLD_BLOCK, false);
            world.getBlockAt(2, 65, 1).setType(Material.CHEST, false);
            var chest = (Chest) world.getBlockAt(2, 65, 1).getState();
            chest.customName(Component.text("Acceptance | chest !"));
            chest.getSnapshotInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
            require(chest.update(true, false), "Cannot persist the template chest");
            require(
                    ((Chest) world.getBlockAt(2, 65, 1).getState())
                                    .getBlockInventory()
                                    .getItem(0)
                                    .getAmount()
                            == 5,
                    "Template chest fixture is not populated");
            template = world.getWorldFolder().toPath();
            Files.writeString(templateRecord, template.toAbsolutePath().toString());
            world.save(true);
            require(Bukkit.unloadWorld(world, true), "Cannot unload the template before copying");
        } else {
            template = Path.of(Files.readString(templateRecord));
        }
        var folder = test.runtime().dungeonFiles().toPath().resolve(name);
        Files.createDirectories(folder);
        try (var paths = Files.walk(template)) {
            for (var path : paths.toList()) {
                if (path.getFileName().toString().equals("session.lock")) continue;
                var target = folder.resolve(template.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        var config = new YamlConfiguration();
        config.set("dungeon.type", type);
        config.set("dungeon.environment", environment.name());
        config.set("dungeon.chunk_generator", "Dungeons:void");
        config.set("locations.start", Map.of("x", 1.5, "y", 65.0, "z", 1.5));
        config.set("rules.spawning.natural_mobs", false);
        config.set("rules.world.freeze_random_ticks", true);
        config.set("rules.building.break_blocks", false);
        config.set("open.preload_world", false);
        config.set("open.max_players", 2);
        if (type.equals("open")) {
            config.set("runs.max_active", 1);
            config.set("open.empty_unload_delay_ticks", 2);
        }
        if (type.equals("branching")) {
            config.set("generator.layout", "branching");
            config.set("generator.seal_open_connectors_with", "AIR");
            config.set("generator.branching.trunk.room_target.min", 2);
            config.set("generator.branching.trunk.room_target.max", 2);
            config.set("generator.branching.trunk.room_target.enforce_minimum", true);
        }
        config.save(folder.resolve("config.yml").toFile());
        var dungeon = test.runtime().dungeonCatalog().loadDungeon(folder.toFile());
        require(dungeon != null, "Actual catalog rejected " + name);
        return dungeon;
    }

    static CompletableFuture<PlayableInstance> start(
            AcceptancePlugin test, DungeonDefinition dungeon) {
        var run = dungeon.createPlaySession(new CountDownLatch(1), null);
        require(run != null, "Run factory returned null for " + dungeon.getWorldName());
        if (!dungeon.getInstances().contains(run)) dungeon.getInstances().add(run);
        test.runtime().activeInstances().registerActiveInstance(run);
        return test.await(
                        "initialized run " + dungeon.getWorldName(),
                        () -> {
                            require(
                                    !run.hasLoadFailed(),
                                    "World initialization failed for " + dungeon.getWorldName());
                            return run.isReady() && run.isStarted();
                        })
                .thenApply(ignored -> run);
    }

    static CompletableFuture<Void> dispose(
            AcceptancePlugin test, DungeonInstance... runs) {
        var names = Arrays.stream(runs).map(run -> run.getInstanceWorld().getName()).toList();
        var folders =
                Arrays.stream(runs)
                        .map(run -> run.getInstanceWorld().getWorldFolder().toPath())
                        .toList();
        for (var run : runs) run.dispose();
        return test.await(
                "world unload, registry removal and temporary-file cleanup",
                () ->
                        names.stream().allMatch(name -> Bukkit.getWorld(name) == null)
                                && folders.stream().allMatch(Files::notExists)
                                && Arrays.stream(runs)
                                        .noneMatch(
                                                run ->
                                                        test.runtime()
                                                                .activeInstances()
                                                                .getActiveInstances()
                                                                .contains(run)));
    }
}

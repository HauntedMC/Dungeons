package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.util.List;
import java.util.concurrent.CompletionStage;
import nl.hauntedmc.dungeons.content.function.CommandFunction;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.util.world.GameRuleUtils;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Minecart;

final class WorldLifecycle {
    private WorldLifecycle() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_static", "static");
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        first ->
                                WorldFixtures.start(test, dungeon)
                                        .thenCompose(
                                                second -> {
                                                    var world = first.getInstanceWorld();
                                                    require(
                                                            !world.getUID()
                                                                    .equals(
                                                                            second.getInstanceWorld()
                                                                                    .getUID()),
                                                            "World copies share their native UUID");
                                                    require(
                                                            world.getBlockAt(-1, 64, 15).getType()
                                                                    == Material.GOLD_BLOCK,
                                                            "Template block at negative chunk"
                                                                    + " boundary was lost");
                                                    var chest =
                                                            (Chest)
                                                                    world.getBlockAt(2, 65, 1)
                                                                            .getState();
                                                    var item = chest.getBlockInventory().getItem(0);
                                                    require(
                                                            item != null
                                                                    && item.getType()
                                                                            == Material.DIAMOND
                                                                    && item.getAmount() == 5,
                                                            "Native tile inventory did not survive"
                                                                    + " the world copy");
                                                    require(
                                                            Integer.valueOf(0)
                                                                    .equals(
                                                                            world.getGameRuleValue(
                                                                                    GameRules
                                                                                            .RANDOM_TICK_SPEED)),
                                                            "Native world game rule was not"
                                                                    + " applied");
                                                    verifyGameRulePersistence(world);
                                                    require(
                                                            !world.isAutoSave(),
                                                            "Runtime world unexpectedly autosaves");
                                                    world.getBlockAt(-1, 64, 15)
                                                            .setType(Material.DIRT, false);
                                                    require(
                                                            second.getInstanceWorld()
                                                                            .getBlockAt(-1, 64, 15)
                                                                            .getType()
                                                                    == Material.GOLD_BLOCK,
                                                            "Changing one run mutated another world"
                                                                    + " copy");
                                                    return verifyConsoleSummons(test, first)
                                                            .thenCompose(
                                                                    ignored ->
                                                                            WorldFixtures.dispose(
                                                                                    test, first,
                                                                                    second));
                                                }));
    }

    private static void verifyGameRulePersistence(World world) {
        var saved = new YamlConfiguration();
        GameRuleUtils.capture(world, saved);
        var mobRule = GameRules.SPAWN_MOBS;
        var tickRule = GameRules.RANDOM_TICK_SPEED;
        boolean originalMobs = world.getGameRuleValue(mobRule);
        int originalTicks = world.getGameRuleValue(tickRule);
        try {
            world.setGameRule(mobRule, !originalMobs);
            world.setGameRule(tickRule, originalTicks + 1);
            var loaded = new YamlConfiguration();
            loaded.loadFromString(saved.saveToString());
            GameRuleUtils.apply(world, loaded);
            require(
                    world.getGameRuleValue(mobRule) == originalMobs,
                    "Boolean gamerule did not survive YAML persistence");
            require(
                    world.getGameRuleValue(tickRule) == originalTicks,
                    "Integer gamerule did not survive YAML persistence");
            for (String path : saved.getKeys(true)) {
                if (!saved.isConfigurationSection(path))
                    saved.set(path, String.valueOf(saved.get(path)));
            }
            boolean rejected = false;
            try {
                GameRuleUtils.apply(world, saved);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "Untyped gamerule strings must be rejected");
        } catch (InvalidConfigurationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            world.setGameRule(mobRule, originalMobs);
            world.setGameRule(tickRule, originalTicks);
        }
    }

    private static CompletionStage<Void> verifyConsoleSummons(
            AcceptancePlugin test, PlayableInstance run) {
        var world = run.getInstanceWorld();
        var command = new CommandFunction();
        command.setInstance(run);
        command.setLocation(new Location(world, -15, 82, -4));
        command.setCommandType(2);
        command.setCommand("summon minecraft:ender_dragon ~ ~ ~ {DragonPhase:4}");
        var event = new TriggerFireEvent(run, null);
        command.runFunction(event, List.of());
        command.setCommand(
                "summon minecraft:minecart 35.5 69.0 -38.5"
                        + " {Passengers:[{id:\"minecraft:endermite\"}]}");
        command.runFunction(event, List.of());
        // Simulate players keeping these chunks active after the startup commands.
        world.setChunkForceLoaded(-1, -1, true);
        world.setChunkForceLoaded(2, -3, true);
        return test.await(
                "dragon and minecart passenger console summons",
                () ->
                        !world.getEntitiesByClass(EnderDragon.class).isEmpty()
                                && world.getEntitiesByClass(Minecart.class).stream()
                                        .anyMatch(
                                                cart ->
                                                        cart.getPassengers().stream()
                                                                .anyMatch(
                                                                        passenger ->
                                                                                passenger
                                                                                        instanceof
                                                                                        org.bukkit
                                                                                                .entity
                                                                                                .Endermite)));
    }
}

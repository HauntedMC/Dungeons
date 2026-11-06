package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.block.Chest;

import java.util.concurrent.CompletionStage;

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
                                                            "Template block at negative chunk boundary was lost");
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
                                                            "Native tile inventory did not survive the world copy");
                                                    require(
                                                            Integer.valueOf(0)
                                                                    .equals(
                                                                            world.getGameRuleValue(
                                                                                    GameRules
                                                                                            .RANDOM_TICK_SPEED)),
                                                            "Native world game rule was not applied");
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
                                                            "Changing one run mutated another world copy");
                                                    return WorldFixtures.dispose(
                                                            test, first, second);
                                                }));
    }
}

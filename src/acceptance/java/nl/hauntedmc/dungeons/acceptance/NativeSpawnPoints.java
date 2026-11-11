package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.util.concurrent.CompletionStage;
import org.bukkit.Location;

/** Exercises configured alternatives through actual world preparation in the packaged plugin. */
final class NativeSpawnPoints {
    private NativeSpawnPoints() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_spawnpoints", "static");
        dungeon.setStartSpawn(new Location(null, 1.5, 65, 1.5, 90, 10));
        dungeon.addStartSpawn("tower", new Location(null, 10.5, 65, 10.5, 180, -10));
        var configured = dungeon.getStartSpawns();
        require(configured.size() == 2, "Legacy start was lost when adding a named spawnpoint");
        require(
                !dungeon.getConfig().contains("locations.start"),
                "Spawnpoint edit retained legacy config");
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        first -> {
                            var selected = first.getStartLoc().clone();
                            require(
                                    selected.getWorld() == first.getInstanceWorld(),
                                    "Selected spawn uses the wrong world");
                            require(
                                    configured.values().stream()
                                            .anyMatch(
                                                    point -> {
                                                        var bound = point.clone();
                                                        bound.setWorld(first.getInstanceWorld());
                                                        return bound.equals(selected);
                                                    }),
                                    "Selected spawn is not a configured location and facing");
                            require(
                                    configured.values().stream()
                                            .allMatch(point -> point.getWorld() == null),
                                    "Instance startup mutated the definition's relative locations");
                            first.startGame();
                            require(
                                    selected.equals(first.getStartLoc()),
                                    "Repeated start rerolled the instance spawn");
                            return WorldFixtures.start(test, dungeon)
                                    .thenCompose(
                                            second -> {
                                                require(
                                                        selected.equals(first.getStartLoc()),
                                                        "Preparing another run changed the first spawn");
                                                require(
                                                        second.getStartLoc().getWorld()
                                                                == second.getInstanceWorld(),
                                                        "Second run inherited another world's spawn");
                                                return WorldFixtures.dispose(test, first, second);
                                            });
                        });
    }
}

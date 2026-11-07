package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.event.RemoteTriggerEvent;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.structure.StructurePiece;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.util.world.SchematicUtils;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.joml.Vector3i;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletionStage;

final class NativeBranching {
    private NativeBranching() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon =
                (BranchingDungeon)
                        WorldFixtures.definition(test, "acceptance_branching", "branching");
        Files.createDirectories(dungeon.getFolder().toPath().resolve("rooms"));
        var start = room(dungeon, "start", Material.GOLD_BLOCK);
        start.setSpawn(new Location(null, 1, 1, 1));
        start.addConnector(new SimpleLocation(2, 1, 1, SimpleLocation.Direction.EAST))
                .setSuccessChance(1);
        var end = room(dungeon, "end", Material.DIAMOND_BLOCK);
        end.setDepthString("2");
        end.addConnector(new SimpleLocation(0, 1, 1, SimpleLocation.Direction.WEST))
                .setSuccessChance(1);
        var signal = new SetStatusFunction(Map.of("status", "branch-ready"));
        signal.setLocation(new Location(null, 1, 1, 1));
        signal.setTrigger(
                new RemoteTrigger(Map.of("triggerName", "branch-room", "awaitConditions", false)));
        signal.initialize();
        start.addFunction(signal.getLocation(), signal);
        var saved =
                start.saveFunctionsAsync()
                        .thenCombine(end.saveRoomAsync(), (first, second) -> first && second);
        return test.onMain(saved)
                .thenCompose(
                        ignored -> {
                            signal.releaseEditorListener();
                            signal.getTrigger().releaseEditorListener();
                            var restored =
                                    (BranchingDungeon)
                                            test.runtime()
                                                    .dungeonCatalog()
                                                    .loadDungeon(dungeon.getFolder());
                            require(
                                    restored != null && restored.getUniqueRooms().size() == 2,
                                    "Saved room definitions did not reload through the catalog");
                            return WorldFixtures.start(test, restored)
                                    .thenCompose(
                                            run -> {
                                                var branch = (BranchingInstance) run;
                                                branch.prepareValidStartPoint();
                                                require(
                                                        branch.getRooms().size() == 2,
                                                        "Actual generator did not place two rooms");
                                                require(
                                                        branch.getStartLoc() != null,
                                                        "Generated start point was not resolved");
                                                var floorTypes = new HashSet<Material>();
                                                for (var room : branch.getRooms()) {
                                                    var bounds = room.getBounds();
                                                    floorTypes.add(
                                                            run.getInstanceWorld()
                                                                    .getBlockAt(
                                                                            (int) bounds.getMinX()
                                                                                    + 1,
                                                                            (int) bounds.getMinY(),
                                                                            (int) bounds.getMinZ()
                                                                                    + 1)
                                                                    .getType());
                                                    require(
                                                            !room.getUsedConnectors().isEmpty(),
                                                            "Generated room is disconnected");
                                                }
                                                require(
                                                        floorTypes.equals(
                                                                java.util.Set.of(
                                                                        Material.GOLD_BLOCK,
                                                                        Material.DIAMOND_BLOCK)),
                                                        "Native chunk generation did not place both"
                                                            + " schematic floors: "
                                                                + floorTypes);
                                                Bukkit.getPluginManager()
                                                        .callEvent(
                                                                new RemoteTriggerEvent(
                                                                        "branch-room",
                                                                        null,
                                                                        run,
                                                                        0,
                                                                        branch.getStartLoc(),
                                                                        null));
                                                return test.await(
                                                                "persisted generated-room mechanic",
                                                                () ->
                                                                        "branch-ready"
                                                                                .equals(
                                                                                        run
                                                                                                .getStatus()))
                                                        .thenCompose(
                                                                triggered ->
                                                                        WorldFixtures.dispose(
                                                                                test, run));
                                            });
                        });
    }

    private static BranchingRoomDefinition room(
            BranchingDungeon dungeon, String name, Material floor) throws java.io.IOException {
        var room = new BranchingRoomDefinition(dungeon, name, new BoundingBox(0, 0, 0, 2, 2, 2));
        room.setOccurrencesString("1");
        room.setDepthString("0+");
        var piece = new StructurePiece();
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                piece.add(
                        new StructurePieceBlock(
                                new Vector3i(x, 0, z), floor.createBlockData(), null));
            }
        }
        SchematicUtils.saveStructurePiece(
                dungeon.getFolder().toPath().resolve("rooms/" + name + ".struct").toFile(), piece);
        return room;
    }
}

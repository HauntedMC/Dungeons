package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.generation.structure.StructurePiece;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.util.world.SchematicUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;

final class NativeStructure {
    private NativeStructure() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_static", "static");
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        run -> {
                            var world = run.getInstanceWorld();
                            var captured = StructurePieceBlock.from(world.getBlockAt(2, 65, 1));
                            require(
                                    captured.getBlockNbt() != null,
                                    "Native chest SNBT capture returned no tile data");
                            var piece = new StructurePiece();
                            piece.add(captured);
                            var file =
                                    test.getDataFolder()
                                            .toPath()
                                            .resolve("native-chest.struct")
                                            .toFile();
                            verifySave(file, piece);
                            var restored = SchematicUtils.loadStructurePiece(file);
                            require(
                                    restored != null && restored.size() == 1,
                                    "Native structure file did not reload");
                            var target = new Location(world, 10, 65, 10);
                            restored.iterator().next().placeAt(target);
                            require(
                                    target.getBlock().getType() == Material.CHEST,
                                    "Native block was not placed");
                            var chest = (Chest) target.getBlock().getState();
                            var item = chest.getBlockInventory().getItem(0);
                            require(
                                    item != null
                                            && item.getType() == Material.DIAMOND
                                            && item.getAmount() == 5,
                                    "Native tile inventory did not survive capture, file reload and"
                                            + " placement");
                            require(
                                    Component.text("Acceptance | chest !")
                                            .equals(chest.customName()),
                                    "Delimiter-bearing tile name did not survive the native SNBT"
                                            + " path");
                            return WorldFixtures.dispose(test, run);
                        });
    }

    private static void verifySave(File file, StructurePiece piece) {
        try {
            SchematicUtils.saveStructurePiece(file, piece);
            byte[] saved = Files.readAllBytes(file.toPath());
            try {
                SchematicUtils.saveStructurePiece(new File(file, "invalid.struct"), piece);
                throw new AssertionError("Invalid schematic destination reported success");
            } catch (IOException expected) {
                require(
                        Arrays.equals(saved, Files.readAllBytes(file.toPath())),
                        "Failed schematic write changed the persisted structure");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

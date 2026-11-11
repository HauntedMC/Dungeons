package nl.hauntedmc.dungeons.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import nl.hauntedmc.dungeons.util.world.SchematicUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoValidationTest {
    @TempDir Path directory;
    private final UUID id = UUID.randomUUID();

    private RepoContent.Snapshot snapshot(String config) throws Exception {
        Path folder = Files.createDirectories(directory.resolve(UUID.randomUUID().toString()));
        Files.writeString(folder.resolve("config.yml"), "dungeon:\n  id: '" + id + "'\n" + config);
        return new RepoContent.Snapshot(folder, List.of(), "0".repeat(64), 0);
    }

    private RepoContent.Snapshot scan(RepoContent.Snapshot source) throws Exception {
        return RepoContent.snapshot(
                source.root(),
                directory.resolve(UUID.randomUUID().toString()),
                new RepoContent.Limits(16 * 1024 * 1024, 100));
    }

    @Test
    void draftsMayBeIncompleteButPublishingRequiresPlayableAnchors() throws Exception {
        var draft = scan(snapshot("  type: static\n"));
        assertEquals("static", RepoValidation.validate(draft, id, false).type());
        assertThrows(java.io.IOException.class, () -> RepoValidation.validate(draft, id, true));
        assertThrows(
                java.io.IOException.class,
                () -> RepoValidation.validate(draft, UUID.randomUUID(), false));
    }

    @Test
    void namedSpawnPointsCanBePublishedAndInvalidCollectionsCannot() throws Exception {
        for (String type : List.of("static", "open")) {
            var source =
                    scan(
                            snapshot(
                                    "  type: "
                                            + type
                                            + "\nlocations:\n  starts:\n    crypt: {x: 1.5, y: 70, z: 2.5, yaw: 90, pitch: 0}\n    '1': {x: 10.5, y: 70, z: 20.5}\n"));
            assertEquals(type, RepoValidation.validate(source, id, true).type());
        }
        for (String collection :
                List.of(
                        "{}",
                        "[]",
                        "{crypt: {x: 1, y: 2}}",
                        "{crypt: {x: .inf, y: 2, z: 3}}",
                        "{Crypt: {x: 1, y: 2, z: 3}, crypt: {x: 4, y: 5, z: 6}}")) {
            var source =
                    scan(
                            snapshot(
                                    "  type: static\nlocations:\n  start: {x: 0, y: 70, z: 0}\n  starts: "
                                            + collection
                                            + "\n"));
            assertThrows(
                    java.io.IOException.class,
                    () -> RepoValidation.validate(source, id, true),
                    collection);
        }
    }

    @Test
    void branchingUsesRoomSpawnsAndValidatesSchematicsWithoutBukkit() throws Exception {
        var source = snapshot("  type: branching\n");
        Files.createDirectories(source.root().resolve("rooms"));
        Files.writeString(
                source.root().resolve("rooms/start.yml"),
                "Bounds:\n  Corner1: {x: 0, y: 0, z: 0}\n  Corner2: {x: 2, y: 2, z: 2}\nConfig:\n  SpawnPoint: {x: 1, y: 1, z: 1}\n");
        Files.write(
                source.root().resolve("rooms/start.struct"),
                SchematicUtils.compress("DUNGEONS-STRUCTURE-2\nminecraft:stone\n0,0,0|0|null"));
        var result = RepoValidation.validate(scan(source), id, true);
        assertEquals(Set.of("minecraft:stone"), result.blockData());
        Files.write(
                source.root().resolve("rooms/start.struct"),
                SchematicUtils.compress("DUNGEONS-STRUCTURE-2\nminecraft:stone\n0,0,0|9|null"));
        assertThrows(
                java.io.IOException.class, () -> RepoValidation.validate(scan(source), id, true));
        Files.delete(source.root().resolve("rooms/start.struct"));
        assertThrows(
                java.io.IOException.class, () -> RepoValidation.validate(scan(source), id, true));
    }

    @Test
    void nestedScriptDiscriminatorsAreDetectedWithoutInstantiation() throws Exception {
        var source = snapshot("  type: static\nlocations:\n  start: {x: 1, y: 2, z: 3}\n");
        Files.writeString(
                source.root().resolve("functions.yml"),
                "Functions:\n  - ==: dungeons.function.composite\n    children:\n      - TYPE--: dungeons.function.command\n        command: test\n");
        var result = RepoValidation.validate(scan(source), id, true);
        assertTrue(result.scripts());
        assertTrue(result.modelTypes().contains("dungeons.function.command"));
        Files.writeString(source.root().resolve("functions.yml"), "Functions: []\nFunctions: []\n");
        assertThrows(
                java.io.IOException.class, () -> RepoValidation.validate(scan(source), id, true));
    }
}

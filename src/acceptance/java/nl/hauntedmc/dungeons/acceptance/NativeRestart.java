package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

final class NativeRestart {
    private NativeRestart() {}

    static CompletionStage<Void> prepare(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_shutdown", "static");
        return WorldFixtures.start(test, dungeon)
                .thenAccept(
                        run -> {
                            try {
                                Files.writeString(
                                        test.getDataFolder().toPath().resolve("shutdown-world.txt"),
                                        run.getInstanceWorld()
                                                .getWorldFolder()
                                                .toPath()
                                                .toAbsolutePath()
                                                .toString());
                            } catch (Exception failure) {
                                throw new CompletionException(failure);
                            }
                            // Leave an actual world running so the script tests Dungeons' real
                            // onDisable cleanup.
                        });
    }

    static CompletionStage<Void> verify(AcceptancePlugin test) throws Exception {
        NativePersistence.verify(test);
        var branch = test.runtime().dungeonCatalog().get("acceptance_branching");
        require(
                branch instanceof BranchingDungeon,
                "Branching map did not survive a process restart");
        var dungeon = (BranchingDungeon) branch;
        require(
                dungeon.getUniqueRooms().size() == 2 && dungeon.getStartRooms().size() == 1,
                "Room definitions or start-room registration changed after restart");
        var shutdownWorld =
                Path.of(
                        Files.readString(
                                test.getDataFolder().toPath().resolve("shutdown-world.txt")));
        require(
                !Files.exists(shutdownWorld),
                "Plugin shutdown left its temporary world files behind: " + shutdownWorld);
        require(
                test.runtime().activeInstances().getActiveInstances().isEmpty(),
                "New runtime inherited active instances from the previous JVM");
        return CompletableFuture.completedFuture(null);
    }
}

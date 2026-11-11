package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.dungeons.repository.*;
import nl.hauntedmc.dungeons.runtime.staff.StaffOperations;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** Real Paper + provider round trip, including portable local settings and first installation. */
final class NativeRepository {
    private NativeRepository() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = WorldFixtures.definition(test, "acceptance_repository", "static");
        var repository = test.runtime().contentRepository();
        var console = Bukkit.getConsoleSender();
        var id = dungeon.getDungeonId();
        var ops = test.runtime().staffOperations();
        return test.onMain(
                        repository.publish(
                                dungeon.getWorldName(),
                                "patch",
                                "Native initial release",
                                console,
                                true))
                .thenCompose(
                        ignored -> {
                            dungeon.setSaveConfig("dungeon.enabled", false);
                            dungeon.setExit(Bukkit.getWorlds().getFirst().getSpawnLocation());
                            return test.onMain(
                                    repository
                                            .status(dungeon.getWorldName(), console, false)
                                            .thenAccept(
                                                    lines -> {
                                                        require(
                                                                lines.stream()
                                                                        .anyMatch(
                                                                                line ->
                                                                                        line
                                                                                                .contains(
                                                                                                        "1.0.0 CLEAN")),
                                                                "Local settings incorrectly dirty the working copy: "
                                                                        + lines);
                                                    }));
                        })
                .thenCompose(
                        ignored -> {
                            dungeon.setSaveConfig("dungeon.display_name", "Repository revised map");
                            return test.onMain(
                                    repository.publish(
                                            dungeon.getWorldName(),
                                            "minor",
                                            "Native minor release",
                                            console,
                                            false));
                        })
                .thenCompose(
                        ignored -> {
                            try {
                                var local = RepoLocal.read(dungeon.getFolder().toPath());
                                require(
                                        local.version().equals(RepoVersion.parse("1.1.0")),
                                        "Push did not increment version");
                                ops.maintenance(
                                        console,
                                        dungeon.getWorldName(),
                                        true,
                                        "Native repository archive");
                                return test.onMain(ops.archive(dungeon));
                            } catch (Exception failure) {
                                return CompletableFuture.failedFuture(failure);
                            }
                        })
                .thenCompose(
                        ignored ->
                                test.onMain(
                                        repository.pull(id.toString(), null, false, null, console)))
                .thenCompose(
                        ignored ->
                                test.await(
                                        "repository installation",
                                        () -> {
                                            var operations =
                                                    ops.operations().stream()
                                                            .filter(
                                                                    op ->
                                                                            op.action.equals(
                                                                                    "repo-pull"))
                                                            .toList();
                                            if (operations.isEmpty()) return false;
                                            var op = operations.getLast();
                                            require(
                                                    op.state != StaffOperations.State.FAILED,
                                                    "Repository pull failed: " + op.detail);
                                            return op.state == StaffOperations.State.SUCCEEDED;
                                        }))
                .thenRun(
                        () -> {
                            var restored = test.runtime().dungeonCatalog().get(id.toString());
                            require(
                                    restored != null && restored.isRepositoryInitialized(),
                                    "Pulled map was not initialized");
                            require(
                                    !restored.getConfig().getBoolean("dungeon.enabled"),
                                    "First install was enabled automatically");
                            require(
                                    restored.getConfig().get("locations.exit") == null,
                                    "Publisher's external exit leaked to a first installation");
                            require(
                                    restored.getDisplayName().contains("Repository revised map"),
                                    "Published content did not round trip");
                            try {
                                require(
                                        RepoLocal.read(restored.getFolder().toPath())
                                                .version()
                                                .equals(RepoVersion.parse("1.1.0")),
                                        "Installed version differs from head");
                                Files.writeString(
                                        test.getDataFolder().toPath().resolve("repository-id.txt"),
                                        id.toString());
                            } catch (Exception failure) {
                                throw new CompletionException(failure);
                            }
                        })
                .thenCompose(ignored -> forceAndRollback(test, id.toString()));
    }

    private static CompletionStage<Void> forceAndRollback(AcceptancePlugin test, String id) {
        var output = new CopyOnWriteArrayList<String>();
        var actor =
                (CommandSender)
                        Proxy.newProxyInstance(
                                CommandSender.class.getClassLoader(),
                                new Class<?>[] {CommandSender.class},
                                (proxy, method, arguments) ->
                                        switch (method.getName()) {
                                            case "getName" -> "RepositoryAcceptance";
                                            case "hasPermission" -> true;
                                            case "isPermissionSet" -> false;
                                            case "sendMessage" -> {
                                                if (arguments != null)
                                                    for (Object value : arguments)
                                                        if (value instanceof Component component)
                                                            output.add(
                                                                    PlainTextComponentSerializer
                                                                            .plainText()
                                                                            .serialize(component));
                                                yield null;
                                            }
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == arguments[0];
                                            default -> null;
                                        });
        var repository = test.runtime().contentRepository();
        test.runtime()
                .dungeonCatalog()
                .get(id)
                .setSaveConfig("dungeon.display_name", "Unpublished local work");
        return test.onMain(repository.pull(id, null, true, null, actor))
                .thenCompose(ignored -> awaitPull(test))
                .thenCompose(
                        ignored -> {
                            String token = token(output);
                            output.clear();
                            return test.onMain(
                                    repository.pull(
                                            id, RepoVersion.parse("1.1.0"), true, token, actor));
                        })
                .thenCompose(ignored -> awaitPull(test))
                .thenCompose(
                        ignored -> {
                            require(
                                    test.runtime()
                                            .dungeonCatalog()
                                            .get(id)
                                            .getDisplayName()
                                            .contains("Repository revised map"),
                                    "Confirmed forced pull did not replace dirty content");
                            output.clear();
                            return test.onMain(
                                    repository.rollback(
                                            id,
                                            RepoVersion.FIRST,
                                            "patch",
                                            "Native rollback",
                                            null,
                                            actor));
                        })
                .thenCompose(
                        ignored -> {
                            String token = token(output);
                            output.clear();
                            return test.onMain(
                                    repository.rollback(
                                            id,
                                            RepoVersion.FIRST,
                                            "patch",
                                            "Native rollback",
                                            token,
                                            actor));
                        })
                .thenCompose(
                        ignored ->
                                test.onMain(
                                        repository
                                                .history(id, actor, 1)
                                                .thenAccept(
                                                        history ->
                                                                require(
                                                                        history.size() == 3
                                                                                && history.getFirst()
                                                                                        .startsWith(
                                                                                                "1.1.1"),
                                                                        "Rollback did not append a new patch release"))));
    }

    private static CompletionStage<Void> awaitPull(AcceptancePlugin test) {
        return test.await(
                "confirmed repository pull",
                () -> {
                    var operations =
                            test.runtime().staffOperations().operations().stream()
                                    .filter(operation -> operation.action.equals("repo-pull"))
                                    .toList();
                    if (operations.isEmpty()) return false;
                    var operation = operations.getLast();
                    require(
                            operation.state != StaffOperations.State.FAILED,
                            "Repository force flow failed: " + operation.detail);
                    return operation.state == StaffOperations.State.SUCCEEDED;
                });
    }

    private static String token(java.util.List<String> output) {
        var pattern = Pattern.compile("--confirm ([0-9a-f-]{36})");
        for (String line : output) {
            var match = pattern.matcher(line);
            if (match.find()) return match.group(1);
        }
        throw new AssertionError("Confirmation was not delivered: " + output);
    }

    static CompletionStage<Void> restart(AcceptancePlugin test) throws Exception {
        var id = Files.readString(test.getDataFolder().toPath().resolve("repository-id.txt"));
        var dungeon = test.runtime().dungeonCatalog().get(id);
        require(
                dungeon != null && dungeon.isRepositoryInitialized(),
                "Repository working copy lost after restart");
        require(
                RepoLocal.read(dungeon.getFolder().toPath())
                        .version()
                        .equals(RepoVersion.parse("1.1.0")),
                "Repository version lost after restart");
        return test.onMain(
                test.runtime()
                        .contentRepository()
                        .history(id, Bukkit.getConsoleSender(), 1)
                        .thenAccept(
                                history ->
                                        require(
                                                history.size() == 3,
                                                "Published history lost after restart")));
    }
}

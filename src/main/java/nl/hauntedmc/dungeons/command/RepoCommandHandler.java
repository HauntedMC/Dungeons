package nl.hauntedmc.dungeons.command;

import java.util.*;
import java.util.concurrent.*;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.repository.*;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** Canonical command adapter. Async completions never deliver Bukkit messages on a worker. */
final class RepoCommandHandler implements CommandHandler {
    private final DungeonsRuntime runtime;

    RepoCommandHandler(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean handleCommand(CommandSender actor, String[] args) {
        var repo = runtime.contentRepository();
        if (repo == null) throw new IllegalArgumentException("Repository services unavailable");
        if (args.length < 1) throw new IllegalArgumentException("Use /dungeon help repo");
        String action = args[0].toLowerCase(Locale.ROOT);
        var input = new ArrayList<>(List.of(args));
        String token = option(input, "--confirm");
        if (token != null && !Set.of("pull", "rollback").contains(action))
            throw new IllegalArgumentException(
                    "--confirm only applies to forced pulls and rollbacks");
        CompletableFuture<?> result;
        switch (action) {
            case "fetch" -> {
                exact(input, 1);
                StaffPermissions.require(actor, "repo.inspect", null);
                result = repo.fetch();
            }
            case "list" -> {
                if (input.size() > 2) throw new IllegalArgumentException("Use repo list [page]");
                result = repo.list(actor, page(input, 1));
            }
            case "init" -> {
                exact(input, 2);
                result = repo.initialize(input.get(1), actor, false);
            }
            case "add" -> {
                minimum(input, 2);
                result = repo.publish(input.get(1), "patch", message(input, 2), actor, true);
            }
            case "push" -> {
                minimum(input, 3);
                result = repo.publish(input.get(1), input.get(2), message(input, 3), actor, false);
            }
            case "pull" -> {
                String version = option(input, "--version");
                boolean force = input.remove("--force");
                exact(input, 2);
                if (token != null && !force)
                    throw new IllegalArgumentException("--confirm requires --force");
                result =
                        repo.pull(
                                input.get(1),
                                version == null ? null : RepoVersion.parse(version),
                                force,
                                token,
                                actor);
            }
            case "rollback" -> {
                minimum(input, 4);
                result =
                        repo.rollback(
                                input.get(1),
                                RepoVersion.parse(input.get(2)),
                                input.get(3),
                                message(input, 4),
                                token,
                                actor);
            }
            case "history" -> {
                if (input.size() < 2 || input.size() > 3)
                    throw new IllegalArgumentException("Use repo history <dungeon> [page]");
                result = repo.history(input.get(1), actor, page(input, 2));
            }
            case "status", "diff" -> {
                if (input.size() > (action.equals("diff") ? 3 : 2)
                        || action.equals("diff") && input.size() < 2)
                    throw new IllegalArgumentException("Use repo " + action + " <dungeon>");
                result =
                        repo.status(
                                input.size() == 2 ? input.get(1) : null,
                                actor,
                                action.equals("diff"));
            }
            default -> throw new IllegalArgumentException("Use /dungeon help repo");
        }
        LangUtils.sendMessage(actor, "repository.started");
        result.whenComplete(
                (value, failure) -> {
                    if (!runtime.environment().plugin().isEnabled()) return;
                    try {
                        Bukkit.getScheduler()
                                .runTask(
                                        runtime.environment().plugin(),
                                        () -> {
                                            if (failure != null) {
                                                Throwable detail = failure;
                                                while ((detail instanceof CompletionException
                                                                || detail
                                                                        instanceof
                                                                        ExecutionException)
                                                        && detail.getCause() != null)
                                                    detail = detail.getCause();
                                                runtime.environment()
                                                        .plugin()
                                                        .getSLF4JLogger()
                                                        .warn(
                                                                "Repository command {} failed",
                                                                action,
                                                                detail);
                                                LangUtils.sendMessage(
                                                        actor,
                                                        "repository.failed",
                                                        LangUtils.placeholder(
                                                                "detail",
                                                                Objects.toString(
                                                                        detail.getMessage(),
                                                                        "Operation failed")));
                                            } else if (value instanceof Collection<?> lines)
                                                lines.forEach(
                                                        line ->
                                                                LangUtils.sendRaw(
                                                                        actor,
                                                                        String.valueOf(line)));
                                            else LangUtils.sendRaw(actor, String.valueOf(value));
                                        });
                    } catch (RuntimeException shuttingDown) {
                        /* Durable journals own interrupted outcomes. */
                    }
                });
        return true;
    }

    private static String option(List<String> input, String flag) {
        int position = input.indexOf(flag);
        if (position < 0) return null;
        if (position + 1 >= input.size() || input.get(position + 1).startsWith("--"))
            throw new IllegalArgumentException(flag + " requires a value");
        String value = input.remove(position + 1);
        input.remove(position);
        if (input.contains(flag)) throw new IllegalArgumentException("Duplicate option " + flag);
        return value;
    }

    private static void minimum(List<String> args, int count) {
        if (args.size() < count)
            throw new IllegalArgumentException("Incomplete repo command; use /dungeon help repo");
    }

    private static void exact(List<String> args, int count) {
        if (args.size() != count)
            throw new IllegalArgumentException("Unexpected repo arguments; use /dungeon help repo");
    }

    private static String message(List<String> args, int from) {
        String result = String.join(" ", args.subList(from, args.size()));
        if (result.contains("--")) throw new IllegalArgumentException("Unknown repository option");
        if (result.length() > 1024)
            throw new IllegalArgumentException("Publication message exceeds 1024 characters");
        return result;
    }

    private static int page(List<String> args, int index) {
        try {
            int page = args.size() > index ? Integer.parseInt(args.get(index)) : 1;
            if (page < 1 || page > 100000) throw new NumberFormatException();
            return page;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Page must be 1–100000");
        }
    }

    @Override
    public List<String> handleTabComplete(CommandSender actor, String[] args) {
        return List.of();
    }
}

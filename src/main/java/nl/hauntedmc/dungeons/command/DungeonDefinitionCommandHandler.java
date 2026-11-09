package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.command.CommandOutput.FilterPageQuery;
import nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

/** Owns dungeon administration commands and their completions. */
final class DungeonDefinitionCommandHandler implements CommandHandler {
    private final DungeonRepository dungeonManager;
    private final PlayerSessionRegistry playerManager;
    private final DungeonQueueRegistry queueManager;
    private final DungeonQueueCoordinator dungeonQueueCoordinator;
    private final CommandOutput output;
    private final DungeonWorldCommandHandler worldCommands;

    DungeonDefinitionCommandHandler(DungeonRepository dungeonManager,
            PlayerSessionRegistry playerManager, DungeonQueueRegistry queueManager,
            DungeonQueueCoordinator dungeonQueueCoordinator, CommandOutput output,
            DungeonWorldCommandHandler worldCommands) {
        this.dungeonManager = dungeonManager;
        this.playerManager = playerManager;
        this.queueManager = queueManager;
        this.dungeonQueueCoordinator = dungeonQueueCoordinator;
        this.output = output;
        this.worldCommands = worldCommands;
    }

    /** Runs handle command. */
    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" ->
                    this.worldCommands.handleCreateCommand(
                            sender, CommandArguments.shiftArgs(args));
            case "import" ->
                    this.worldCommands.handleImportCommand(
                            sender, CommandArguments.shiftArgs(args));
            case "list" ->
                    this.handleDungeonListCommand(
                            sender, CommandArguments.shiftArgs(args));
            case "info" ->
                    this.handleDungeonInfoCommand(
                            sender, CommandArguments.shiftArgs(args));
            case "enable" ->
                    this.handleSetDungeonEnabledCommand(
                            sender, CommandArguments.shiftArgs(args), true);
            case "disable" ->
                    this.handleSetDungeonEnabledCommand(
                            sender, CommandArguments.shiftArgs(args), false);
            case "keys" -> this.handleAuditedKeys(sender, args);
            case "exit" -> this.handleExitCommand(sender, args);
            default -> {
                CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
                yield true;
            }
        };
    }

    /** Runs handle tab complete. */
    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            CommandArguments.addMatchingLiteralOptions(
                    options, args[1], "create", "import", "list", "info", "enable", "disable",
                    "keys", "exit");
            return options;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info", "enable", "disable" -> {
                if (args.length == 3) {
                    DungeonCompletions.addMatchingDungeonNames(this.dungeonManager, options, args[2]);
                }
            }
            case "list" -> {
                if (args.length == 3) {
                    DungeonCompletions.addMatchingDungeonNames(this.dungeonManager, options, args[2]);
                } else if (args.length == 4) {
                    CommandArguments.addMatchingLiteralOptions(
                            options, args[3], "1", "2", "3", "4", "5");
                }
            }

            case "import" -> {
                if (args.length == 4) {
                    CommandArguments.addMatchingLiteralOptions(
                            options, args[3], "NORMAL", "NETHER", "THE_END");
                }
            }
            case "create" -> {
                if (args.length == 4) {
                    this.addMatchingDungeonTypeNames(options, args[3]);
                }
            }
            case "keys" -> {
                if (args.length == 3)
                    CommandArguments.addMatchingLiteralOptions(
                            options, args[2], "add", "remove", "clear");
                else if (args.length == 4)
                    DungeonCompletions.addMatchingEditableDungeonNames(this.dungeonManager, 
                            sender, options, args[3]);
            }
            case "exit" -> {
                if (args.length == 3) {
                    CommandArguments.addMatchingLiteralOptions(options, args[2], "set");
                } else if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
                    DungeonCompletions.addMatchingDungeonNames(this.dungeonManager, options, args[3]);
                }
            }
            default -> {
                return List.of();
            }
        }

        return options;
    }

    private boolean handleAuditedKeys(CommandSender sender, String[] raw) {
        var input = new java.util.ArrayList<>(java.util.List.of(raw));
        String token = null;
        int confirmation = input.indexOf("--confirm");
        if (confirmation >= 0) {
            if (confirmation != input.size() - 2) throw new IllegalArgumentException("Use --confirm <token> at the end");
            token = input.removeLast();
            input.removeLast();
        }
        String[] args = input.toArray(String[]::new);
        if (args.length < 5) throw new IllegalArgumentException("A key change requires a reason");
        int reasonStart = args[2].equalsIgnoreCase("remove") ? 5 : 4;
        if (args.length <= reasonStart) throw new IllegalArgumentException("A key change requires a reason");
        var dungeon = this.dungeonManager.get(args[3]);
        if (dungeon == null) throw new IllegalArgumentException("Dungeon not found");
        nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(sender, "keys", dungeon.getWorldName());
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length));
        nl.hauntedmc.dungeons.runtime.staff.StaffOperations.requireReason(reason);
        var before = dungeon.getAccessKeys().stream().map(key -> key.getKeyId()).toList();
        var ops = dungeon.getRuntime().staffOperations();
        if (ops != null) ops.requireContentWritable(dungeon.getWorldName());
        if (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("clear")) {
            if (ops == null) throw new IllegalStateException("Staff confirmation service unavailable");
            var next = ops.confirm(sender, "key-" + args[2], dungeon.getWorldName(),
                    dungeon.getDungeonId() + ":" + dungeon.getConfig().saveToString() + ":" + before,
                    String.join(" ", input), token);
            if (next != null) {
                String command = CommandRoutes.canonicalCommand(CommandRoutes.Handler.MAP, input) + " --confirm " + next;
                sender.sendMessage(net.kyori.adventure.text.Component.text("[Confirm key change within 30 seconds]")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command)));
                return true;
            }
        }
        String[] operation = java.util.Arrays.copyOf(args, reasonStart);
        if (args[2].equalsIgnoreCase("remove")) {
            for (int index = 0; index < dungeon.getAccessKeys().size(); index++) {
                if (dungeon.getAccessKeys().get(index).getKeyId().equals(operation[4])) operation[4] = String.valueOf(index + 1);
            }
        }
        boolean result = this.handleKeysCommand(sender, operation);
        var after = dungeon.getAccessKeys().stream().map(key -> key.getKeyId()).toList();
        if (ops != null) ops.audit(sender, "key-" + args[2], dungeon.getWorldName(), reason,
                before.equals(after) ? "unchanged" : "updated");
        return result;
    }

    /** Runs handle keys command. */
    private boolean handleKeysCommand(CommandSender sender, String[] args) {
        if (args.length > 3)
            nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(
                    sender, "keys", args[3]);
        if (args.length < 4) {
            CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
            return true;
        }

        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" ->
                    args.length == 4
                            ? this.handleAddKeyCommand(
                                    sender, new String[] {"addkey", args[3]})
                            : this.handleInvalidDungeonKeysUsage(sender);
            case "remove" ->
                    args.length == 4
                            ? this.handleRemoveKeyCommand(
                                    sender, new String[] {"removekey", args[3]})
                            : args.length == 5
                                    ? this.handleRemoveKeyCommand(
                                            sender,
                                            new String[] {"removekey", args[3], args[4]})
                                    : this.handleInvalidDungeonKeysUsage(sender);
            case "clear" ->
                    args.length == 4
                            ? this.handleRemoveAllkeysCommand(
                                    sender, new String[] {"clearkeys", args[3]})
                            : this.handleInvalidDungeonKeysUsage(sender);
            default -> {
                CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
                yield true;
            }
        };
    }

    /** Runs handle exit command. */
    private boolean handleExitCommand(CommandSender sender, String[] args) {
        if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
            return this.handleSetExitCommand(
                    sender, new String[] {"setexit", args[3]});
        }

        CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
        return true;
    }

    /** Runs handle set exit command. */
    private boolean handleSetExitCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.dungeon.exit.set.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.exit.set.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            LangUtils.sendMessage(player, "commands.edit.open.no-permission");
            return true;
        }

        Location exitLoc = player.getLocation();
        dungeon.setExit(exitLoc);
        double exitX = MathUtils.round(exitLoc.getX(), 2);
        double exitY = MathUtils.round(exitLoc.getY(), 2);
        double exitZ = MathUtils.round(exitLoc.getZ(), 2);
        double exitYAW = MathUtils.round(exitLoc.getYaw(), 2);
        LangUtils.sendMessage(
                player,
                "commands.dungeon.exit.set.success",
                LangUtils.placeholder("x", String.valueOf(exitX)),
                LangUtils.placeholder("y", String.valueOf(exitY)),
                LangUtils.placeholder("z", String.valueOf(exitZ)),
                LangUtils.placeholder("yaw", String.valueOf(exitYAW)));
        return true;
    }

    /** Runs handle set dungeon enabled command. */
    private boolean handleSetDungeonEnabledCommand(
            CommandSender sender, String[] args, boolean enabled) {
        if (args.length < 3) {
            LangUtils.sendMessage(
                    sender,
                    enabled ? "commands.dungeon.enable.usage" : "commands.dungeon.disable.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    enabled
                            ? "commands.dungeon.enable.dungeon-not-found"
                            : "commands.dungeon.disable.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(sender, "edit", dungeon.getWorldName());
        if (dungeon.isEnabled() == enabled) {
            LangUtils.sendMessage(
                    sender,
                    enabled
                            ? "commands.dungeon.enable.already-enabled"
                            : "commands.dungeon.disable.already-disabled",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        nl.hauntedmc.dungeons.runtime.staff.StaffOperations.requireReason(reason);
        dungeon.setSaveConfig("dungeon.enabled", enabled);
        if (dungeon.getRuntime().staffOperations() != null)
            dungeon.getRuntime().staffOperations().audit(sender, enabled ? "enable" : "disable", dungeon.getWorldName(), reason, "updated");
        if (!enabled) {
            this.clearQueuedStartsForDisabledDungeon(dungeon);
            if (dungeon.getRuntime().staffOperations() != null)
                dungeon.getRuntime().staffOperations().cancelStarts(dungeon.getWorldName());
        }

        LangUtils.sendMessage(
                sender,
                enabled ? "commands.dungeon.enable.success" : "commands.dungeon.disable.success",
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        return true;
    }

    /** Runs clear queued starts for disabled dungeon. */
    private void clearQueuedStartsForDisabledDungeon(DungeonDefinition dungeon) {
        for (DungeonQueueEntry queue : this.queueManager.snapshotQueues()) {
            if (queue.getDungeon() != dungeon) {
                continue;
            }

            for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
                DungeonPlayerSession playerSession = this.playerManager.get(playerId);
                Player player = playerSession == null ? null : playerSession.getPlayer();
                if (player != null && player.isOnline()) {
                    LangUtils.sendMessage(
                            player,
                            "commands.play.disabled",
                            LangUtils.placeholder("dungeon", dungeon.getWorldName()));
                }
            }

            this.dungeonQueueCoordinator.discardQueue(queue);
        }
    }

    /** Runs handle remove allkeys command. */
    private boolean handleRemoveAllkeysCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.clear.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.clear.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(player, "keys", dungeon.getWorldName());

        int removedCount = dungeon.removeAllAccessKeys();
        LangUtils.sendMessage(
                player,
                "commands.dungeon.keys.clear.success",
                LangUtils.placeholder("count", String.valueOf(removedCount)));
        return true;
    }

    /** Runs handle remove key command. */
    private boolean handleRemoveKeyCommand(CommandSender sender, String[] args) {
        if (args.length != 2 && args.length != 3) {
            LangUtils.sendMessage(sender, "commands.dungeon.keys.remove.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.keys.remove.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(sender, "keys", dungeon.getWorldName());

        if (args.length == 3) {
            Optional<Integer> parsedKeyId = InputUtils.readIntegerInput(sender, args[2]);
            if (parsedKeyId.isEmpty()) {
                return true;
            }

            if (!dungeon.removeAccessKey(parsedKeyId.get())) {
                LangUtils.sendMessage(
                        sender,
                        "commands.dungeon.keys.remove.invalid-key",
                        LangUtils.placeholder("id", args[2]),
                        LangUtils.placeholder("dungeon", dungeon.getWorldName()));
                return true;
            }

            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.keys.remove.success",
                    LangUtils.placeholder("count", "1"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            LangUtils.sendMessage(sender, "commands.dungeon.keys.remove.no-held-item");
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-held-item");
            return true;
        }

        int removedCount = dungeon.removeAccessKey(heldItem);
        if (removedCount > 0) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.remove.success",
                    LangUtils.placeholder("count", String.valueOf(removedCount)));
        } else {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-key-found");
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-key-found-detail");
        }

        return true;
    }

    /** Runs handle add key command. */
    private boolean handleAddKeyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.add.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(player, "keys", dungeon.getWorldName());

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.no-held-item");
            return true;
        }

        AccessKeyDefinition addedKey = dungeon.addAccessKey(heldItem, player);
        ItemStack taggedKeyItem = dungeon.issueAccessKey(addedKey);
        int keyId = dungeon.getAccessKeys().indexOf(addedKey) + 1;
        if (taggedKeyItem == null || taggedKeyItem.getType() == Material.AIR) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.add.issue-failed",
                    LangUtils.placeholder("id", String.valueOf(keyId)));
            return true;
        }

        player.getInventory().setItemInMainHand(taggedKeyItem);
        player.updateInventory();
        LangUtils.sendMessage(
                player,
                "commands.dungeon.keys.add.success",
                LangUtils.placeholder("id", String.valueOf(keyId)));
        return true;
    }

    private boolean handleInvalidDungeonKeysUsage(CommandSender sender) {
        CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
        return true;
    }

    /** Runs handle dungeon list command. */
    private boolean handleDungeonListCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasAnyDungeonEditAccess(sender)) {
            return false;
        }

        FilterPageQuery query =
                this.output.parseFilterPageQuery(sender, args, 1, "commands.dungeon.list.usage");
        if (query == null) {
            return true;
        }

        List<DungeonDefinition> dungeons = this.output.getFilteredDungeons(query.filter(), true);
        List<String> lines = new ArrayList<>();
        for (DungeonDefinition dungeon : dungeons) {
            lines.add(
                    LangUtils.getMessage(
                            "commands.dungeon.list.line",
                            false,
                            LangUtils.placeholder("dungeon", dungeon.getWorldName()),
                            LangUtils.placeholder("display", dungeon.getDisplayName()),
                            LangUtils.placeholder("type", this.output.getDungeonTypeName(dungeon)),
                            LangUtils.placeholder(
                                    "instances", String.valueOf(dungeon.getInstances().size())),
                            LangUtils.placeholder(
                                    "team_size", String.valueOf(dungeon.getMaxTeamSize())),
                            LangUtils.placeholder(
                                    "difficulties",
                                    this.output.formatDungeonDifficulties(dungeon))));
        }

        this.output.sendPaginatedLines(
                sender,
                LangUtils.getMessage("commands.dungeon.list.header", false),
                lines,
                "commands.dungeon.list.no-match",
                "commands.dungeon.list.footer",
                query.page());
        return true;
    }

    /** Runs handle dungeon info command. */
    private boolean handleDungeonInfoCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasAnyDungeonEditAccess(sender)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(sender, "commands.dungeon.info.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.status.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        sender.sendMessage(
                LangUtils.getMessage(
                        "commands.dungeon.info.header",
                        false,
                        LangUtils.placeholder("dungeon", dungeon.getWorldName())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.display",
                LangUtils.placeholder("display", dungeon.getDisplayName()));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.type",
                LangUtils.placeholder("type", this.output.getDungeonTypeName(dungeon)));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.team-size",
                LangUtils.placeholder("team_size", String.valueOf(dungeon.getMaxTeamSize())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.instances",
                LangUtils.placeholder("count", String.valueOf(dungeon.getInstances().size())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.locations",
                LangUtils.placeholder(
                        "lobby", this.output.formatConfiguredState(dungeon.getLobbySpawn())),
                LangUtils.placeholder(
                        "start", this.output.formatConfiguredState(dungeon.getStartSpawn())),
                LangUtils.placeholder(
                        "exit", this.output.formatConfiguredState(dungeon.getExitLoc())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.difficulties",
                LangUtils.placeholder(
                        "difficulties", this.output.formatDungeonDifficulties(dungeon)));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.keys",
                LangUtils.placeholder("count", String.valueOf(dungeon.getValidKeys().size())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.enabled",
                LangUtils.placeholder(
                        "state", this.output.formatEnabledState(dungeon.isEnabled())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.access-cooldown",
                LangUtils.placeholder(
                        "state",
                        this.output.formatEnabledState(dungeon.isAccessCooldownEnabled())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.edit-session",
                LangUtils.placeholder(
                        "state", this.output.formatYesNoState(dungeon.getEditSession() != null)));
        return true;
    }

    /** Adds matching dungeon type names. */
    private void addMatchingDungeonTypeNames(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<Class<?>> found = new ArrayList<>();
        for (Entry<String, Class<? extends DungeonDefinition>> pair :
                DungeonTypeRegistry.getDungeonTypes().entrySet()) {
            String dungeonType = pair.getKey();
            if (!found.contains(pair.getValue())
                    && dungeonType.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                found.add(pair.getValue());
                options.add(dungeonType);
            }
        }
    }

}

package nl.hauntedmc.dungeons.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.staff.StaffEditGuard;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Owns editor commands, scoped authorization and editor completions. */
final class EditCommandHandler implements CommandHandler {
    private final DungeonsPlugin plugin;
    private final PlayerSessionRegistry playerManager;
    private final DungeonRepository dungeonManager;

    EditCommandHandler(
            DungeonsPlugin plugin,
            PlayerSessionRegistry playerManager,
            DungeonRepository dungeonManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.dungeonManager = dungeonManager;
    }

    /** Runs handle command. */
    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "close" -> this.handleEditCloseCommand(sender, args);
            case "save" -> this.handleSaveCommand(sender, args);
            case "lobby" -> this.handleLobbyCommand(sender, args);
            case "start" -> this.handleStartCommand(sender, args);
            case "exit" -> {
                if (!(sender instanceof Player player))
                    throw new IllegalArgumentException("Players only");
                var session = this.playerManager.get(player);
                if (!StaffEditGuard.editor(player, session))
                    throw new IllegalArgumentException("Open an authorized editor first");
                session.getInstance().getDungeon().setExit(player.getLocation());
                yield true;
            }
            case "items" -> this.handleItemsCommand(sender, args);
            case "tools" -> this.handleToolsCommand(sender, args);
            case "signs" -> this.handleSignsCommand(sender, args);
            default -> {
                CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
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
                    options, args[1], "close", "save", "lobby", "start", "items", "tools", "signs");
            DungeonCompletions.addMatchingEditableDungeonNames(
                    this.dungeonManager, sender, options, args[1]);
            return options;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "lobby", "start" -> {
                if (args.length == 3) {
                    CommandArguments.addMatchingLiteralOptions(options, args[2], "set");
                }
            }
            case "items" -> {
                if (args.length == 3) {
                    CommandArguments.addMatchingLiteralOptions(
                            options, args[2], "ban", "unban", "mark-dungeon");
                }
            }
            case "tools" -> {
                if (args.length == 3) {
                    CommandArguments.addMatchingLiteralOptions(
                            options, args[2], "function", "room");
                }
            }
            case "signs" -> {
                if (args.length == 3) {
                    CommandArguments.addMatchingLiteralOptions(options, args[2], "clean");
                }
            }
            default -> {
                return List.of();
            }
        }

        return options;
    }

    /** Runs handle save command. */
    private boolean handleSaveCommand(CommandSender sender, String[] args) {
        if (args.length != 2) {
            CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        return this.handleSaveCommand(sender);
    }

    /** Runs handle lobby command. */
    private boolean handleLobbyCommand(CommandSender sender, String[] args) {
        if (args.length == 3 && args[2].equalsIgnoreCase("set")) {
            return this.handleSetLobbyCommand(sender, new String[] {"setlobby"});
        }

        CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
        return true;
    }

    /** Runs handle start command. */
    private boolean handleStartCommand(CommandSender sender, String[] args) {
        if (args.length == 3 && args[2].equalsIgnoreCase("set")) {
            return this.handleSetSpawnCommand(sender, new String[] {"setspawn"});
        }

        CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
        return true;
    }

    /** Runs handle items command. */
    private boolean handleItemsCommand(CommandSender sender, String[] args) {
        if (args.length != 3) {
            CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "ban" -> this.handleBanItemCommand(sender, new String[] {"banitem"});
            case "unban" -> this.handleUnbanItemCommand(sender, new String[] {"unbanitem"});
            case "mark-dungeon" ->
                    this.handleDungeonItemCommand(sender, new String[] {"dungeonitem"});
            default -> {
                CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                yield true;
            }
        };
    }

    /** Runs handle tools command. */
    private boolean handleToolsCommand(CommandSender sender, String[] args) {
        if (args.length != 3) {
            CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "function" -> this.handleFunctionToolCommand(sender);
            case "room" -> this.handleRoomToolCommand(sender);
            default -> {
                CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                yield true;
            }
        };
    }

    /** Runs handle signs command. */
    private boolean handleSignsCommand(CommandSender sender, String[] args) {
        if (args.length == 3 && args[2].equalsIgnoreCase("clean")) {
            return this.handleCleanSignsCommand(sender, new String[] {"cleansigns"});
        }

        CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
        return true;
    }

    /** Runs handle clean signs command. */
    private boolean handleCleanSignsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.staff.edit")) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.signs.clean.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.signs.clean.not-in-dungeon");
            return true;
        }

        int cleanedSigns = ((EditableInstance) playerSession.getInstance()).cleanSigns();
        LangUtils.sendMessage(
                player,
                "commands.edit.signs.clean.success",
                LangUtils.placeholder("count", String.valueOf(cleanedSigns)));
        return true;
    }

    /** Runs handle save command. */
    private boolean handleSaveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession.getInstance();
        if (instance == null) {
            LangUtils.sendMessage(player, "commands.edit.save.not-in-dungeon");
            return true;
        }

        EditableInstance editable = instance.asEditInstance();
        if (editable == null) {
            LangUtils.sendMessage(player, "commands.edit.save.not-in-dungeon");
            return true;
        }

        if (!this.requireEditorPersistenceAccess(player, instance.getDungeon())) {
            return false;
        }

        if (instance.getDungeon().isSaving()) {
            LangUtils.sendMessage(player, "instance.lifecycle.is-saving");
            return true;
        }

        LangUtils.sendMessage(player, "commands.edit.save.saving");
        editable.saveWorldAsync()
                .whenComplete(
                        (success, throwable) ->
                                Bukkit.getScheduler()
                                        .runTask(
                                                this.plugin,
                                                () -> {
                                                    if (!player.isOnline()) {
                                                        return;
                                                    }

                                                    if (throwable != null
                                                            || !Boolean.TRUE.equals(success)) {
                                                        LangUtils.sendMessage(
                                                                player,
                                                                "commands.edit.save.failed",
                                                                LangUtils.placeholder(
                                                                        "dungeon",
                                                                        instance.getDungeon()
                                                                                .getWorldName()));
                                                        return;
                                                    }

                                                    LangUtils.sendMessage(
                                                            player,
                                                            "commands.edit.save.success",
                                                            LangUtils.placeholder(
                                                                    "dungeon",
                                                                    instance.getDungeon()
                                                                            .getWorldName()));
                                                }));
        return true;
    }

    /** Runs handle function tool command. */
    private boolean handleFunctionToolCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.staff.edit")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || !playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.tools.function.not-in-dungeon");
            return true;
        }

        playerSession.restoreEditorTools();
        int functionSlot = PluginConfigView.getFunctionToolSlot(this.plugin.getConfig());
        player.getInventory().setHeldItemSlot(functionSlot);
        LangUtils.sendMessage(
                player,
                "commands.edit.tools.function.restored",
                LangUtils.placeholder("slot", String.valueOf(functionSlot + 1)));
        return true;
    }

    /** Runs handle room tool command. */
    private boolean handleRoomToolCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.staff.edit")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || !playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.tools.room.not-in-dungeon");
            return true;
        }

        if (playerSession.getInstance().as(BranchingEditableInstance.class) == null) {
            LangUtils.sendMessage(player, "commands.edit.tools.room.not-in-branching");
            return true;
        }

        playerSession.restoreEditorTools();
        int roomSlot = PluginConfigView.getRoomToolSlot(this.plugin.getConfig());
        player.getInventory().setHeldItemSlot(roomSlot);
        LangUtils.sendMessage(
                player,
                "commands.edit.tools.room.restored",
                LangUtils.placeholder("slot", String.valueOf(roomSlot + 1)));
        return true;
    }

    /** Runs handle dungeon item command. */
    private boolean handleDungeonItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.items.mark-dungeon.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.items.mark-dungeon.not-in-dungeon");
            return true;
        }

        if (!this.requireDungeonEditAccess(player, playerSession.getInstance().getDungeon())) {
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.no-held-item");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(this.plugin, "DungeonItem"), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        LangUtils.sendMessage(player, "commands.edit.items.mark-dungeon.success");
        return true;
    }

    /** Runs handle unban item command. */
    private boolean handleUnbanItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.items.unban.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.items.unban.not-in-dungeon");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        boolean unbanSuccess = dungeon.unbanItem(player.getInventory().getItemInMainHand());
        if (unbanSuccess) {
            LangUtils.sendMessage(player, "commands.edit.items.unban.success");
        } else {
            LangUtils.sendMessage(player, "commands.edit.items.unban.not-banned");
        }

        return true;
    }

    /** Runs handle ban item command. */
    private boolean handleBanItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.items.ban.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.items.ban.not-in-dungeon");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        dungeon.banItem(player.getInventory().getItemInMainHand());
        LangUtils.sendMessage(player, "commands.edit.items.ban.success");
        return true;
    }

    /** Runs handle set spawn command. */
    private boolean handleSetSpawnCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.start.set.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.start.set.not-in-dungeon");
            LangUtils.sendMessage(player, "commands.edit.start.set.not-editing");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        Location spawnLoc = player.getLocation();
        dungeon.setStartSpawn(spawnLoc);
        double spawnX = MathUtils.round(spawnLoc.getX(), 2);
        double spawnY = MathUtils.round(spawnLoc.getY(), 2);
        double spawnZ = MathUtils.round(spawnLoc.getZ(), 2);
        double spawnYAW = MathUtils.round(spawnLoc.getYaw(), 2);
        LangUtils.sendMessage(
                player,
                "commands.edit.start.set.success",
                LangUtils.placeholder("x", String.valueOf(spawnX)),
                LangUtils.placeholder("y", String.valueOf(spawnY)),
                LangUtils.placeholder("z", String.valueOf(spawnZ)),
                LangUtils.placeholder("yaw", String.valueOf(spawnYAW)));
        return true;
    }

    /** Runs handle set lobby command. */
    private boolean handleSetLobbyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.lobby.set.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.lobby.set.not-in-dungeon");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        Location lobbyLocation = player.getLocation();
        dungeon.setLobbySpawn(lobbyLocation);
        double lobbyX = MathUtils.round(lobbyLocation.getX(), 2);
        double lobbyY = MathUtils.round(lobbyLocation.getY(), 2);
        double lobbyZ = MathUtils.round(lobbyLocation.getZ(), 2);
        double lobbyYAW = MathUtils.round(lobbyLocation.getYaw(), 2);
        LangUtils.sendMessage(
                player,
                "commands.edit.lobby.set.success",
                LangUtils.placeholder("x", String.valueOf(lobbyX)),
                LangUtils.placeholder("y", String.valueOf(lobbyY)),
                LangUtils.placeholder("z", String.valueOf(lobbyZ)),
                LangUtils.placeholder("yaw", String.valueOf(lobbyYAW)));
        return true;
    }

    /** Runs handle edit close command. */
    private boolean handleEditCloseCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.edit.close.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null) {
            LangUtils.sendMessage(player, "commands.edit.close.not-in-editor");
            return true;
        }

        DungeonInstance instance = playerSession.getInstance();
        if (instance == null || instance.asEditInstance() == null || !playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.close.not-in-editor");
            return true;
        }

        if (!this.requireEditorPersistenceAccess(player, instance.getDungeon())) {
            return false;
        }

        var dungeonName = instance.getDungeon().getDisplayNameComponent();
        instance.removePlayer(playerSession);
        LangUtils.sendMessage(
                player,
                "commands.edit.close.success",
                LangUtils.placeholder("dungeon", dungeonName));
        return true;
    }

    private boolean requireEditorPersistenceAccess(Player player, DungeonDefinition dungeon) {
        if (dungeon.getRuntime().staffOperations() != null
                && dungeon.getRuntime().staffOperations().contentBusy(dungeon.getWorldName())) {
            if (!CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName()))
                throw new IllegalArgumentException("Missing editor permission");
            if (dungeon.getEditSession() == null
                    || this.playerManager.get(player) == null
                    || this.playerManager.get(player).getInstance() != dungeon.getEditSession())
                throw new IllegalArgumentException("Editor ownership has changed");
            return true;
        }
        return requireDungeonEditAccess(player, dungeon);
    }

    private boolean requireDungeonEditAccess(Player player, DungeonDefinition dungeon) {
        if (dungeon != null && CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            StaffEditGuard.configuration(player, dungeon, "editor");
            return true;
        }
        LangUtils.sendMessage(player, "general.errors.no-permission");
        return false;
    }
}

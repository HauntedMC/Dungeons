package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Owns the debug command branch and its completions. */
final class DebugCommandHandler implements CommandHandler {
    private final PlayerSessionRegistry playerManager;

    DebugCommandHandler(PlayerSessionRegistry playerManager) {
        this.playerManager = playerManager;
    }

    /** Runs handle command. */
    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("map"))
            return handleMapCommand(sender, new String[] {"map"});
        if (args.length < 2 || !args[1].equalsIgnoreCase("doors")) {
            CommandArguments.sendUsageList(sender, "commands.debug.help.lines");
            return true;
        }

        if (args.length == 3 && args[2].equalsIgnoreCase("test-all")) {
            return this.handleTestAllDoorsCommand(sender, new String[] {"testalldoors"});
        }

        if (args.length == 4 && args[2].equalsIgnoreCase("test")) {
            return this.handleTestDoorCommand(sender, new String[] {"testdoor", args[3]});
        }

        CommandArguments.sendUsageList(sender, "commands.debug.help.lines");
        return true;
    }

    /** Runs handle tab complete. */
    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            CommandArguments.addMatchingLiteralOptions(options, args[1], "doors", "map");
            return options;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("doors")) {
            CommandArguments.addMatchingLiteralOptions(options, args[2], "test", "test-all");
        }

        return options;
    }

    private boolean canDiagnose(CommandSender sender) {
        if (!(sender instanceof Player player)) return false;
        var session = this.playerManager.get(player);
        var dungeon = session == null || session.getInstance() == null ? null : session.getInstance().getDungeon();
        return dungeon != null && StaffPermissions.allows(sender, "diagnostics", dungeon.getWorldName());
    }

    /** Runs handle test door command. */
    boolean handleTestDoorCommand(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!this.canDiagnose(sender)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession == null ? null : playerSession.getInstance();
        if (instance != null) {
            BranchingInstance branchingInstance = instance.as(BranchingInstance.class);
            if (branchingInstance != null) {
                InstanceRoom room = branchingInstance.getRoom(player.getLocation());
                if (room != null) {
                    room.toggleDoor(args[1]);
                }
            }
        }

        return true;
    }

    /** Runs handle test all doors command. */
    boolean handleTestAllDoorsCommand(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!this.canDiagnose(sender)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession == null ? null : playerSession.getInstance();
        if (instance != null) {
            BranchingInstance branchingInstance = instance.as(BranchingInstance.class);
            if (branchingInstance != null) {
                InstanceRoom room = branchingInstance.getRoom(player.getLocation());
                if (room != null) {
                    room.toggleValidDoors(true);
                }
            }
        }

        return true;
    }

    /** Runs handle map command. */
    boolean handleMapCommand(CommandSender sender, String[] args) {
        if (args.length != 1) {
            if (sender instanceof Player player) {
                LangUtils.sendMessage(player, "commands.debug.map.usage");
                return true;
            }

            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!this.canDiagnose(sender)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null) {
            LangUtils.sendMessage(player, "commands.debug.map.not-in-dungeon");
            return true;
        }

        DungeonInstance abs = playerSession.getInstance();
        if (abs == null) {
            LangUtils.sendMessage(player, "commands.debug.map.not-in-dungeon");
            return true;
        }

        PlayableInstance instance = abs.as(PlayableInstance.class);
        if (instance == null) {
            LangUtils.sendMessage(player, "commands.debug.map.not-in-dungeon");
            return true;
        }

        instance.giveDungeonMap(player);
        LangUtils.sendMessage(player, "commands.debug.map.success");
        return true;
    }
}

package nl.hauntedmc.dungeons.command;

import java.util.List;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Runs diagnostics inside a private testplay run. */
final class DebugCommandHandler implements CommandHandler {
    private final PlayerSessionRegistry playerManager;

    DebugCommandHandler(PlayerSessionRegistry playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return false;
        var run = testRun(player);
        if (run == null)
            throw new IllegalArgumentException(
                    "Start a test run with /dungeon edit testplay <map> first");
        StaffPermissions.require(player, "diagnostics", run.getDungeon().getWorldName());
        if (args[1].equals("map")) {
            run.giveDungeonMap(player);
            LangUtils.sendMessage(player, "commands.debug.map.success");
            return true;
        }
        var room = room(player, run);
        if (room == null)
            throw new IllegalArgumentException("Stand inside a room in a branching test run");
        if (args[2].equals("test-all")) {
            room.toggleValidDoors(true);
        } else {
            if (!room.getDoors().containsKey(args[3]))
                throw new IllegalArgumentException("Unknown door in this room: " + args[3]);
            room.toggleDoor(args[3]);
        }
        return true;
    }

    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        var run = testRun(player);
        if (run == null
                || !StaffPermissions.allows(player, "diagnostics", run.getDungeon().getWorldName()))
            return List.of();
        var room = room(player, run);
        return room == null ? List.of() : List.copyOf(room.getDoors().keySet());
    }

    private PlayableInstance testRun(Player player) {
        var session = this.playerManager.get(player);
        return session != null
                        && session.getInstance() instanceof PlayableInstance run
                        && run.isTestPlay()
                ? run
                : null;
    }

    private InstanceRoom room(Player player, PlayableInstance run) {
        return run instanceof BranchingInstance branching
                ? branching.getRoom(player.getLocation())
                : null;
    }
}

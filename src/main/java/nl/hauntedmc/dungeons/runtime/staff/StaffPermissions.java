package nl.hauntedmc.dungeons.runtime.staff;

import org.bukkit.command.CommandSender;

/** Capability checks shared by commands, menus and editor callbacks. */
public final class StaffPermissions {
    private StaffPermissions() {}

    public static boolean allows(CommandSender actor, String capability, String dungeon) {
        String node = "dungeons.staff." + capability;
        if (actor.isPermissionSet(node) && !actor.hasPermission(node)) return false;
        if (dungeon != null
                && actor.isPermissionSet(node + "." + dungeon)
                && !actor.hasPermission(node + "." + dungeon)) return false;
        if (actor.hasPermission(node)
                || dungeon != null && actor.hasPermission(node + "." + dungeon)) return true;
        return false;
    }

    public static void require(CommandSender actor, String capability, String dungeon) {
        if (!allows(actor, capability, dungeon))
            throw new IllegalArgumentException(
                    "Missing permission: dungeons.staff."
                            + capability
                            + (dungeon == null ? "" : "." + dungeon));
    }
}

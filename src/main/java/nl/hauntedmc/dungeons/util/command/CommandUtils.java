package nl.hauntedmc.dungeons.util.command;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.command.CommandSender;

/** Command permission helpers. */
public final class CommandUtils {

    /** Utility class. */
    private CommandUtils() {}

    /** Checks permission and sends a localized no-permission message when denied. */
    public static boolean hasPermission(CommandSender sender, String node) {
        if (!hasPermissionSilent(sender, node)) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return false;
        }
        return true;
    }

    /** Checks permission without sending feedback messages. */
    public static boolean hasPermissionSilent(CommandSender sender, String node) {
        return sender.hasPermission(node);
    }

    /** Returns whether sender can edit a specific dungeon by name. */
    public static boolean hasDungeonEditAccess(CommandSender sender, String dungeonName) {
        return StaffPermissions.allows(
                sender, "edit", dungeonName);
    }

    /** Returns whether sender has any dungeon-edit access across loaded dungeons. */
    public static boolean hasAnyDungeonEditAccess(CommandSender sender) {
        if (StaffPermissions.allows(sender, "edit", null)) {
            return true;
        }

        try {
            for (DungeonDefinition dungeon : RuntimeContext.dungeonCatalog().getLoadedDungeons()) {
                if (hasDungeonEditAccess(sender, dungeon.getWorldName())) {
                    return true;
                }
            }
        } catch (IllegalStateException ignored) {
            return false;
        }

        return false;
    }

}

package nl.hauntedmc.dungeons.util.command;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.command.CommandSender;

/** Command permission and help-menu utility helpers. */
public final class CommandUtils {

    /** Utility class. */
    private CommandUtils() {}

    /** Checks permission and sends a localized no-permission message when denied. */
    public static boolean hasPermission(CommandSender sender, String node) {
        if (!sender.hasPermission(node)) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return false;
        } else {
            return true;
        }
    }

    /** Checks permission without sending feedback messages. */
    public static boolean hasPermissionSilent(CommandSender sender, String node) {
        return sender.hasPermission(node);
    }

    /** Returns whether sender can edit a specific dungeon by name. */
    public static boolean hasDungeonEditAccess(CommandSender sender, String dungeonName) {
        return hasPermissionSilent(sender, "dungeons.edit")
                || hasPermissionSilent(sender, "dungeons.edit.*")
                || (dungeonName != null
                        && !dungeonName.isBlank()
                        && hasPermissionSilent(sender, "dungeons.edit." + dungeonName));
    }

    /** Returns whether sender has any dungeon-edit access across loaded dungeons. */
    public static boolean hasAnyDungeonEditAccess(CommandSender sender) {
        if (hasPermissionSilent(sender, "dungeons.edit")
                || hasPermissionSilent(sender, "dungeons.edit.*")) {
            return true;
        }

        try {
            for (DungeonDefinition dungeon : RuntimeContext.dungeonCatalog().getLoadedDungeons()) {
                if (hasPermissionSilent(sender, "dungeons.edit." + dungeon.getWorldName())) {
                    return true;
                }
            }
        } catch (IllegalStateException ignored) {
            return false;
        }

        return false;
    }

    /** Renders paginated help lines based on sender permissions and sender type. */
    public static void displayHelpMenu(CommandSender sender, int page) {
        if (page > 0) {
            page--;
        }

        sender.sendMessage(
                LangUtils.getMessage(
                        "commands.help.header",
                        false,
                        LangUtils.placeholder("version", RuntimeContext.version())));
        List<String> helpInfo = new ArrayList<>();
        boolean playerSender = sender instanceof org.bukkit.entity.Player;
        boolean dungeonEditPerm = hasAnyDungeonEditAccess(sender);
        boolean dungeonAdminPerm = hasPermissionSilent(sender, "dungeons.admin");
        boolean playPerm = hasPermissionSilent(sender, "dungeons.play");
        boolean playSendPerm = hasPermissionSilent(sender, "dungeons.play.send");

        helpInfo.add(LangUtils.getMessage("commands.help.help", false));
        if (dungeonAdminPerm
                || (playerSender && (playPerm || playSendPerm))
                || (!playerSender && playSendPerm)) {
            helpInfo.add(LangUtils.getMessage("commands.help.list", false));
        }
        if ((playerSender && (playPerm || playSendPerm)) || (!playerSender && playSendPerm)) {
            helpInfo.add(LangUtils.getMessage("commands.help.play", false));
        }
        if (playerSender && playPerm) {
            helpInfo.add(LangUtils.getMessage("commands.help.team", false));
        }
        if (playerSender) {
            helpInfo.add(LangUtils.getMessage("commands.help.leave", false));
        }
        if (playerSender && hasPermissionSilent(sender, "dungeons.stuck")) {
            helpInfo.add(LangUtils.getMessage("commands.help.stuck", false));
        }
        if (playerSender && hasPermissionSilent(sender, "dungeons.lives")) {
            helpInfo.add(LangUtils.getMessage("commands.help.lives", false));
        }
        if (playerSender && hasPermissionSilent(sender, "dungeons.rewards")) {
            helpInfo.add(LangUtils.getMessage("commands.help.rewards", false));
        }
        if (dungeonEditPerm) {
            helpInfo.add(LangUtils.getMessage("commands.help.config", false));
            if (playerSender) {
                helpInfo.add(LangUtils.getMessage("commands.help.edit", false));
            }
        }
        if (playerSender && hasPermissionSilent(sender, "dungeons.loottables")) {
            helpInfo.add(LangUtils.getMessage("commands.help.loot", false));
        }
        if (dungeonAdminPerm) {
            helpInfo.add(LangUtils.getMessage("commands.help.dungeon", false));
            helpInfo.add(LangUtils.getMessage("commands.help.player", false));
            if (playerSender) {
                helpInfo.add(LangUtils.getMessage("commands.help.instance", false));
                helpInfo.add(LangUtils.getMessage("commands.help.debug", false));
            }
        }

        int totalPages = (int) Math.ceil(helpInfo.size() / 10.0);
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        if (page < 0) {
            page = 0;
        }

        for (int i = page * 10; i < (page + 1) * 10 && i < helpInfo.size(); i++) {
            sender.sendMessage(helpInfo.get(i));
        }

        sender.sendMessage(
                LangUtils.getMessage(
                        "commands.help.footer",
                        false,
                        LangUtils.placeholder("page", String.valueOf(page + 1)),
                        LangUtils.placeholder("total_pages", String.valueOf(totalPages))));
    }
}

package nl.hauntedmc.dungeons.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Owns the team command branch and its completions. */
final class TeamCommandHandler implements CommandHandler {
    private final DungeonTeamService teamManager;

    TeamCommandHandler(DungeonTeamService teamManager) {
        this.teamManager = teamManager;
    }

    /** Runs handle command. */
    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        return this.handleTeamCommand(sender, args);
    }

    /** Runs handle tab complete. */
    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            CommandArguments.addMatchingLiteralOptions(
                    options, args[1], "create", "invite", "kick", "revoke", "accept", "deny",
                    "leave", "delete", "info");
            return options;
        }

        if (args.length == 3
                && (args[1].equalsIgnoreCase("invite")
                        || args[1].equalsIgnoreCase("kick")
                        || args[1].equalsIgnoreCase("revoke"))) {
            CommandArguments.addMatchingPlayers(options, args[2]);
        }

        return options;
    }

    /** Runs handle team command. */
    private boolean handleTeamCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.play")) {
            return false;
        }

        if (args.length < 2) {
            LangUtils.sendMessageList(player, "commands.team.help.lines");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> this.teamManager.handleCreateTeam(player);
            case "invite" -> this.handleTeamInviteCommand(player, args);
            case "kick" -> this.handleTeamKickCommand(player, args);
            case "revoke" -> this.handleTeamRevokeCommand(player, args);
            case "accept" -> this.teamManager.handleAcceptInvite(player);
            case "deny" -> this.teamManager.handleDenyInvite(player);
            case "leave" -> this.teamManager.handleLeaveTeam(player);
            case "delete" -> this.teamManager.handleDeleteTeam(player);
            case "info" -> this.teamManager.handleInfo(player);
            default -> {
                LangUtils.sendMessage(player, "commands.team.unknown-subcommand");
                yield true;
            }
        };
    }

    /** Runs handle team invite command. */
    private boolean handleTeamInviteCommand(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.team.invite.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.player-not-found",
                    LangUtils.placeholder("player", args[2]));
            return true;
        }

        return this.teamManager.handleInvite(player, target);
    }

    /** Runs handle team kick command. */
    private boolean handleTeamKickCommand(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.team.kick.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.player-not-found",
                    LangUtils.placeholder("player", args[2]));
            return true;
        }

        return this.teamManager.handleKickMember(player, target);
    }

    /** Runs handle team revoke command. */
    private boolean handleTeamRevokeCommand(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.team.revoke.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.player-not-found",
                    LangUtils.placeholder("player", args[2]));
            return true;
        }

        return this.teamManager.handleRevokeInvite(player, target);
    }
}

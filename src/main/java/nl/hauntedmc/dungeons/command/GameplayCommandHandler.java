package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeam;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owns player run commands and play argument completion. */
final class GameplayCommandHandler implements CommandHandler {
    private final PlayerSessionRegistry playerManager;
    private final DungeonRepository dungeonManager;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final DungeonQueueCoordinator dungeonQueueCoordinator;
    private final DungeonQueueRegistry queueManager;
    private final DungeonTeamService teamManager;
    private final GuiService guiService;

    GameplayCommandHandler(PlayerSessionRegistry playerManager, DungeonRepository dungeonManager,
            ActiveInstanceRegistry activeInstanceManager, DungeonQueueCoordinator dungeonQueueCoordinator,
            DungeonQueueRegistry queueManager, DungeonTeamService teamManager, GuiService guiService) {
        this.playerManager = playerManager;
        this.dungeonManager = dungeonManager;
        this.activeInstanceManager = activeInstanceManager;
        this.dungeonQueueCoordinator = dungeonQueueCoordinator;
        this.queueManager = queueManager;
        this.teamManager = teamManager;
        this.guiService = guiService;
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "play" -> this.handlePlayCommand(sender, args);
            case "leave" -> this.handleLeaveCommand(sender);
            case "lives" -> this.handleLivesCommand(sender);
            case "stuck" -> this.handleStuckCommand(sender, args);
            case "rewards" -> this.handleRewardsCommand(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        return args[0].equalsIgnoreCase("play") ? this.completePlayCommand(sender, args) : List.of();
    }

    /** Runs handle rewards command. */
    private boolean handleRewardsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.rewards")) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.rewards.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || playerSession.getRewardItems().isEmpty()) {
            LangUtils.sendMessage(player, "commands.rewards.empty");
            return true;
        }

        this.guiService.openGui(player, "rewards");
        return true;
    }

    /** Runs handle leave command. */
    private boolean handleLeaveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession != null && playerSession.isObserving()) {
            playerSession.stopObservation();
            player.sendMessage("[Dungeons] Observation ended");
            return true;
        }
        if (playerSession.getInstance() == null) {
            DungeonQueueEntry queue = this.queueManager.getQueue(playerSession);
            if (queue != null) {
                DungeonTeam team = this.teamManager.getTeam(player.getUniqueId());
                if (queue.isTeamQueue() && team != null) {
                    if (team.isLeader(player.getUniqueId())) {
                        this.queueManager.unqueue(queue);
                        this.teamManager.clearQueued(team);
                    } else {
                        this.queueManager.removePlayer(player.getUniqueId());
                    }
                } else {
                    this.queueManager.unqueue(playerSession);
                }

                LangUtils.sendMessage(player, "commands.leave.left-queue");
                return true;
            }

            DungeonTeam team = this.teamManager.getTeam(player.getUniqueId());
            if (team != null) {
                LangUtils.sendMessage(player, "commands.leave.not-in-dungeon");
                return true;
            }

            DungeonInstance instance =
                    this.activeInstanceManager.getDungeonInstance(player.getWorld().getName());
            if (instance != null) {
                if (playerSession.getSavedPosition() != null) {
                    EntityUtils.forceTeleport(player, playerSession.getSavedPosition());
                } else {
                    EntityUtils.forceTeleport(player, player.getRespawnLocation());
                }
            }

            LangUtils.sendMessage(player, "commands.leave.not-in-dungeon");
            return true;
        }

        String dungeonName = playerSession.getInstance().getDungeon().getDisplayName();
        playerSession.getInstance().removePlayer(playerSession);
        LangUtils.sendMessage(
                player,
                "commands.leave.left-dungeon",
                LangUtils.placeholder("dungeon", dungeonName));
        return true;
    }

    /** Runs handle lives command. */
    private boolean handleLivesCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.lives")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession.getInstance();
        if (instance == null) {
            LangUtils.sendMessage(player, "commands.lives.not-in-dungeon");
            return true;
        }

        PlayableInstance playable = instance.asPlayInstance();
        if (playable != null) {
            Integer livesRemaining = playable.getPlayerLives().get(player.getUniqueId());
            if (livesRemaining == null) {
                LangUtils.sendMessage(player, "commands.lives.infinite-lives");
            } else {
                LangUtils.sendMessage(
                        player,
                        "commands.lives.lives-remaining",
                        LangUtils.placeholder("lives", String.valueOf(livesRemaining)));
            }
        }

        return true;
    }

    /** Runs handle stuck command. */
    private boolean handleStuckCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.stuck")) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.stuck.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession.getInstance() == null) {
            LangUtils.sendMessage(player, "commands.stuck.not-in-dungeon");
            return true;
        }

        LangUtils.sendMessage(player, "commands.stuck.success");
        playerSession.sendToCheckpoint();
        return true;
    }

    /** Runs handle play command. */
    private boolean handlePlayCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.play")
                && !CommandUtils.hasPermission(sender, "dungeons.play.send")) {
            return true;
        }

        if (args.length < 2) {
            LangUtils.sendMessage(sender, "commands.play.usage");
            return true;
        }

        Player targetPlayer = null;

        if (args.length == 2) {
            if (!CommandUtils.hasPermission(sender, "dungeons.play")) {
                return true;
            }

            if (!(sender instanceof Player player)) {
                LangUtils.sendMessage(sender, "commands.play.console-needs-player");
                return true;
            }

            targetPlayer = player;
        } else if (args.length == 3) {
            if (!CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")) {
                return true;
            }

            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                LangUtils.sendMessage(
                        sender,
                        "commands.play.player-not-found",
                        LangUtils.placeholder("player", args[2]));
                return true;
            }
        }

        if (targetPlayer == null) {
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(targetPlayer);
        if (playerSession.isAwaitingDungeon()) {
            LangUtils.sendMessage(sender, "commands.play.already-in-queue");
            return true;
        }

        if (playerSession.getInstance() != null) {
            LangUtils.sendMessage(sender, "commands.play.already-in-dungeon");
            return true;
        }

        if (this.queueManager.getQueue(playerSession) != null) {
            LangUtils.sendMessage(sender, "commands.play.already-in-queue");
            return true;
        }

        String dungeonName = args[1];
        String difficulty = "";
        if (dungeonName.contains(":")) {
            String[] split = dungeonName.split(":", 2);
            dungeonName = split[0];
            difficulty = split[1];
        }

        DungeonDefinition dungeon = this.dungeonManager.get(dungeonName);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.play.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!dungeon.isEnabled()) {
            LangUtils.sendMessage(
                    sender,
                    "commands.play.disabled",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            if (!sender.equals(targetPlayer)) {
                LangUtils.sendMessage(
                        targetPlayer,
                        "commands.play.disabled",
                        LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            }
            return true;
        }

        boolean useDifficulty = dungeon.isUseDifficultyLevels();
        boolean showMenu = dungeon.isShowDifficultyMenu();
        if (useDifficulty) {
            if (difficulty.isEmpty()) {
                if (showMenu) {
                    this.guiService.openGui(targetPlayer, "difficulty_" + dungeon.getWorldName());
                    return true;
                }
            } else if (!CommandUtils.hasPermission(sender, "dungeons.play.difficulty")) {
                return true;
            }
        }

        this.dungeonQueueCoordinator.sendToDungeon(targetPlayer, dungeonName, difficulty);
        return true;
    }

    /** Runs complete play command. */
    List<String> completePlayCommand(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            String query = args[1];
            if (query.contains(":")) {
                if (CommandUtils.hasPermissionSilent(sender, "dungeons.play.difficulty")) {
                    DungeonCompletions.addMatchingPlayDifficultyOptions(this.dungeonManager, options, query);
                }
            } else {
                DungeonCompletions.addMatchingDungeonNames(this.dungeonManager, options, query);
                if (CommandUtils.hasPermissionSilent(sender, "dungeons.play.difficulty")) {
                    DungeonCompletions.addMatchingPlayDifficultyOptions(this.dungeonManager, options, query);
                }
            }

            return options;
        }

        if (args.length == 3 && CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")) {
            CommandArguments.addMatchingPlayers(options, args[2]);
        }

        return options;
    }
}

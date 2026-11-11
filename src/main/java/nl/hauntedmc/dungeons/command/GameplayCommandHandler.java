package nl.hauntedmc.dungeons.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.staff.StaffOperations;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeam;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Owns player run commands and play argument completion. */
final class GameplayCommandHandler implements CommandHandler {
    private final PlayerSessionRegistry playerManager;
    private final DungeonRepository dungeonManager;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final DungeonQueueCoordinator dungeonQueueCoordinator;
    private final DungeonQueueRegistry queueManager;
    private final DungeonTeamService teamManager;
    private final GuiService guiService;

    GameplayCommandHandler(
            PlayerSessionRegistry playerManager,
            DungeonRepository dungeonManager,
            ActiveInstanceRegistry activeInstanceManager,
            DungeonQueueCoordinator dungeonQueueCoordinator,
            DungeonQueueRegistry queueManager,
            DungeonTeamService teamManager,
            GuiService guiService) {
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
            case "send" -> this.handleSendCommand(sender, args);
            case "leave" -> this.handleLeaveCommand(sender);
            case "lives" -> this.handleLivesCommand(sender);
            case "stuck" -> this.handleStuckCommand(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        return args[0].equalsIgnoreCase("play")
                ? this.completePlayCommand(sender, args)
                : List.of();
    }

    /** Runs handle leave command. */
    private boolean handleLeaveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession != null && playerSession.isObserving()) {
            playerSession.stopObservation();
            LangUtils.sendRaw(player, "Observation ended");
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

        var dungeonName = playerSession.getInstance().getDungeon().getDisplayNameComponent();
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
        if (!(sender instanceof Player targetPlayer)) {
            throw new IllegalArgumentException(
                    "Use /dungeon player <player> send <dungeon> for another player");
        }
        if (!CommandUtils.hasPermission(sender, "dungeons.play")) return true;
        if (args.length < 2 || args.length > 3 || args[1].contains(":")) {
            throw new IllegalArgumentException("Usage: /dungeon play <dungeon> [difficulty]");
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
        String difficulty = args.length == 3 ? args[2] : "";

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
        if (!difficulty.isEmpty()
                && (!useDifficulty || !dungeon.getDifficultyLevels().containsKey(difficulty))) {
            throw new IllegalArgumentException(
                    "Unknown difficulty for " + dungeonName + ": " + difficulty);
        }
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

    private boolean handleSendCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.play.send")) return true;
        if (args.length < 4)
            throw new IllegalArgumentException("A send operation requires a reason");
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null)
            throw new IllegalArgumentException("Player must be online on this server");
        var dungeon = this.dungeonManager.get(args[2]);
        if (dungeon == null || !dungeon.isEnabled())
            throw new IllegalArgumentException("Dungeon unavailable");
        String difficulty = "";
        int reasonStart = 3;
        if (args[3].equals("--difficulty")) {
            if (args.length < 6)
                throw new IllegalArgumentException("Difficulty and reason are required");
            difficulty = args[4];
            reasonStart = 5;
            if (!dungeon.isUseDifficultyLevels()
                    || !dungeon.getDifficultyLevels().containsKey(difficulty))
                throw new IllegalArgumentException("Unknown difficulty");
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length));
        StaffOperations.requireReason(reason);
        var session = this.playerManager.get(target);
        if (session == null
                || session.isAwaitingDungeon()
                || session.getInstance() != null
                || session.isObserving()
                || this.queueManager.getQueue(session) != null)
            throw new IllegalArgumentException("Player already has an active dungeon operation");
        this.dungeonQueueCoordinator.sendToDungeon(target, dungeon.getWorldName(), difficulty);
        if (dungeon.getRuntime().staffOperations() != null)
            dungeon.getRuntime()
                    .staffOperations()
                    .audit(
                            sender,
                            "send",
                            target.getUniqueId().toString(),
                            reason,
                            "admission requested");
        return true;
    }

    /** Runs complete play command. */
    List<String> completePlayCommand(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2)
            DungeonCompletions.addMatchingDungeonNames(this.dungeonManager, options, args[1]);
        if (args.length == 3) {
            var dungeon = this.dungeonManager.get(args[1]);
            if (dungeon != null)
                dungeon.getDifficultyLevels().keySet().stream()
                        .filter(value -> value.startsWith(args[2]))
                        .forEach(options::add);
        }
        return options;
    }
}

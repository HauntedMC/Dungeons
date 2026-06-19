package nl.hauntedmc.dungeons.content.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that dispatches a configured command from dungeon content.
 *
 * <p>The command can be run with different execution contexts and can optionally be expanded once
 * per target player.
 */
@AutoRegister(id = "dungeons.function.command")
@SerializableAs("dungeons.function.command")
public class CommandFunction extends DungeonFunction {
    private static final int PLAYER_COMMAND_TYPE = 1;
    private static final int CONSOLE_COMMAND_TYPE = 2;

    @PersistedField private String command = "";
    @PersistedField private int commandType = PLAYER_COMMAND_TYPE;
    @PersistedField private boolean forEachPlayer = false;

    /**
     * Creates a new CommandFunction instance.
     */
    public CommandFunction(Map<String, Object> config) {
        super("Command", config);
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Creates a new CommandFunction instance.
     */
    public CommandFunction() {
        super("Command");
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        this.normalizeCommandType();

        String commandToRunTemplate = this.sanitizeCommand(this.command);
        if (commandToRunTemplate.isEmpty()) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Command function in dungeon '{}' has an empty command at {},{},{}.",
                            this.instance.getDungeon().getWorldName(),
                            this.location.getBlockX(),
                            this.location.getBlockY(),
                            this.location.getBlockZ());
            return;
        }

        List<Player> players = this.resolvePlayers(triggerEvent, targets);
        if (this.commandType == PLAYER_COMMAND_TYPE && players.isEmpty()) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Command function in dungeon '{}' at {},{},{} was skipped because no valid player context was available.",
                            this.instance.getDungeon().getWorldName(),
                            this.location.getBlockX(),
                            this.location.getBlockY(),
                            this.location.getBlockZ());
            return;
        }

        if (this.commandType == CONSOLE_COMMAND_TYPE) {
            this.runConsoleCommand(commandToRunTemplate, players);
            return;
        }

        for (Player player : players) {
            String commandToRun = this.resolveCommand(commandToRunTemplate, player);

            if (commandToRun.isEmpty()) {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .warn(
                                "Command function in dungeon '{}' resolved to an empty player command for '{}'.",
                                this.instance.getDungeon().getWorldName(),
                                player.getName());
                continue;
            }

            try {
                if (!Bukkit.dispatchCommand(player, commandToRun)) {
                    RuntimeContext.plugin()
                            .getSLF4JLogger()
                            .warn(
                                    "Player command failed for '{}' in dungeon '{}': {}",
                                    player.getName(),
                                    this.instance.getDungeon().getWorldName(),
                                    commandToRun);
                }
            } catch (Exception exception) {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .error(
                                "Player command threw an exception for '{}' in dungeon '{}': {}",
                                player.getName(),
                                this.instance.getDungeon().getWorldName(),
                                commandToRun,
                                exception);
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.COMMAND_BLOCK);
        button.setDisplayName("&bCommand Sender");
        button.addLore("&eRuns a command as the target");
        button.addLore("&eplayer or the console.");
        return button;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.normalizeCommandType();

        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COMMAND_BLOCK);
                        this.button.setDisplayName("&d&lSet Command");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.command.ask-command");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.command.current-command",
                                LangUtils.placeholder("command", CommandFunction.this.command));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        CommandFunction.this.command = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.command.command-set",
                                LangUtils.placeholder("command", CommandFunction.this.command));
                    }
                });

        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    @Override
                    public void buildButton() {
                        this.button =
                                new MenuButton(
                                        CommandFunction.this.commandType == CONSOLE_COMMAND_TYPE
                                                ? Material.REPEATING_COMMAND_BLOCK
                                                : Material.COMMAND_BLOCK);
                        this.button.setDisplayName(
                                "&d&lExecution: " + CommandFunction.this.getCommandTypeDisplay());
                        this.button.addLore("&ePlayer: runs as resolved target.");
                        this.button.addLore("&eConsole: runs in the dungeon world");
                        this.button.addLore("&eat this function's location.");
                        this.button.setEnchanted(
                                CommandFunction.this.commandType == CONSOLE_COMMAND_TYPE);
                    }

                    @Override
                    public void onSelect(Player player) {
                        CommandFunction.this.commandType =
                                CommandFunction.this.commandType == CONSOLE_COMMAND_TYPE
                                        ? PLAYER_COMMAND_TYPE
                                        : CONSOLE_COMMAND_TYPE;
                        player.sendMessage(
                                LangUtils.getMessage(
                                        "editor.function.command.command-set",
                                        LangUtils.placeholder(
                                                "command",
                                                "Execution set to "
                                                        + CommandFunction.this.getCommandTypeDisplay())));
                    }
                });

        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PLAYER_HEAD);
                        this.button.setDisplayName("&d&lToggle 'For Each Player'");
                        this.button.setEnchanted(CommandFunction.this.forEachPlayer);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!CommandFunction.this.forEachPlayer) {
                            LangUtils.sendMessage(player, "editor.function.command.for-each-player");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.command.run-once");
                        }

                        CommandFunction.this.forEachPlayer = !CommandFunction.this.forEachPlayer;
                    }
                });
    }

    /**
     * Sets the command.
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Sets the command type.
     */
    public void setCommandType(int commandType) {
        this.commandType = commandType;
        this.normalizeCommandType();
    }

    /**
     * Sets the for each player.
     */
    public void setForEachPlayer(boolean forEachPlayer) {
        this.forEachPlayer = forEachPlayer;
    }

    /**
     * Performs normalize command type.
     */
    private void normalizeCommandType() {
        if (this.commandType != PLAYER_COMMAND_TYPE && this.commandType != CONSOLE_COMMAND_TYPE) {
            this.commandType = PLAYER_COMMAND_TYPE;
        }
    }

    /**
     * Runs the command as console.
     */
    private void runConsoleCommand(String commandToRunTemplate, List<Player> players) {
        if (players.isEmpty()) {
            this.dispatchConsoleCommand(this.resolveConsoleCommand(commandToRunTemplate, null), null);
            return;
        }

        for (Player player : players) {
            this.dispatchConsoleCommand(this.resolveConsoleCommand(commandToRunTemplate, player), player);
        }
    }

    /**
     * Dispatches one console command and logs failures.
     */
    private void dispatchConsoleCommand(String commandToRun, Player player) {
        if (commandToRun.isEmpty()) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Command function in dungeon '{}' resolved to an empty console command{}.",
                            this.instance.getDungeon().getWorldName(),
                            player == null ? "" : " for '" + player.getName() + "'");
            return;
        }

        try {
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun)) {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .warn(
                                "Console command failed{} in dungeon '{}': {}",
                                player == null ? "" : " for '" + player.getName() + "'",
                                this.instance.getDungeon().getWorldName(),
                                commandToRun);
            }
        } catch (Exception exception) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .error(
                            "Console command threw an exception{} in dungeon '{}': {}",
                            player == null ? "" : " for '" + player.getName() + "'",
                            this.instance.getDungeon().getWorldName(),
                            commandToRun,
                            exception);
        }
    }

    /**
     * Resolves players.
     */
    private List<Player> resolvePlayers(
            TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        List<Player> players = new ArrayList<>();
        if (this.forEachPlayer) {
            for (DungeonPlayerSession target : targets) {
                if (target != null) {
                    this.addPlayerIfValid(players, target.getPlayer());
                }
            }
        } else if (!targets.isEmpty()) {
            DungeonPlayerSession primaryTarget = this.resolvePrimaryTarget(triggerEvent, targets);
            if (primaryTarget != null) {
                this.addPlayerIfValid(players, primaryTarget.getPlayer());
            }
        }

        if (players.isEmpty()
                && this.getTargetType() == FunctionTargetType.NONE
                && triggerEvent.getPlayer() != null) {
            this.addPlayerIfValid(players, triggerEvent.getPlayer());
        }

        return players;
    }

    /**
     * Resolves primary target.
     */
    private DungeonPlayerSession resolvePrimaryTarget(
            TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        DungeonPlayerSession source = triggerEvent.getPlayerSession();
        if (source != null) {
            for (DungeonPlayerSession target : targets) {
                if (target != null
                        && target.getPlayer().getUniqueId().equals(source.getPlayer().getUniqueId())) {
                    return target;
                }
            }
        }

        return targets.getFirst();
    }

    /**
     * Adds player if valid.
     */
    private void addPlayerIfValid(List<Player> players, Player player) {
        if (player != null && player.isOnline() && !players.contains(player)) {
            players.add(player);
        }
    }

    /**
     * Performs sanitize command.
     */
    private String sanitizeCommand(String command) {
        if (command == null) {
            return "";
        }

        String sanitized = command.trim();
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1).trim();
        }

        return sanitized;
    }

    /**
     * Resolves supported player placeholders for the command context.
     */
    private String resolveCommand(String commandTemplate, Player player) {
        String commandToRun = this.sanitizeCommand(commandTemplate);
        if (player == null) {
            return commandToRun;
        }

        String playerName = player.getName();
        String playerId = player.getUniqueId().toString();
        return commandToRun.replace("{player}", playerName)
                .replace("{player_name}", playerName)
                .replace("<player>", playerName)
                .replace("%player%", playerName)
                .replace("%player_name%", playerName)
                .replace("{uuid}", playerId)
                .replace("%uuid%", playerId);
    }

    /**
     * Resolves one console command anchored to the dungeon function location when possible.
     */
    private String resolveConsoleCommand(String commandTemplate, Player player) {
        String commandToRun = this.resolveCommand(commandTemplate, player);
        if (commandToRun.isEmpty()) {
            return commandToRun;
        }

        Location anchor = this.resolveConsoleAnchor();
        if (anchor == null || anchor.getWorld() == null) {
            return commandToRun;
        }

        World world = anchor.getWorld();
        String worldKey = world.getKey().asString();
        return "execute in "
                + worldKey
                + " positioned "
                + this.formatCoordinate(anchor.getX())
                + " "
                + this.formatCoordinate(anchor.getY())
                + " "
                + this.formatCoordinate(anchor.getZ())
                + " run "
                + commandToRun;
    }

    /**
     * Resolves the location that console commands should run from.
     */
    private Location resolveConsoleAnchor() {
        if (this.location != null && this.location.getWorld() != null) {
            return this.location;
        }

        if (this.instance != null && this.instance.getStartLoc() != null) {
            return this.instance.getStartLoc();
        }

        return null;
    }

    /**
     * Formats one command coordinate with stable decimal output.
     */
    private String formatCoordinate(double value) {
        if (Math.rint(value) == value) {
            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }

        return Double.toString(value);
    }

    /**
     * Returns the display name of the configured command sender.
     */
    private String getCommandTypeDisplay() {
        return this.commandType == CONSOLE_COMMAND_TYPE ? "Console" : "Player";
    }
}

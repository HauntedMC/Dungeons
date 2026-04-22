package nl.hauntedmc.dungeons.content.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Condition that counts nearby non-spectator players.
 *
 * <p>The check can require either an exact count or a minimum count inside the configured radius.
 */
@AutoRegister(id = "dungeons.condition.players_within")
@SerializableAs("dungeons.condition.players_within")
public class PlayersWithinCondition extends TriggerCondition {
    @PersistedField private double radius = 3.0;
    @PersistedField private int count = 1;
    @PersistedField private boolean playersExact = false;
    private transient boolean invalidConfigurationLogged;

    /**
     * Creates a new PlayersWithinCondition instance.
     */
    public PlayersWithinCondition(Map<String, Object> config) {
        super("playerswithin", config);
    }

    /**
     * Creates a new PlayersWithinCondition instance.
     */
    public PlayersWithinCondition() {
        super("playerswithin");
    }

    /**
     * Performs check.
     */
    @Override
    public boolean check(TriggerFireEvent event) {
        if (this.location == null || this.location.getWorld() == null) {
            if (!this.invalidConfigurationLogged) {
                this.invalidConfigurationLogged = true;
                this.logger()
                        .warn(
                                "PlayersWithinCondition in dungeon '{}' has no valid location.",
                                this.dungeonNameForLogs());
            }
            return false;
        }

        double effectiveRadius = Math.max(0.0, this.radius);
        int requiredPlayers = Math.max(0, this.count);
        if ((effectiveRadius != this.radius || requiredPlayers != this.count)
                && !this.invalidConfigurationLogged) {
            this.invalidConfigurationLogged = true;
            this.logger()
                    .warn(
                            "PlayersWithinCondition in dungeon '{}' at {} had invalid radius/count ({}, {}). Using {}, {}.",
                            this.dungeonNameForLogs(),
                            this.locationForLogs(),
                            this.radius,
                            this.count,
                            effectiveRadius,
                            requiredPlayers);
        }

        Collection<Entity> entities =
                this.location
                        .getWorld()
                        .getNearbyEntities(this.location, effectiveRadius, effectiveRadius, effectiveRadius);
        List<Player> players = new ArrayList<>();

        for (Entity ent : entities) {
            if (ent instanceof Player player && player.getGameMode() != GameMode.SPECTATOR) {
                players.add(player);
            }
        }

        return this.playersExact
                ? players.size() == requiredPlayers
                : players.size() >= requiredPlayers;
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.DETECTOR_RAIL);
        functionButton.setDisplayName("&dPlayers Within");
        functionButton.addLore("&eChecks for a number of players");
        functionButton.addLore("&ewithin a configured radius from");
        functionButton.addLore("&ethis function.");
        return functionButton;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.DETECTOR_RAIL);
                        this.button.setDisplayName("&d&lDistance");
                        this.button.setAmount((int) MathUtils.round(PlayersWithinCondition.this.radius, 0));
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.players-within.ask-radius");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        PlayersWithinCondition.this.radius = value.orElse(PlayersWithinCondition.this.radius);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.condition.players-within.radius-set",
                                    LangUtils.placeholder(
                                            "radius", String.valueOf(PlayersWithinCondition.this.radius)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PLAYER_HEAD);
                        this.button.setDisplayName(
                                PlayersWithinCondition.this.playersExact
                                        ? "&d&lPlayers Required"
                                        : "&d&lMinimum Players Required");
                        this.button.setAmount(PlayersWithinCondition.this.count);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.players-within.ask-count");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        PlayersWithinCondition.this.count = value.orElse(PlayersWithinCondition.this.count);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.condition.players-within.count-set",
                                    LangUtils.placeholder(
                                            "count", String.valueOf(PlayersWithinCondition.this.count)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lRequired Players Exact");
                        this.button.setEnchanted(PlayersWithinCondition.this.playersExact);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!PlayersWithinCondition.this.playersExact) {
                            LangUtils.sendMessage(player, "editor.condition.players-within.exact");
                        } else {
                            LangUtils.sendMessage(player, "editor.condition.players-within.minimum");
                        }

                        PlayersWithinCondition.this.playersExact = !PlayersWithinCondition.this.playersExact;
                    }
                });
    }

    /**
     * Returns the radius.
     */
    public double getRadius() {
        return this.radius;
    }

    /**
     * Sets the radius.
     */
    public void setRadius(double radius) {
        this.radius = radius;
    }

    /**
     * Returns the count.
     */
    public int getCount() {
        return this.count;
    }

    /**
     * Sets the count.
     */
    public void setCount(int count) {
        this.count = count;
    }
}

package nl.hauntedmc.dungeons.content.condition;

import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.time.TimeUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Condition that requires a playable instance to have been active for a minimum amount of time.
 */
@AutoRegister(id = "dungeons.condition.time_elapsed")
@SerializableAs("dungeons.condition.time_elapsed")
public class TimeElapsedCondition extends TriggerCondition {
    @PersistedField private int time;
    private transient boolean invalidTimeLogged;

    /**
     * Creates a new TimeElapsedCondition instance.
     */
    public TimeElapsedCondition(Map<String, Object> config) {
        super("Time Elapsed", config);
    }

    /**
     * Creates a new TimeElapsedCondition instance.
     */
    public TimeElapsedCondition() {
        super("Time Elapsed");
    }

    /**
     * Performs check.
     */
    @Override
    public boolean check(TriggerFireEvent event) {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance == null) {
            return false;
        } else {
            int requiredTime = Math.max(0, this.time);
            if (requiredTime != this.time && !this.invalidTimeLogged) {
                this.invalidTimeLogged = true;
                this.logger()
                        .warn(
                                "TimeElapsedCondition in dungeon '{}' at {} had invalid time {}. Clamping to 0.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.time);
            }

            int timeElapsed = instance.getTimeElapsed();
            return timeElapsed >= requiredTime;
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.CLOCK);
        functionButton.setDisplayName("&6Time Elapsed");
        functionButton.addLore("&eChecks if the dungeon has been");
        functionButton.addLore("&eactive for at least a,");
        functionButton.addLore("&ea specified duration.");
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
                        this.button = new MenuButton(Material.CLOCK);
                        this.button.setDisplayName(
                                TimeElapsedCondition.this.inverted ? "&d&lMinimum Time" : "&d&lMaximum Time");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!TimeElapsedCondition.this.inverted) {
                            LangUtils.sendMessage(player, "editor.condition.time-elapsed.ask-min");
                        } else {
                            LangUtils.sendMessage(player, "editor.condition.time-elapsed.ask-max");
                        }

                        LangUtils.sendMessage(
                                player,
                                "editor.condition.time-elapsed.current-time",
                                LangUtils.placeholder(
                                        "time", TimeUtils.formatDuration(TimeElapsedCondition.this.time * 1000L)));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> count = InputUtils.readIntegerInput(player, message);
                        TimeElapsedCondition.this.time = count.orElse(TimeElapsedCondition.this.time);
                        if (count.isPresent()) {
                            if (!TimeElapsedCondition.this.inverted) {
                                LangUtils.sendMessage(
                                        player,
                                        "editor.condition.time-elapsed.min-set",
                                        LangUtils.placeholder(
                                                "time", TimeUtils.formatDuration(TimeElapsedCondition.this.time * 1000L)));
                            } else {
                                LangUtils.sendMessage(
                                        player,
                                        "editor.condition.time-elapsed.max-set",
                                        LangUtils.placeholder(
                                                "time", TimeUtils.formatDuration(TimeElapsedCondition.this.time * 1000L)));
                            }
                        }
                    }
                });
    }
}

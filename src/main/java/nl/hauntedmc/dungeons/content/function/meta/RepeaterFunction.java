package nl.hauntedmc.dungeons.content.function.meta;

import java.util.*;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Composite function that re-runs its child functions a configured number of times.
 */
@AutoRegister(id = "dungeons.function.repeater")
@SerializableAs("dungeons.function.repeater")
public class RepeaterFunction extends CompositeFunction {
    @PersistedField private DungeonFunction function;
    @PersistedField private int interval = 20;
    @PersistedField private int delay = 0;
    @PersistedField private int maxRepeats = 0;
    private int repeatCount = 0;
    private BukkitRunnable repeater;

    /**
     * Creates a new RepeaterFunction instance.
     */
    public RepeaterFunction(Map<String, Object> config) {
        super("Function Repeater", config);
        this.setAllowChangingTargetType(false);
    }

    /**
     * Creates a new RepeaterFunction instance.
     */
    public RepeaterFunction() {
        super("Function Repeater");
        this.setAllowChangingTargetType(false);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();
        if (this.function != null) {
            this.function.setLocation(this.location);
            this.function.initialize();
        }
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        if (this.function == null) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Function repeater in dungeon '{}' has no nested function at {},{},{}.",
                            this.instance.getDungeon().getWorldName(),
                            this.location.getBlockX(),
                            this.location.getBlockY(),
                            this.location.getBlockZ());
        } else {
            this.function.setInstance(this.instance);
            this.function.setLocation(this.location);
            this.function.enable(this.instance, this.location);
        }
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        if (this.repeater != null && !this.repeater.isCancelled()) {
            this.repeater.cancel();
            this.repeater = null;
        }

        if (this.function != null) {
            this.function.disable();
        }
    }

    /**
     * Adds function.
     */
    @Override
    public void addFunction(DungeonFunction function) {
        this.function = function;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(final TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (this.function == null) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Skipping function repeater in dungeon '{}' at {},{},{} because nested function is missing.",
                            this.instance.getDungeon().getWorldName(),
                            this.location.getBlockX(),
                            this.location.getBlockY(),
                            this.location.getBlockZ());
            return;
        }

        int effectiveInterval = Math.max(1, this.interval);
        if (effectiveInterval != this.interval) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Function repeater in dungeon '{}' had invalid interval {} at {},{},{}; using 1 tick.",
                            this.instance.getDungeon().getWorldName(),
                            this.interval,
                            this.location.getBlockX(),
                            this.location.getBlockY(),
                            this.location.getBlockZ());
        }

        final List<DungeonPlayerSession> functionTargets =
                this.resolveNestedFunctionTargets(this.function, triggerEvent, targets);

        this.repeater =
                                new BukkitRunnable() {
                    /**
                     * Performs run.
                     */
                    public void run() {
                        if (RepeaterFunction.this.maxRepeats <= 0) {
                            RepeaterFunction.this.executeNestedFunction(
                                    RepeaterFunction.this.function, triggerEvent, functionTargets);
                        } else {
                            if (RepeaterFunction.this.repeatCount >= RepeaterFunction.this.maxRepeats) {
                                this.cancel();
                                if (RepeaterFunction.this.trigger != null
                                        && RepeaterFunction.this.trigger.isAllowRetrigger()) {
                                    RepeaterFunction.this.repeatCount = 0;
                                }

                                return;
                            }

                            RepeaterFunction.this.executeNestedFunction(
                                    RepeaterFunction.this.function, triggerEvent, functionTargets);
                            RepeaterFunction.this.repeatCount++;
                        }
                    }
                };
        this.repeater.runTaskTimer(RuntimeContext.plugin(), this.delay, effectiveInterval);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.REPEATING_COMMAND_BLOCK);
        button.setDisplayName("&bFunction Repeater");
        button.addLore("&eRuns a configured function");
        button.addLore("&erepeatedly on an interval.");
        return button;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COMMAND_BLOCK);
                        this.button.setDisplayName("&a&lSet Function");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "functionmenu");
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
                        this.button.setDisplayName("&e&lEdit Function");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        if (RepeaterFunction.this.function != null) {
                            playerSession.showHotbar(RepeaterFunction.this.function.getMenu());
                        } else {
                            LangUtils.sendMessage(player, "editor.function.repeater.no-function");
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
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName("&d&lStart Delay");
                        this.button.setAmount(RepeaterFunction.this.delay);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.repeater.ask-delay");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RepeaterFunction.this.delay = value.orElse(RepeaterFunction.this.delay);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.repeater.delay-set",
                                    LangUtils.placeholder("delay", String.valueOf(RepeaterFunction.this.delay)));
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
                        this.button = new MenuButton(Material.CLOCK);
                        this.button.setDisplayName("&d&lRepeater Interval");
                        this.button.setAmount(RepeaterFunction.this.interval);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.repeater.ask-rate");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RepeaterFunction.this.interval = value.orElse(RepeaterFunction.this.interval);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.repeater.interval-set",
                                    LangUtils.placeholder(
                                            "interval", String.valueOf(RepeaterFunction.this.interval)));
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
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lMax Repeats");
                        this.button.setAmount(RepeaterFunction.this.maxRepeats);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.repeater.ask-max-repeats");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RepeaterFunction.this.maxRepeats = value.orElse(RepeaterFunction.this.maxRepeats);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.repeater.max-repeats-set",
                                    LangUtils.placeholder(
                                            "max_repeats", String.valueOf(RepeaterFunction.this.maxRepeats)));
                        }
                    }
                });
    }

    /**
     * Performs clone.
     */
    public RepeaterFunction clone() {
        RepeaterFunction clone = (RepeaterFunction) super.clone();
        if (this.function != null) {
            clone.function = this.function.clone();
            if (clone.function != null) {
                clone.function.setLocation(clone.location);
            }
        }
        return clone;
    }
}

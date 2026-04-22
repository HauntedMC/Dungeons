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
 * Composite function that runs its child functions after a configured delay.
 */
@AutoRegister(id = "dungeons.function.delayed")
@SerializableAs("dungeons.function.delayed")
public class DelayedFunction extends CompositeFunction {
    @PersistedField private DungeonFunction function;
    @PersistedField private int delay = 0;
    private BukkitRunnable task;

    /**
     * Creates a new DelayedFunction instance.
     */
    public DelayedFunction(Map<String, Object> config) {
        super("Delayed Function", config);
    }

    /**
     * Creates a new DelayedFunction instance.
     */
    public DelayedFunction() {
        super("Delayed Function");
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
                            "Delayed function in dungeon '{}' has no nested function at {},{},{}.",
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
        if (this.task != null && !this.task.isCancelled()) {
            this.task.cancel();
            this.task = null;
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
        if (this.function != null) {
            final List<DungeonPlayerSession> functionTargets =
                    this.resolveNestedFunctionTargets(this.function, triggerEvent, targets);

            this.task =
                                        new BukkitRunnable() {
                        /**
                         * Performs run.
                         */
                        public void run() {
                            DelayedFunction.this.executeNestedFunction(
                                    DelayedFunction.this.function, triggerEvent, functionTargets);
                        }
                    };
            this.task.runTaskLater(RuntimeContext.plugin(), this.delay);
        } else {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Skipping delayed function in dungeon '{}' at {},{},{} because nested function is missing.",
                            this.instance.getDungeon().getWorldName(),
                            this.location.getBlockX(),
                            this.location.getBlockY(),
                            this.location.getBlockZ());
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.REPEATER);
        button.setDisplayName("&bDelayed Function");
        button.addLore("&eRuns a configured function");
        button.addLore("&eafter a delayed interval.");
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
                        if (DelayedFunction.this.function != null) {
                            playerSession.showHotbar(DelayedFunction.this.function.getMenu());
                        } else {
                            LangUtils.sendMessage(player, "editor.function.delayed.no-function");
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
                        this.button.setAmount(DelayedFunction.this.delay);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.delayed.ask-delay");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        DelayedFunction.this.delay = value.orElse(DelayedFunction.this.delay);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.delayed.delay-set",
                                    LangUtils.placeholder("delay", String.valueOf(DelayedFunction.this.delay)));
                        }
                    }
                });
    }

    /**
     * Performs clone.
     */
    public DelayedFunction clone() {
        DelayedFunction clone = (DelayedFunction) super.clone();
        if (this.function != null) {
            clone.function = this.function.clone();
            if (clone.function != null) {
                clone.function.setLocation(clone.location);
            }
        }
        return clone;
    }
}

package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Function that toggles a redstone block state at its location.
 */
@AutoRegister(id = "dungeons.function.redstone_block")
@SerializableAs("dungeons.function.redstone_block")
public class RedstoneBlockFunction extends DungeonFunction {
    @PersistedField private int delay = 0;
    @PersistedField private int count = 1;
    @PersistedField private int rate = 5;
    private BukkitRunnable repeater;
    private int status = 0;

    /**
     * Creates a new RedstoneBlockFunction instance.
     */
    public RedstoneBlockFunction(Map<String, Object> config) {
        super("Redstone Block", config);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Creates a new RedstoneBlockFunction instance.
     */
    public RedstoneBlockFunction() {
        super("Redstone Block");
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        if (this.repeater != null && !this.repeater.isCancelled()) {
            this.repeater.cancel();
        }

        this.repeater = null;
        this.status = 0;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        final World world = this.instance.getInstanceWorld();
        if (this.repeater != null && !this.repeater.isCancelled()) {
            this.repeater.cancel();
        }

        this.status = 0;
        final int effectiveDelay = Math.max(0, this.delay);
        final int effectiveCount = Math.max(1, this.count);
        final int effectiveRate = Math.max(1, this.rate);
        this.repeater =
                                new BukkitRunnable() {
                    /**
                     * Performs run.
                     */
                    public void run() {
                        world.getBlockAt(RedstoneBlockFunction.this.location).setType(Material.REDSTONE_BLOCK);
                        RedstoneBlockFunction.this.status++;
                        if (RedstoneBlockFunction.this.status >= effectiveCount) {
                            this.cancel();
                        } else {
                            (new BukkitRunnable() {
                                        /**
                                         * Performs run.
                                         */
                                        public void run() {
                                            world.getBlockAt(RedstoneBlockFunction.this.location).setType(Material.AIR);
                                        }
                                    })
                                    .runTaskLater(RuntimeContext.plugin(), effectiveRate - 1L);
                        }
                    }
                };
        this.repeater.runTaskTimer(RuntimeContext.plugin(), effectiveDelay, effectiveRate);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.REDSTONE_BLOCK);
        functionButton.setDisplayName("&dPlace Redstone Block");
        functionButton.addLore("&ePlaces a redstone block at");
        functionButton.addLore("&ethis location.");
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
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName("&a&lDelay");
                        this.button.setAmount(RedstoneBlockFunction.this.delay);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.redstone-block.ask-delay");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RedstoneBlockFunction.this.delay = value.orElse(RedstoneBlockFunction.this.delay);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.redstone-block.delay-set",
                                    LangUtils.placeholder("delay", String.valueOf(RedstoneBlockFunction.this.delay)));
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
                        this.button = new MenuButton(Material.COMPARATOR);
                        this.button.setDisplayName("&a&lRepeat Count");
                        this.button.setAmount(RedstoneBlockFunction.this.count);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.redstone-block.ask-count");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RedstoneBlockFunction.this.count = value.orElse(RedstoneBlockFunction.this.count);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.redstone-block.count-set",
                                    LangUtils.placeholder("count", String.valueOf(RedstoneBlockFunction.this.count)));
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
                        this.button.setDisplayName("&a&lRepeat Interval");
                        this.button.setAmount(RedstoneBlockFunction.this.rate);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.redstone-block.ask-rate");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RedstoneBlockFunction.this.rate = value.orElse(RedstoneBlockFunction.this.rate);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.redstone-block.rate-set",
                                    LangUtils.placeholder("rate", String.valueOf(RedstoneBlockFunction.this.rate)));
                        }
                    }
                });
    }
}

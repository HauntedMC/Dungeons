package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Function that manages a toggleable hologram in a playable instance.
 *
 * <p>The hologram can be shown by default, hidden until triggered, and edited in-world from the
 * dungeon editor.
 */
@AutoRegister(id = "dungeons.function.hologram")
@SerializableAs("dungeons.function.hologram")
public class HologramFunction extends DungeonFunction {
    @PersistedField private Location hologramLoc;
    @PersistedField private String message;
    @PersistedField private int radius = 25;
    @PersistedField private boolean visibleByDefault = true;
    private boolean awaitingInput = false;
    private BukkitRunnable inputWaiter;
    private BukkitRunnable particleIndicator;
    private boolean visible = true;

    /**
     * Creates a new HologramFunction instance.
     */
    public HologramFunction(Map<String, Object> config) {
        super("Hologram", config);
        this.targetType = FunctionTargetType.NONE;
        this.setCategory(FunctionCategory.LOCATION);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new HologramFunction instance.
     */
    public HologramFunction() {
        super("Hologram");
        this.setAllowChangingTargetType(false);
        this.targetType = FunctionTargetType.NONE;
        this.setCategory(FunctionCategory.LOCATION);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        this.visible = this.visibleByDefault;
        if (this.instance.isPlayInstance()) {
            this.instance.asPlayInstance().addHologram(this);
            if (!this.visible) {
                this.instance.asPlayInstance().hideHologramFunction(this);
            }
        }
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        if (this.inputWaiter != null && !this.inputWaiter.isCancelled()) {
            this.inputWaiter.cancel();
        }

        if (this.particleIndicator != null && !this.particleIndicator.isCancelled()) {
            this.particleIndicator.cancel();
        }

        this.awaitingInput = false;
        this.inputWaiter = null;
        this.particleIndicator = null;
        if (this.instance != null && this.instance.isPlayInstance()) {
            this.instance.asPlayInstance().hideHologramFunction(this);
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.NAME_TAG);
        functionButton.setDisplayName("&bHologram");
        functionButton.addLore("&eDisplays hologram text at a");
        functionButton.addLore("&especified location. Triggering");
        functionButton.addLore("&etoggles hologram visibility.");
        return functionButton;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        this.toggleVisible();
    }

    /**
     * Toggles visible.
     */
    private void toggleVisible() {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            this.visible = !this.visible;
            if (this.visible) {
                instance.showHologramFunction(this);
            } else {
                instance.hideHologramFunction(this);
            }
        }
    }

    /**
     * Returns the hologram loc.
     */
    public Location getHologramLoc() {
        return this.hologramLoc == null ? this.location : this.hologramLoc;
    }

    /**
     * Returns the clean message.
     */
    public String getCleanMessage() {
        return this.message == null ? null : this.message.replace("\\n", "\n");
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
                        this.button = new MenuButton(Material.COMPASS);
                        this.button.setDisplayName("&d&lSet Location");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        final Player player = event.getPlayer();
                        if (HologramFunction.this.awaitingInput) {
                            HologramFunction.this.awaitingInput = false;
                            HologramFunction.this.hologramLoc = player.getLocation();
                            HologramFunction.this.hologramLoc.setWorld(null);
                            HologramFunction.this.hologramLoc.setY(
                                    HologramFunction.this.hologramLoc.getY() + 1.75);
                            LangUtils.sendMessage(player, "editor.function.hologram.location-set-standing");
                        } else {
                            HologramFunction.this.awaitingInput = true;
                            HologramFunction.this.inputWaiter =
                                                                        new BukkitRunnable() {
                                        /**
                                         * Performs run.
                                         */
                                        public void run() {
                                            HologramFunction.this.awaitingInput = false;
                                            if (HologramFunction.this.particleIndicator != null) {
                                                HologramFunction.this.particleIndicator.cancel();
                                            }
                                        }
                                    };
                            HologramFunction.this.inputWaiter.runTaskLater(RuntimeContext.plugin(), 200L);
                            if (HologramFunction.this.hologramLoc == null) {
                                LangUtils.sendMessage(player, "editor.function.hologram.current-location-none");
                                LangUtils.sendMessage(player, "editor.function.hologram.click-again-set-location");
                                return;
                            }

                            final Location targetLoc = HologramFunction.this.hologramLoc.clone();
                            if (targetLoc.getWorld() == null && HologramFunction.this.location != null) {
                                targetLoc.setWorld(HologramFunction.this.location.getWorld());
                            }
                            HologramFunction.this.particleIndicator =
                                                                        new BukkitRunnable() {
                                        /**
                                         * Performs run.
                                         */
                                        public void run() {
                                            if (targetLoc.getWorld() != null) {
                                                player.spawnParticle(Particle.END_ROD, targetLoc, 4, 0.1, 0.1, 0.1, 0.0);
                                            }
                                        }
                                    };
                            HologramFunction.this.particleIndicator.runTaskTimer(
                                    RuntimeContext.plugin(), 0L, 10L);
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.hologram.current-location",
                                    LangUtils.placeholder(
                                            "x",
                                            String.valueOf(MathUtils.round(HologramFunction.this.hologramLoc.getX(), 2))),
                                    LangUtils.placeholder(
                                            "y",
                                            String.valueOf(MathUtils.round(HologramFunction.this.hologramLoc.getY(), 2))),
                                    LangUtils.placeholder(
                                            "z",
                                            String.valueOf(
                                                    MathUtils.round(HologramFunction.this.hologramLoc.getZ(), 2))));
                            LangUtils.sendMessage(player, "editor.function.hologram.click-again-set-location");
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
                        this.button = new MenuButton(Material.NAME_TAG);
                        this.button.setDisplayName("&d&lHologram Message");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (HologramFunction.this.instance.getHologramManager() != null) {
                            LangUtils.sendMessage(player, "editor.function.hologram.ask-message-multiline");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.hologram.ask-message");
                        }

                        LangUtils.sendMessage(
                                player,
                                "editor.function.hologram.current-message",
                                LangUtils.placeholder("message", HologramFunction.this.getCleanMessage()));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        HologramFunction.this.message = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.hologram.message-set",
                                LangUtils.placeholder("message", HologramFunction.this.getCleanMessage()));
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
                        this.button.setDisplayName("&d&lView Distance");
                        this.button.setAmount(HologramFunction.this.radius);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.hologram.ask-radius");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        HologramFunction.this.radius = value.orElse(HologramFunction.this.radius);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.hologram.radius-set",
                                    LangUtils.placeholder("radius", String.valueOf(HologramFunction.this.radius)));
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
                        this.button = new MenuButton(Material.STRUCTURE_VOID);
                        this.button.setDisplayName("&d&lVisible by Default");
                        this.button.setEnchanted(HologramFunction.this.visibleByDefault);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!HologramFunction.this.visibleByDefault) {
                            LangUtils.sendMessage(player, "editor.function.hologram.default-visible");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.hologram.default-invisible");
                        }

                        HologramFunction.this.visibleByDefault = !HologramFunction.this.visibleByDefault;
                    }
                });
    }

    /**
     * Returns the message.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Returns the radius.
     */
    public int getRadius() {
        return this.radius;
    }

    /**
     * Returns whether visible by default.
     */
    public boolean isVisibleByDefault() {
        return this.visibleByDefault;
    }

    /**
     * Returns whether visible.
     */
    public boolean isVisible() {
        return this.visible;
    }
}

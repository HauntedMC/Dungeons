package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Function that teleports its targets to the configured destination.
 */
@AutoRegister(id = "dungeons.function.teleport")
@SerializableAs("dungeons.function.teleport")
public class TeleportFunction extends DungeonFunction {
    @PersistedField private Location teleportTarget;
    private Location instanceLoc;
    private boolean awaitingInput = false;
    private BukkitRunnable inputWaiter;
    private BukkitRunnable particleIndicator;

    /**
     * Creates a new TeleportFunction instance.
     */
    public TeleportFunction(Map<String, Object> config) {
        super("Teleporter", config);
        this.setCategory(FunctionCategory.PLAYER);
        this.setRequiresTarget(true);
    }

    /**
     * Creates a new TeleportFunction instance.
     */
    public TeleportFunction() {
        super("Teleporter");
        this.setCategory(FunctionCategory.PLAYER);
        this.setRequiresTarget(true);
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        if (this.teleportTarget == null) {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .error(
                            "Teleport function in dungeon '{}' has no target location at {},{},{}.",
                            this.instance.getDungeon().getWorldName(),
                            this.location.getBlockX(),
                            this.location.getBlockY(),
                            this.location.getBlockZ());
        } else {
            this.instanceLoc = this.teleportTarget.clone();
            this.instanceLoc.setWorld(this.location.getWorld());
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
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (this.instanceLoc != null) {
            for (DungeonPlayerSession playerSession : targets) {
                if (playerSession.getInstance() == this.instance) {
                    EntityUtils.forceTeleport(playerSession.getPlayer(), this.instanceLoc);
                }
            }
        } else {
            RuntimeContext.plugin()
                    .getSLF4JLogger()
                    .warn(
                            "Skipping teleport function in dungeon '{}' at {},{},{} because no runtime target was initialized.",
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
        MenuButton button = new MenuButton(Material.ENDER_PEARL);
        button.setDisplayName("&aTeleporter");
        button.addLore("&eTeleports the target player(s)");
        button.addLore("&eto a configured location.");
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
                        this.button = new MenuButton(Material.COMPASS);
                        this.button.setDisplayName("&d&lSet Location");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        final Player player = event.getPlayer();
                        if (TeleportFunction.this.awaitingInput) {
                            TeleportFunction.this.awaitingInput = false;
                            TeleportFunction.this.teleportTarget = player.getLocation();
                            TeleportFunction.this.teleportTarget.setWorld(null);
                            if (TeleportFunction.this.particleIndicator != null) {
                                TeleportFunction.this.particleIndicator.cancel();
                            }

                            LangUtils.sendMessage(player, "editor.function.teleport.location-set-standing");
                        } else {
                            TeleportFunction.this.awaitingInput = true;
                            TeleportFunction.this.inputWaiter =
                                                                        new BukkitRunnable() {
                                        /**
                                         * Performs run.
                                         */
                                        public void run() {
                                            TeleportFunction.this.awaitingInput = false;
                                            if (TeleportFunction.this.particleIndicator != null) {
                                                TeleportFunction.this.particleIndicator.cancel();
                                            }
                                        }
                                    };
                            TeleportFunction.this.inputWaiter.runTaskLater(RuntimeContext.plugin(), 200L);
                            if (TeleportFunction.this.teleportTarget == null) {
                                LangUtils.sendMessage(player, "editor.function.teleport.current-location-none");
                                LangUtils.sendMessage(player, "editor.function.teleport.click-again-set-location");
                                return;
                            }

                            final Location targetLoc = TeleportFunction.this.teleportTarget.clone();
                            targetLoc.setY(targetLoc.getY() + 0.5);
                            targetLoc.setWorld(TeleportFunction.this.location.getWorld());
                            TeleportFunction.this.particleIndicator =
                                                                        new BukkitRunnable() {
                                        /**
                                         * Performs run.
                                         */
                                        public void run() {
                                            if (TeleportFunction.this.instance.isEditInstance()) {
                                                player.spawnParticle(Particle.END_ROD, targetLoc, 4, 0.1, 0.1, 0.1, 0.0);
                                            }
                                        }
                                    };
                            TeleportFunction.this.particleIndicator.runTaskTimer(
                                    RuntimeContext.plugin(), 0L, 10L);
                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            RuntimeContext.plugin(),
                                            () -> {
                                                if (TeleportFunction.this.particleIndicator != null) {
                                                    TeleportFunction.this.particleIndicator.cancel();
                                                }
                                            },
                                            200L);
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.teleport.current-location",
                                    LangUtils.placeholder(
                                            "x",
                                            String.valueOf(
                                                    MathUtils.round(TeleportFunction.this.teleportTarget.getX(), 2))),
                                    LangUtils.placeholder(
                                            "y",
                                            String.valueOf(
                                                    MathUtils.round(TeleportFunction.this.teleportTarget.getY(), 2))),
                                    LangUtils.placeholder(
                                            "z",
                                            String.valueOf(
                                                    MathUtils.round(TeleportFunction.this.teleportTarget.getZ(), 2))));
                            LangUtils.sendMessage(player, "editor.function.teleport.click-again-set-location");
                        }
                    }
                });
    }

    /**
     * Returns the teleport target.
     */
    public Location getTeleportTarget() {
        return this.teleportTarget;
    }

    /**
     * Sets the teleport target.
     */
    public void setTeleportTarget(Location teleportTarget) {
        this.teleportTarget = teleportTarget;
    }

    /**
     * Returns the instance loc.
     */
    public Location getInstanceLoc() {
        return this.instanceLoc;
    }
}

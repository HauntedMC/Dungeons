package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Function that updates player respawn and optional save-point locations.
 */
@AutoRegister(id = "dungeons.function.checkpoint")
@SerializableAs("dungeons.function.checkpoint")
public class CheckpointFunction extends DungeonFunction {
    @PersistedField private double yaw = 0.0;
    @PersistedField private double pitch = 0.0;
    @PersistedField private boolean savePoint = false;

    /**
     * Creates a new CheckpointFunction instance.
     */
    public CheckpointFunction(Map<String, Object> config) {
        super("Checkpoint", config);
        this.setCategory(FunctionCategory.DUNGEON);
    }

    /**
     * Creates a new CheckpointFunction instance.
     */
    public CheckpointFunction() {
        super("Checkpoint");
        this.setCategory(FunctionCategory.DUNGEON);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        for (DungeonPlayerSession playerSession : targets) {
            Location loc = this.resolveRespawnLocation();
            loc.setYaw((float) this.yaw);
            loc.setPitch((float) this.pitch);
            playerSession.setDungeonRespawn(loc);
            if (this.savePoint && this.instance.as(BranchingInstance.class) == null) {
                playerSession.setDungeonSavePoint(loc, this.instance.getDungeon().getWorldName());
            }

            LangUtils.sendMessage(playerSession.getPlayer(), "instance.play.functions.checkpoint");
        }
    }

    /**
     * Converts the editor block position into a stable standing point for respawn.
     */
    private Location resolveRespawnLocation() {
        Location loc = this.location.clone();
        if (this.isBlockCoordinate(loc.getX())) {
            loc.setX(loc.getX() + 0.5D);
        }

        if (this.isBlockCoordinate(loc.getZ())) {
            loc.setZ(loc.getZ() + 0.5D);
        }

        if (this.isBlockCoordinate(loc.getY()) && loc.getBlock().isSolid()) {
            loc.setY(loc.getY() + 1.0D);
        }

        return loc;
    }

    private boolean isBlockCoordinate(double value) {
        return Math.floor(value) == value;
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.ORANGE_BANNER);
        functionButton.setDisplayName("&6Respawn Checkpoint");
        functionButton.addLore("&eSets a respawn checkpoint at");
        functionButton.addLore("&ethis location. If the checkpoint");
        functionButton.addLore("&ehas been triggered, players will");
        functionButton.addLore("&erespawn here after losing a life.");
        return functionButton;
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
                        this.button.setDisplayName("&d&lPlayer Direction");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        CheckpointFunction.this.yaw = player.getLocation().getYaw();
                        CheckpointFunction.this.pitch = player.getLocation().getPitch();
                        LangUtils.sendMessage(
                                player,
                                "editor.function.checkpoint.direction-set",
                                LangUtils.placeholder("yaw", String.valueOf(CheckpointFunction.this.yaw)));
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        if (CheckpointFunction.this.instance.as(BranchingEditableInstance.class) != null) {
                            this.button = null;
                        } else {
                            this.button = new MenuButton(Material.LIME_BANNER);
                            this.button.setDisplayName("&d&lUse as Save Point");
                            this.button.setEnchanted(CheckpointFunction.this.savePoint);
                        }
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.checkpoint.rejoin-note");
                        if (!CheckpointFunction.this.savePoint) {
                            LangUtils.sendMessage(player, "editor.function.checkpoint.enabled");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.checkpoint.disabled");
                        }

                        CheckpointFunction.this.savePoint = !CheckpointFunction.this.savePoint;
                    }
                });
    }

    /**
     * Returns the yaw.
     */
    public double getYaw() {
        return this.yaw;
    }

    /**
     * Sets the yaw.
     */
    public void setYaw(double yaw) {
        this.yaw = yaw;
    }

    /**
     * Returns the pitch.
     */
    public double getPitch() {
        return this.pitch;
    }

    /**
     * Sets the pitch.
     */
    public void setPitch(double pitch) {
        this.pitch = pitch;
    }

    /**
     * Returns whether save point.
     */
    public boolean isSavePoint() {
        return this.savePoint;
    }
}

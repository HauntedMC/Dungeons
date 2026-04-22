package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.trigger.KeyItemTrigger;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.event.InteractionUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Openable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Function that locks, unlocks, opens, or closes a door block at its location.
 *
 * <p>When paired with a key-item trigger, the locked message can reference the required key item.
 */
@AutoRegister(id = "dungeons.function.door_control")
@SerializableAs("dungeons.function.door_control")
public class DoorControlFunction extends DungeonFunction {
    @PersistedField private boolean lockedByDefault = true;
    @PersistedField private boolean openOnUnlock = true;
    private boolean locked = true;

    /**
     * Creates a new DoorControlFunction instance.
     */
    public DoorControlFunction(Map<String, Object> config) {
        super("Door Control", config);
        this.setRequiresTrigger(false);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Creates a new DoorControlFunction instance.
     */
    public DoorControlFunction() {
        super("Door Control");
        this.setRequiresTrigger(false);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        this.locked = this.lockedByDefault;
    }

    /**
     * Sets the locked.
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
        if (locked) {
            this.instance.messagePlayers(LangUtils.getMessage("instance.play.functions.doors.lock"));
            this.instance
                    .getInstanceWorld()
                    .playSound(this.getLocation(), "minecraft:block.iron_trapdoor.close", 5.0F, 0.7F);
            if (this.openOnUnlock) {
                this.closeDoor();
            }
        } else {
            this.instance.messagePlayers(LangUtils.getMessage("instance.play.functions.doors.unlock"));
            this.instance
                    .getInstanceWorld()
                    .playSound(this.getLocation(), "minecraft:block.iron_trapdoor.open", 5.0F, 0.7F);
            if (this.openOnUnlock) {
                this.openDoor();
            }
        }
    }

    /**
     * Opens door.
     */
    public void openDoor() {
        if (this.location.getBlock().getBlockData() instanceof Openable door) {
            door.setOpen(true);
            this.location.getBlock().setBlockData(door);
        }
    }

    /**
     * Closes door.
     */
    public void closeDoor() {
        if (this.location.getBlock().getBlockData() instanceof Openable door) {
            door.setOpen(false);
            this.location.getBlock().setBlockData(door);
        }
    }

    /**
     * Performs on interact door.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractDoor(PlayerInteractEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            Player player = event.getPlayer();
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
                if (!InteractionUtils.isInteractionDenied(event)) {
                    if (event.getClickedBlock() != null) {
                        Location blockLoc = event.getClickedBlock().getLocation();
                        Location aboveLoc = blockLoc.clone();
                        aboveLoc.setY(blockLoc.getY() + 1.0);
                        Location belowLoc = blockLoc.clone();
                        belowLoc.setY(blockLoc.getY() - 1.0);
                        if (blockLoc.equals(this.location)
                                || aboveLoc.equals(this.location)
                                || belowLoc.equals(this.location)) {
                            if (this.locked) {
                                InteractionUtils.denyInteraction(event);
                                if (this.trigger instanceof KeyItemTrigger keyTrigger) {
                                    ItemStack keyItem = keyTrigger.getItem();
                                    LangUtils.sendMessage(
                                            player,
                                            "instance.play.functions.doors.locked-key",
                                            LangUtils.placeholder("key", ItemUtils.getItemDisplayName(keyItem)));
                                } else {
                                    LangUtils.sendMessage(player, "instance.play.functions.doors.locked");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        this.setLocked(!this.locked);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.IRON_DOOR);
        functionButton.setDisplayName("&dDoor Controller");
        functionButton.addLore("&eUnlocks or locks a door at");
        functionButton.addLore("&ethis location.");
        return functionButton;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.IRON_DOOR);
                        this.button.setDisplayName("&d&lStart Locked");
                        this.button.setEnchanted(DoorControlFunction.this.lockedByDefault);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!DoorControlFunction.this.lockedByDefault) {
                            LangUtils.sendMessage(player, "editor.function.door-control.locked-default");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.door-control.unlocked-default");
                        }

                        DoorControlFunction.this.lockedByDefault = !DoorControlFunction.this.lockedByDefault;
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
                        this.button.setDisplayName("&d&lAuto Open/Close");
                        this.button.setEnchanted(DoorControlFunction.this.openOnUnlock);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!DoorControlFunction.this.openOnUnlock) {
                            LangUtils.sendMessage(player, "editor.function.door-control.auto");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.door-control.manual");
                        }

                        DoorControlFunction.this.openOnUnlock = !DoorControlFunction.this.openOnUnlock;
                    }
                });
    }

    /**
     * Returns whether locked by default.
     */
    public boolean isLockedByDefault() {
        return this.lockedByDefault;
    }

    /**
     * Sets the locked by default.
     */
    public void setLockedByDefault(boolean lockedByDefault) {
        this.lockedByDefault = lockedByDefault;
    }

    /**
     * Returns whether open on unlock.
     */
    public boolean isOpenOnUnlock() {
        return this.openOnUnlock;
    }

    /**
     * Sets the open on unlock.
     */
    public void setOpenOnUnlock(boolean openOnUnlock) {
        this.openOnUnlock = openOnUnlock;
    }

    /**
     * Returns whether locked.
     */
    public boolean isLocked() {
        return this.locked;
    }
}

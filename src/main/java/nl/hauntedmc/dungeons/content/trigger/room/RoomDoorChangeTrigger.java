package nl.hauntedmc.dungeons.content.trigger.room;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.event.RoomDoorChangeEvent;
import nl.hauntedmc.dungeons.generation.room.DoorAction;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerEvent;

/**
 * Trigger that listens for open/close/toggle events on branching room doors.
 */
@AutoRegister(id = "dungeons.trigger.room_door_change")
@SerializableAs("dungeons.trigger.room_door_change")
public class RoomDoorChangeTrigger extends DungeonTrigger {
    @PersistedField private String doorName = "any";
    @PersistedField private int action = 1;

    /**
     * Creates a new RoomDoorChangeTrigger instance.
     */
    public RoomDoorChangeTrigger(Map<String, Object> config) {
        super("Room Door", config);
        this.setCategory(TriggerCategory.ROOM);
        this.setHasTarget(false);
    }

    /**
     * Creates a new RoomDoorChangeTrigger instance.
     */
    public RoomDoorChangeTrigger() {
        super("Room Door");
        this.setCategory(TriggerCategory.ROOM);
        this.setHasTarget(false);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.OAK_DOOR);
        functionButton.setDisplayName("&aRoom Door Open/Close");
        functionButton.addLore("&eTriggered when a door in this");
        functionButton.addLore("&eroom is opened or closed.");
        return functionButton;
    }

    /**
     * Performs on door change.
     */
    @EventHandler
    public void onDoorChange(RoomDoorChangeEvent event) {
        DoorAction[] actions = DoorAction.values();
        DoorAction action = actions[Math.floorMod(this.action, actions.length)];
        if (action.equals(event.getAction())) {
            BranchingInstance branchingInstance = this.instance.as(BranchingInstance.class);
            if (branchingInstance != null) {
                InstanceRoom room = branchingInstance.getRoom(this.location);
                if (room != null) {
                    if (event.getRoom() == room) {
                        if (this.matchesAnyDoor() || event.getDoor().getNamespace().equals(this.doorName)) {
                            this.trigger();
                        }
                    }
                }
            }
        }
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
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(RoomDoorChangeTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!RoomDoorChangeTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.room-door-change.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.room-door-change.prevent-repeat");
                        }

                        RoomDoorChangeTrigger.this.allowRetrigger = !RoomDoorChangeTrigger.this.allowRetrigger;
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
                        this.button.setDisplayName("&d&lDoor Name");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.room-door-change.ask-door");
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.room-door-change.current-door",
                                LangUtils.placeholder("door", RoomDoorChangeTrigger.this.doorName));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        RoomDoorChangeTrigger.this.doorName = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.room-door-change.door-set",
                                LangUtils.placeholder("door", message));
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        DoorAction[] actions = DoorAction.values();
                        RoomDoorChangeTrigger.this.action =
                                Math.floorMod(RoomDoorChangeTrigger.this.action, actions.length);
                        this.button = new MenuButton(Material.DIAMOND);
                        this.button.setDisplayName("&d&lAction: " + actions[RoomDoorChangeTrigger.this.action]);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        RoomDoorChangeTrigger.this.action++;
                        if (RoomDoorChangeTrigger.this.action >= DoorAction.values().length) {
                            RoomDoorChangeTrigger.this.action = 1;
                        }

                        DoorAction[] actions = DoorAction.values();
                        DoorAction action =
                                actions[Math.floorMod(RoomDoorChangeTrigger.this.action, actions.length)];
                        this.menu.openFor(playerSession);
                        if (action == DoorAction.OPEN) {
                            LangUtils.sendMessage(player, "editor.trigger.room-door-change.on-open");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.room-door-change.on-close");
                        }
                    }
                });
    }

    /**
     * Returns the door name.
     */
    public String getDoorName() {
        return this.doorName;
    }

    /**
     * Performs matches any door.
     */
    private boolean matchesAnyDoor() {
        return "any".equalsIgnoreCase(this.doorName) || "all".equalsIgnoreCase(this.doorName);
    }
}

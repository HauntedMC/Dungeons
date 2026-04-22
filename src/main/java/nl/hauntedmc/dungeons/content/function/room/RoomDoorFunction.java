package nl.hauntedmc.dungeons.content.function.room;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.DoorAction;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Branching-room function that opens, closes, or toggles named room doors.
 */
@AutoRegister(id = "dungeons.function.room_door")
@SerializableAs("dungeons.function.room_door")
public class RoomDoorFunction extends DungeonFunction {
    @PersistedField private String doorName = "all";
    @PersistedField private int action = 0;
    @PersistedField private boolean keepEntranceOpen = true;

    /**
     * Creates a new RoomDoorFunction instance.
     */
    public RoomDoorFunction(Map<String, Object> config) {
        super("Room Door Controller", config);
        this.setCategory(FunctionCategory.ROOM);
        this.setAllowChangingTargetType(false);
    }

    /**
     * Creates a new RoomDoorFunction instance.
     */
    public RoomDoorFunction() {
        super("Room Door Controller");
        this.setCategory(FunctionCategory.ROOM);
        this.setAllowChangingTargetType(false);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        BranchingInstance branchingInstance = this.instance.as(BranchingInstance.class);
        if (branchingInstance != null) {
            InstanceRoom room = branchingInstance.getRoom(this.location);
            if (room != null) {
                DoorAction[] actions = DoorAction.values();
                DoorAction action = actions[Math.floorMod(this.action, actions.length)];
                switch (action) {
                    case TOGGLE:
                        if (this.controlsAllDoors()) {
                            room.toggleValidDoors(this.keepEntranceOpen);
                        } else {
                            room.toggleDoor(this.doorName);
                        }
                        break;
                    case OPEN:
                        if (this.controlsAllDoors()) {
                            room.openValidDoors(this.keepEntranceOpen);
                        } else {
                            room.openDoor(this.doorName);
                        }
                        break;
                    case CLOSE:
                        if (this.controlsAllDoors()) {
                            room.closeValidDoors(this.keepEntranceOpen);
                        } else {
                            room.closeDoor(this.doorName);
                        }
                }
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.OAK_DOOR);
        functionButton.setDisplayName("&dRoom Door Controller");
        functionButton.addLore("&eOpens or closes a specified room");
        functionButton.addLore("&edoors. Room doors can be created");
        functionButton.addLore("&ewhen editing a connector.");
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
                        this.button = new MenuButton(Material.NAME_TAG);
                        this.button.setDisplayName("&d&lDoor Name");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.room-door.ask-door-name");
                        LangUtils.sendMessage(player, "editor.function.room-door.enter-all");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.room-door.current-door",
                                LangUtils.placeholder("door", RoomDoorFunction.this.doorName));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        RoomDoorFunction.this.doorName = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.room-door.door-set",
                                LangUtils.placeholder("door", message));
                        BranchingEditableInstance instance =
                                playerSession.getInstance().as(BranchingEditableInstance.class);
                        if (instance != null) {
                            BranchingRoomDefinition room =
                                    instance.getDungeon().getRoom(RoomDoorFunction.this.location);
                            if (room != null && !RoomDoorFunction.this.controlsAllDoors()) {
                                for (Connector conn : room.getConnectors()) {
                                    if (conn.getDoor().getNamespace().equals(RoomDoorFunction.this.doorName)) {
                                        return;
                                    }
                                }

                                LangUtils.sendMessage(player, "editor.function.room-door.warning-door-not-found");
                            }

                            this.menu.openFor(playerSession);
                        }
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
                        RoomDoorFunction.this.action =
                                Math.floorMod(RoomDoorFunction.this.action, actions.length);
                        this.button = new MenuButton(Material.DIAMOND);
                        this.button.setDisplayName("&d&lAction: " + actions[RoomDoorFunction.this.action]);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        RoomDoorFunction.this.action++;
                        if (RoomDoorFunction.this.action >= DoorAction.values().length) {
                            RoomDoorFunction.this.action = 0;
                        }

                        DoorAction[] actions = DoorAction.values();
                        DoorAction action =
                                actions[Math.floorMod(RoomDoorFunction.this.action, actions.length)];
                        this.menu.openFor(playerSession);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.room-door.action-set",
                                LangUtils.placeholder("action", action));
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        if (!RoomDoorFunction.this.controlsAllDoors()) {
                            this.button = null;
                        } else {
                            this.button = new MenuButton(Material.REDSTONE_TORCH);
                            this.button.setDisplayName("&d&lKeep Entrance Open");
                            this.button.addLore("&7Whether to keep the door the player");
                            this.button.addLore("&7entered from open.");
                            this.button.setEnchanted(RoomDoorFunction.this.keepEntranceOpen);
                        }
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!RoomDoorFunction.this.keepEntranceOpen) {
                            LangUtils.sendMessage(player, "editor.function.room-door.keep-entrance-open");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.room-door.do-not-keep-entrance-open");
                        }

                        RoomDoorFunction.this.keepEntranceOpen = !RoomDoorFunction.this.keepEntranceOpen;
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
     * Returns whether keep entrance open.
     */
    public boolean isKeepEntranceOpen() {
        return this.keepEntranceOpen;
    }

    /**
     * Performs controls all doors.
     */
    private boolean controlsAllDoors() {
        return "all".equalsIgnoreCase(this.doorName) || "any".equalsIgnoreCase(this.doorName);
    }
}

package nl.hauntedmc.dungeons.gui.menu;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.function.Supplier;
import java.util.function.IntSupplier;

/** Builds connector and door editor hotbars independently of layout generation. */
public final class ConnectorEditorMenus {
    private ConnectorEditorMenus() {}

    public static PlayerHotbarMenu connectorMenu(DungeonsRuntime runtime, Supplier<PlayerHotbarMenu> doorMenu) {
        PlayerHotbarMenu menu = PlayerHotbarMenu.createMenu();
        menu.addMenuItem(backFromConnector(runtime));
        menu.addMenuItem(editDoor(runtime, doorMenu));
        menu.addMenuItem(editAllowedRooms(runtime));
        menu.addMenuItem(copyConnector(runtime));
        menu.addMenuItem(cutConnector(runtime));
        menu.addMenuItem(pasteConnector(runtime));
        menu.addMenuItem(deleteConnector(runtime));
        return menu;
    }

    public static PlayerHotbarMenu doorMenu(DungeonsRuntime runtime, IntSupplier targetRange) {
        PlayerHotbarMenu menu = PlayerHotbarMenu.createMenu();
        menu.addMenuItem(backFromDoor(runtime));
        menu.addMenuItem(setDoorName(runtime));
        menu.addMenuItem(toggleDoorBlock(runtime, targetRange));
        menu.addMenuItem(toggleDoorOpen(runtime));
        menu.addMenuItem(toggleDoorSound(runtime));
        return menu;
    }

    private static MenuItem backFromConnector(DungeonsRuntime runtime) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                this.button.setDisplayName("&c&lBACK");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                playerSession.setActiveConnector(null);
                playerSession.setConfirmRoomAction(false);
                playerSession.restorePreviousHotbar(true);
            }
        };
    }

    private static MenuItem editDoor(DungeonsRuntime runtime, Supplier<PlayerHotbarMenu> doorMenu) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.OAK_DOOR);
                this.button.setDisplayName("&e&lEdit Door");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                BranchingRoomDefinition room = playerSession.getActiveRoom();
                Connector connector = playerSession.getActiveConnector();
                if (room != null && connector != null) {
                    ConnectorDoor door = connector.getDoor();
                    LangUtils.sendMessage(
                            player, "editor.layout.connector-door.open-menu-info");
                    playerSession.showHotbar(doorMenu.get(), true);
                    playerSession.setActiveDoor(door);
                }
            }
        };
    }

    private static MenuItem editAllowedRooms(DungeonsRuntime runtime) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.JIGSAW);
                this.button.setDisplayName("&e&lEdit Allowed Rooms");
                this.button.addLore("&eOpens a menu for customizing");
                this.button.addLore("&ea whitelist of what rooms can");
                this.button.addLore("&ebe connected to this one.");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                runtime.guiService().openGui(player, "connector_whitelist");
            }
        };
    }

    private static MenuItem copyConnector(DungeonsRuntime runtime) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.BOOK);
                this.button.setDisplayName("&3&lCopy Connector");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                if (playerSession.getActiveConnector() == null) {
                    LangUtils.sendMessage(player, "editor.layout.connector.no-selection");
                    return;
                }

                playerSession.beginConnectorCopy(playerSession.getActiveConnector());
                LangUtils.sendMessage(player, "editor.layout.connector.copied");
            }
        };
    }

    private static MenuItem cutConnector(DungeonsRuntime runtime) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.SHEARS);
                this.button.setDisplayName("&3&lCut Connector");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                if (playerSession.getActiveConnector() == null) {
                    LangUtils.sendMessage(player, "editor.layout.connector.no-selection");
                    return;
                }

                playerSession.beginConnectorCut(playerSession.getActiveConnector());
                LangUtils.sendMessage(player, "editor.layout.connector.cut");
            }
        };
    }

    private static MenuItem pasteConnector(DungeonsRuntime runtime) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.GLOBE_BANNER_PATTERN);
                this.button.setDisplayName("&3&lPaste Connector");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                if (event instanceof PlayerInteractEvent interactEvent) {
                    if (interactEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession =
                                runtime.playerSessions().get(player);
                        EditableInstance instance =
                                playerSession
                                        .getInstance()
                                        .as(BranchingEditableInstance.class);
                        if (instance != null) {
                            Location targetLocation =
                                    interactEvent.getClickedBlock().getLocation();
                            DungeonDefinition dungeon =
                                    instance.getDungeon().as(BranchingDungeon.class);
                            BranchingRoomDefinition room = playerSession.getActiveRoom();
                            if (dungeon != null && room != null) {
                                SimpleLocation simpleLoc =
                                        SimpleLocation.from(targetLocation);
                                if (playerSession.isConnectorCopying()) {
                                    if (room.getConnector(simpleLoc) != null) {
                                        LangUtils.sendMessage(
                                                player,
                                                "editor.layout.connector.already-here");
                                    } else {
                                        Connector connector =
                                                playerSession.getCopiedConnector();
                                        if (connector == null) {
                                            playerSession.clearConnectorClipboard();
                                            LangUtils.sendMessage(
                                                    player,
                                                    "editor.layout.connector.no-selection");
                                            return;
                                        }

                                        if (!room.applyDirectionAtEdge(simpleLoc)) {
                                            LangUtils.sendMessage(
                                                    player,
                                                    "editor.layout.connector.must-be-on-edge");
                                        } else {
                                            Connector copiedConnector =
                                                    connector.copy(simpleLoc);
                                            if (copiedConnector == null) {
                                                LangUtils.sendMessage(
                                                        player,
                                                        "editor.layout.connector.no-selection");
                                                return;
                                            }

                                            room.addConnector(copiedConnector);
                                            SimpleLocation.Direction oldDir =
                                                    connector.getLocation().getDirection();
                                            SimpleLocation.Direction newDir =
                                                    simpleLoc.getDirection();
                                            int rotation =
                                                    newDir.getDegrees()
                                                            - oldDir.getDegrees();
                                            Vector origin =
                                                    connector.getLocation().asVector();
                                            Vector dest = simpleLoc.asVector();
                                            Vector offset = dest.subtract(origin);
                                            copiedConnector.setDoor(
                                                    copiedConnector
                                                            .getDoor()
                                                            .copy(
                                                                    offset, origin,
                                                                    rotation));
                                            playerSession.setActiveConnector(
                                                    copiedConnector);
                                            LangUtils.sendMessage(
                                                    player,
                                                    "editor.layout.connector.pasted");
                                        }
                                    }
                                } else if (playerSession.isConnectorCutting()) {
                                    if (room.getConnector(simpleLoc) != null) {
                                        LangUtils.sendMessage(
                                                player,
                                                "editor.layout.connector.already-here");
                                        return;
                                    }

                                    Connector connector =
                                            playerSession.getCopiedConnector();
                                    if (connector == null) {
                                        playerSession.clearConnectorClipboard();
                                        LangUtils.sendMessage(
                                                player,
                                                "editor.layout.connector.no-selection");
                                        return;
                                    }

                                    if (!room.applyDirectionAtEdge(simpleLoc)) {
                                        LangUtils.sendMessage(
                                                player,
                                                "editor.layout.connector.must-be-on-edge");
                                        return;
                                    }

                                    SimpleLocation.Direction oldDir =
                                            connector.getLocation().getDirection();
                                    SimpleLocation.Direction newDir =
                                            simpleLoc.getDirection();
                                    int rotation =
                                            newDir.getDegrees() - oldDir.getDegrees();
                                    Vector origin = connector.getLocation().asVector();
                                    Vector dest = simpleLoc.asVector();
                                    Vector offset = dest.subtract(origin);
                                    connector.setLocation(simpleLoc);
                                    connector.setDoor(
                                            connector.getDoor() == null
                                                    ? new ConnectorDoor(connector)
                                                    : connector
                                                            .getDoor()
                                                            .copy(
                                                                    offset, origin,
                                                                    rotation));
                                    playerSession.setActiveConnector(connector);
                                    LangUtils.sendMessage(
                                            player, "editor.layout.connector.cut-pasted");
                                    playerSession.clearConnectorClipboard();
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    private static MenuItem deleteConnector(DungeonsRuntime runtime) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.BARRIER);
                this.button.setDisplayName("&c&lDelete Connector");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                BranchingRoomDefinition room = playerSession.getActiveRoom();
                Connector connector = playerSession.getActiveConnector();
                if (room != null && connector != null) {
                    if ((!connector.getRoomBlacklist().isEmpty()
                                    || !connector.getRoomWhitelist().isEmpty())
                            && !playerSession.isConfirmRoomAction()) {
                        LangUtils.sendMessage(
                                player, "editor.layout.connector.delete-confirm");
                        playerSession.setConfirmRoomAction(true);
                    } else {
                        playerSession.setConfirmRoomAction(false);
                        room.removeConnector(connector);
                        if (playerSession.isConnectorCutting()
                                && playerSession.getCopiedConnector() == connector) {
                            playerSession.clearConnectorClipboard();
                        }
                        playerSession.setActiveConnector(null);
                        playerSession.setActiveDoor(null);
                        LangUtils.sendMessage(player, "editor.layout.connector.removed");
                        playerSession.restorePreviousHotbar(true);
                    }
                }
            }
        };
    }

    private static MenuItem backFromDoor(DungeonsRuntime runtime) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                this.button.setDisplayName("&c&lBACK");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                playerSession.setActiveDoor(null);
                playerSession.restorePreviousHotbar(true);
            }
        };
    }

    private static MenuItem setDoorName(DungeonsRuntime runtime) {
        return new ChatMenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.NAME_TAG);
                this.button.setDisplayName("&d&lSet Door Name");
            }

            @Override
            public void onSelect(Player player) {
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                BranchingRoomDefinition room = playerSession.getActiveRoom();
                ConnectorDoor door = playerSession.getActiveDoor();
                if (room != null && door != null) {
                    LangUtils.sendMessage(player, "editor.layout.connector-door.ask-name");
                    LangUtils.sendMessage(
                            player,
                            "editor.layout.connector-door.current-name",
                            LangUtils.placeholder("name", door.getNamespace()));
                }
            }

            @Override
            public void onInput(Player player, String message) {
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                BranchingRoomDefinition room = playerSession.getActiveRoom();
                Connector connector = playerSession.getActiveConnector();
                if (room != null && connector != null) {
                    ConnectorDoor door = connector.getDoor();
                    if (door == null) {
                        door = new ConnectorDoor(connector);
                        connector.setDoor(door);
                    }

                    door.setNamespace(message);
                    LangUtils.sendMessage(
                            player,
                            "editor.layout.connector-door.name-set",
                            LangUtils.placeholder("name", message));
                    LangUtils.sendMessage(player, "editor.layout.connector-door.name-note");
                }
            }
        };
    }

    private static MenuItem toggleDoorBlock(DungeonsRuntime runtime, IntSupplier targetRange) {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.EMERALD);
                this.button.setDisplayName("&d&lAdd/Remove Block");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                Block block =
                        player.getTargetBlockExact(targetRange.getAsInt());
                if (block != null && !block.isEmpty()) {
                    DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                    BranchingRoomDefinition room = playerSession.getActiveRoom();
                    Connector connector = playerSession.getActiveConnector();
                    if (room != null && connector != null) {
                        ConnectorDoor door = connector.getDoor();
                        if (door == null) {
                            door = new ConnectorDoor(connector);
                            connector.setDoor(door);
                        }

                        SimpleLocation target = SimpleLocation.from(block);
                        if (door.getLocations().contains(target)) {
                            door.removeLocation(target);
                            LangUtils.sendMessage(
                                    player, "editor.layout.connector-door.block-removed");
                        } else {
                            door.addLocation(target);
                            LangUtils.sendMessage(
                                    player, "editor.layout.connector-door.block-added");
                        }
                    }
                }
            }
        };
    }

    private static MenuItem toggleDoorOpen(DungeonsRuntime runtime) {
        return new ToggleMenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.IRON_DOOR);
                this.button.setDisplayName("&d&lToggle Start Open");
            }

            @Override
            public void onSelect(Player player) {
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                BranchingRoomDefinition room = playerSession.getActiveRoom();
                Connector connector = playerSession.getActiveConnector();
                if (room != null && connector != null) {
                    ConnectorDoor door = connector.getDoor();
                    if (door == null) {
                        door = new ConnectorDoor(connector);
                        connector.setDoor(door);
                    }

                    if (!door.isStartOpen()) {
                        LangUtils.sendMessage(
                                player, "editor.layout.connector-door.start-open");
                    } else {
                        LangUtils.sendMessage(
                                player, "editor.layout.connector-door.start-closed");
                    }

                    door.setStartOpen(!door.isStartOpen());
                }
            }
        };
    }

    private static MenuItem toggleDoorSound(DungeonsRuntime runtime) {
        return new ToggleMenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.NOTE_BLOCK);
                this.button.setDisplayName("&d&lToggle Sound");
            }

            @Override
            public void onSelect(Player player) {
                DungeonPlayerSession playerSession = runtime.playerSessions().get(player);
                BranchingRoomDefinition room = playerSession.getActiveRoom();
                Connector connector = playerSession.getActiveConnector();
                if (room != null && connector != null) {
                    ConnectorDoor door = connector.getDoor();
                    if (door == null) {
                        door = new ConnectorDoor(connector);
                        connector.setDoor(door);
                    }

                    if (door.isDisableSound()) {
                        LangUtils.sendMessage(
                                player, "editor.layout.connector-door.sound-enabled");
                    } else {
                        LangUtils.sendMessage(
                                player, "editor.layout.connector-door.sound-disabled");
                    }

                    door.setDisableSound(!door.isDisableSound());
                }
            }
        };
    }
}

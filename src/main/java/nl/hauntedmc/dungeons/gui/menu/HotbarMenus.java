package nl.hauntedmc.dungeons.gui.menu;

import java.util.Optional;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.math.RangedNumber;
import nl.hauntedmc.dungeons.util.world.DirectionUtils;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.util.BoundingBox;

/**
 * Hotbar menu builders used by the dungeon editor.
 */
public class HotbarMenus {
    private static PlayerHotbarMenu functionEditMenu;
    private static PlayerHotbarMenu roomEditMenu;
    private static PlayerHotbarMenu roomRulesMenu;

    /** Builds the editor hotbar menu used for function editing actions. */
    public static void initializeFunctionEditMenu() {
        functionEditMenu = PlayerHotbarMenu.createMenu();
        functionEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.COMMAND_BLOCK);
                        this.button.setDisplayName("&a&lEdit Function");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        playerSession.showHotbar(playerSession.getActiveFunction().getMenu(), true);
                    }
                });
        functionEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
                        this.button.setDisplayName("&e&lChange Trigger");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "triggermenu");
                    }
                });
        functionEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.BOOK);
                        this.button.setDisplayName("&3&lCopy Function");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        playerSession.setCutting(false);
                        playerSession.setCopying(true);
                        playerSession.setCopiedFunction(playerSession.getActiveFunction());
                        LangUtils.sendMessage(player, "editor.session.hotbar.function-copied");
                    }
                });
        functionEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.SHEARS);
                        this.button.setDisplayName("&3&lCut Function");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        playerSession.setCopying(false);
                        playerSession.setCutting(true);
                        LangUtils.sendMessage(player, "editor.session.hotbar.function-cut");
                    }
                });
        functionEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.GLOBE_BANNER_PATTERN);
                        this.button.setDisplayName("&3&lPaste Function");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        if (event instanceof PlayerInteractEvent interactEvent) {
                            if (interactEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
                                Player player = event.getPlayer();
                                DungeonPlayerSession playerSession =
                                        RuntimeContext.playerSessions().get(player);
                                EditableInstance instance = playerSession.getInstance().asEditInstance();
                                if (instance != null) {
                                    Location targetLocation;
                                    if (interactEvent.getClickedBlock() != null) {
                                        targetLocation = interactEvent.getClickedBlock().getLocation();
                                    } else {
                                        LangUtils.sendMessage(player, "editor.session.hotbar.look-at-block-paste");
                                        return;
                                    }
                                    DungeonDefinition dungeon = instance.getDungeon();
                                    if (playerSession.isCopying()) {
                                        if (dungeon.getFunctions().containsKey(targetLocation)) {
                                            LangUtils.sendMessage(player, "editor.session.hotbar.function-already-here");
                                        } else {
                                            DungeonFunction copiedFunction = playerSession.getCopiedFunction().clone();
                                            dungeon.addFunction(targetLocation, copiedFunction);
                                            playerSession.setActiveFunction(copiedFunction);
                                            instance.addFunctionLabel(copiedFunction);
                                            LangUtils.sendMessage(player, "editor.session.hotbar.function-pasted");
                                        }
                                    } else {
                                        if (playerSession.isCutting()) {
                                            if (dungeon.getFunctions().containsKey(targetLocation)) {
                                                LangUtils.sendMessage(
                                                        player, "editor.session.hotbar.function-already-here");
                                                return;
                                            }

                                            instance.removeFunctionLabelByFunction(playerSession.getActiveFunction());
                                            dungeon.removeFunction(playerSession.getActiveFunction().getLocation());
                                            dungeon.addFunction(targetLocation, playerSession.getActiveFunction());
                                            instance.addFunctionLabel(dungeon.getFunctions().get(targetLocation));
                                            LangUtils.sendMessage(player, "editor.session.hotbar.function-cut-pasted");
                                            playerSession.setCutting(false);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
        functionEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&c&lDelete Function");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        EditableInstance instance = playerSession.getInstance().asEditInstance();
                        if (instance != null) {
                            instance.getDungeon().removeFunction(playerSession.getActiveFunction().getLocation());
                            instance.removeFunctionLabelByFunction(playerSession.getActiveFunction());
                            LangUtils.sendMessage(player, "editor.session.hotbar.function-deleted");
                            playerSession.restoreCapturedHotbar();
                        }
                    }
                });
    }

    /** Builds the editor hotbar menu used for room editing actions. */
    public static void initializeRoomEditMenu() {
        roomEditMenu = PlayerHotbarMenu.createMenu();
        roomEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.IRON_DOOR);
                        this.button.setDisplayName("&a&lEdit Connector");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        Block block = player.getTargetBlockExact(10);
                        if (block == null) {
                            LangUtils.sendMessage(player, "editor.session.hotbar.look-at-block-connector");
                        } else {
                            BranchingEditableInstance instance =
                                    playerSession.getInstance().as(BranchingEditableInstance.class);
                            if (instance != null) {
                                BranchingDungeon dungeon = instance.getDungeon();
                                BranchingRoomDefinition targetRoom = dungeon.getRoom(block.getLocation());
                                BranchingRoomDefinition activeRoom = playerSession.getActiveRoom();
                                SimpleLocation loc = SimpleLocation.from(block);
                                Connector connector = activeRoom.getConnector(loc);
                                if (connector == null) {
                                    if (targetRoom == null || targetRoom != activeRoom) {
                                        LangUtils.sendMessage(player, "editor.session.hotbar.block-not-in-room");
                                        return;
                                    }

                                    connector = activeRoom.addConnector(loc);
                                    if (connector == null) {
                                        LangUtils.sendMessage(player, "editor.session.hotbar.connector-edge-only");
                                        return;
                                    }

                                    LangUtils.sendMessage(player, "editor.session.hotbar.connector-added");
                                }

                                playerSession.setConfirmRoomAction(false);
                                playerSession.setActiveConnector(connector);
                                playerSession.showHotbar(dungeon.getLayout().getConnectorEditMenu(), true);
                            }
                        }
                    }
                });
        roomEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.NETHER_STAR);
                        this.button.setDisplayName("&e&lEdit Rules");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        playerSession.showHotbar(HotbarMenus.roomRulesMenu, true);
                    }
                });
        roomEditMenu.addMenuItem(
                                new ChatMenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.ENDER_EYE);
                        this.button.setDisplayName("&d&lDefault Weight");
                        this.button.addLore("&eHow likely this room will");
                        this.button.addLore("&egenerate relative to others.");
                    }

                    @Override
                                        public void onSelect(Player player) {
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            LangUtils.sendMessage(player, "editor.session.hotbar.room-weight-prompt");
                            LangUtils.sendMessage(
                                    player,
                                    "editor.session.hotbar.room-weight-current",
                                    LangUtils.placeholder(
                                            "weight", String.valueOf(MathUtils.round(room.getWeight(), 2))));
                        }
                    }

                    @Override
                                        public void onInput(Player player, String message) {
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            BranchingEditableInstance instance =
                                    playerSession.getInstance().as(BranchingEditableInstance.class);
                            if (instance != null) {
                                Optional<Double> value = InputUtils.readDoubleInput(player, message);
                                room.setWeight(value.orElse(room.getWeight()));
                                instance.setRoomLabel(room);
                                if (value.isPresent()) {
                                    double weight = value.get();
                                    LangUtils.sendMessage(
                                            player,
                                            "editor.session.hotbar.room-weight-set",
                                            LangUtils.placeholder("weight", String.valueOf(MathUtils.round(weight, 2))));
                                    LangUtils.sendMessage(player, "editor.session.hotbar.room-weight-note");
                                }
                            }
                        }
                    }
                });
        roomEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.RESPAWN_ANCHOR);
                        this.button.setDisplayName("&d&lSet Spawn");
                        this.button.addLore("&eMakes this room a valid starting");
                        this.button.addLore("&eroom and places the spawn point");
                        this.button.addLore("&eat the target location.");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            BranchingEditableInstance instance =
                                    playerSession.getInstance().as(BranchingEditableInstance.class);
                            if (instance != null) {
                                Block block = player.getTargetBlockExact(10);
                                if (block == null) {
                                    LangUtils.sendMessage(player, "editor.session.hotbar.look-at-block-spawn");
                                } else {
                                    Location target = block.getLocation();
                                    target.setWorld(null);
                                    target.add(0.5, 0.0, 0.5);
                                    if (target.equals(room.getSpawn())) {
                                        LangUtils.sendMessage(player, "editor.session.hotbar.room-spawn-removed");
                                        instance.removeRoomLabel(room);
                                        room.setSpawn(null);
                                        instance.setRoomLabel(room);
                                    } else {
                                        LangUtils.sendMessage(player, "editor.session.hotbar.room-spawn-set");
                                        if (room.getSpawn() == null) {
                                            LangUtils.sendMessage(
                                                    player, "editor.session.hotbar.room-spawn-added-starting");
                                        }

                                        instance.removeRoomLabel(room);
                                        room.setSpawn(block.getLocation().add(0.5, 0.0, 0.5));
                                        instance.setRoomLabel(room);
                                    }
                                }
                            }
                        }
                    }

                    @Override
                                        public void onClick(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            BranchingEditableInstance instance =
                                    playerSession.getInstance().as(BranchingEditableInstance.class);
                            if (instance != null) {
                                if (room.getSpawn() != null) {
                                    float yaw = (float) MathUtils.round(player.getYaw(), 0);
                                    room.getSpawn().setYaw(yaw);
                                    LangUtils.sendMessage(
                                            player,
                                            "editor.session.hotbar.room-spawn-direction-set",
                                            LangUtils.placeholder("yaw", String.valueOf(yaw)));
                                }
                            }
                        }
                    }
                });
        roomEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.CAMPFIRE);
                        this.button.setDisplayName("&3&lExpand & Shrink");
                        this.button.addLore("&eExpands or shrinks the room by one");
                        this.button.addLore("&ein the direction you're facing.");
                        this.button.addLore("");
                        this.button.addLore("&8Left-click expands.");
                        this.button.addLore("&8Right-click shrinks.");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            BranchingEditableInstance instance =
                                    playerSession.getInstance().as(BranchingEditableInstance.class);
                            if (instance != null) {
                                BlockFace facingDir = DirectionUtils.getFacingDirection(player);
                                instance.clearRoomDisplay(room);
                                instance.removeRoomLabel(room);
                                room.expand(facingDir, -1);
                                instance.setRoomLabel(room);
                                instance.displayRoomParticles(player, room);
                                LangUtils.sendMessage(
                                        player,
                                        "editor.session.hotbar.room-shrunk",
                                        LangUtils.placeholder("direction", facingDir.getOppositeFace().name()));
                            }
                        }
                    }

                    @Override
                                        public void onClick(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            BranchingEditableInstance instance =
                                    playerSession.getInstance().as(BranchingEditableInstance.class);
                            if (instance != null) {
                                instance.clearRoomDisplay(room);
                                BlockFace facingDir = DirectionUtils.getFacingDirection(player);
                                instance.removeRoomLabel(room);
                                room.expand(facingDir, 1);
                                instance.setRoomLabel(room);
                                instance.displayRoomParticles(player, room);
                                LangUtils.sendMessage(
                                        player,
                                        "editor.session.hotbar.room-expanded",
                                        LangUtils.placeholder("direction", facingDir.name()));
                            }
                        }
                    }
                });
        roomEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.DIAMOND_AXE);
                        this.button.setDisplayName("&d&lSelect Area");
                        this.button.addLore("&8Right-click selects corner 1");
                        this.button.addLore("&8Left-click selects corner 2");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            if (playerSession.getInstance() != null) {
                                BranchingEditableInstance instance =
                                        playerSession.getInstance().as(BranchingEditableInstance.class);
                                if (instance != null) {
                                    Block block = player.getTargetBlockExact(10);
                                    if (block == null) {
                                        LangUtils.sendMessage(player, "editor.session.room-click-required");
                                    } else {
                                        LangUtils.sendMessage(player, "editor.session.room-select-2");
                                        Location pos1 = block.getLocation();
                                        playerSession.setPos1(pos1);
                                        if (playerSession.getPos2() != null) {
                                            LangUtils.sendMessage(
                                                    player,
                                                    "editor.session.room-edited",
                                                    LangUtils.placeholder("room", room.getNamespace()));
                                            BoundingBox bounds =
                                                    LocationUtils.captureBoundingBox(
                                                            playerSession.getPos1(), playerSession.getPos2());
                                            instance.clearRoomDisplay(room);
                                            instance.removeRoomLabel(room);
                                            room.setBounds(player, bounds);
                                            instance.setRoomLabel(room);
                                            instance.displayRoomParticles(player, room);
                                            playerSession.setPos1(null);
                                            playerSession.setPos2(null);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Override
                                        public void onClick(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            if (playerSession.getInstance() != null) {
                                BranchingEditableInstance instance =
                                        playerSession.getInstance().as(BranchingEditableInstance.class);
                                if (instance != null) {
                                    Block block = player.getTargetBlockExact(10);
                                    if (block == null) {
                                        LangUtils.sendMessage(player, "editor.session.room-click-required");
                                    } else {
                                        LangUtils.sendMessage(player, "editor.session.room-select-1");
                                        Location pos2 = block.getLocation();
                                        playerSession.setPos2(pos2);
                                        if (playerSession.getPos1() != null) {
                                            LangUtils.sendMessage(
                                                    player,
                                                    "editor.session.room-edited",
                                                    LangUtils.placeholder("room", room.getNamespace()));
                                            BoundingBox bounds =
                                                    LocationUtils.captureBoundingBox(
                                                            playerSession.getPos1(), playerSession.getPos2());
                                            instance.clearRoomDisplay(room);
                                            instance.removeRoomLabel(room);
                                            room.setBounds(player, bounds);
                                            instance.setRoomLabel(room);
                                            instance.displayRoomParticles(player, room);
                                            playerSession.setPos1(null);
                                            playerSession.setPos2(null);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Override
                                        public void onUnhover(PlayerItemHeldEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        if (playerSession.getPos1() != null || playerSession.getPos2() != null) {
                            playerSession.setPos1(null);
                            playerSession.setPos2(null);
                            LangUtils.sendMessage(player, "editor.session.room-edit-cancelled");
                        }
                    }
                });
        roomEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&c&lDelete Room");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        if (!playerSession.isConfirmRoomAction()) {
                            LangUtils.sendMessage(player, "editor.session.hotbar.room-delete-confirm");
                            playerSession.setConfirmRoomAction(true);
                        } else {
                            playerSession.setConfirmRoomAction(false);
                            BranchingEditableInstance instance =
                                    playerSession.getInstance().as(BranchingEditableInstance.class);
                            if (instance != null) {
                                BranchingRoomDefinition room = playerSession.getActiveRoom();
                                if (room != null) {
                                    BranchingDungeon dungeon = instance.getDungeon();
                                    dungeon.removeRoom(room);
                                    instance.removeRoomLabel(room);

                                    for (DungeonFunction function : room.getFunctionsMapRelative().values()) {
                                        instance.removeFunctionLabelByFunction(function);
                                        instance.getFunctions().remove(function.getLocation());
                                    }

                                    instance.clearRoomDisplay(room);
                                    playerSession.setActiveRoom(null);
                                    LangUtils.sendMessage(player, "editor.session.hotbar.room-deleted");
                                    playerSession.restorePreviousHotbar();
                                }
                            }
                        }
                    }
                });
    }

    /** Builds the editor hotbar menu used for room-rule configuration actions. */
    public static void initializeRoomRulesMenu() {
        roomRulesMenu = PlayerHotbarMenu.createMenu();
        roomRulesMenu.addMenuItem(MenuItem.BACK);
        roomRulesMenu.addMenuItem(
                                new MenuItem() {
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
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        if (room != null) {
                            RuntimeContext.guiService()
                                    .openGui(
                                            player,
                                            "whitelist_" + room.getDungeon().getWorldName() + "_" + room.getNamespace());
                        }
                    }
                });
        roomRulesMenu.addMenuItem(
                                new ChatMenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.EMERALD);
                        this.button.setDisplayName("&d&lGeneration Limit");
                        this.button.addLore("&eHow many times this room should");
                        this.button.addLore("&egenerate in the dungeon.");
                        this.button.addLore("");
                        this.button.addLore("&dSupports ranges like 0-3, or 2+!");
                    }

                    @Override
                                        public void onSelect(Player player) {
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        LangUtils.sendMessage(player, "editor.session.hotbar.room-occurrences-prompt");
                        LangUtils.sendMessage(
                                player,
                                "editor.session.hotbar.current-value",
                                LangUtils.placeholder("value", room.getOccurrencesString()));
                    }

                    @Override
                                        public void onInput(Player player, String message) {
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingEditableInstance instance =
                                playerSession.getInstance().as(BranchingEditableInstance.class);
                        if (instance != null) {
                            BranchingRoomDefinition room = playerSession.getActiveRoom();
                            RangedNumber range = new RangedNumber(message);
                            instance.removeRoomLabel(room);
                            room.setOccurrencesString(message);
                            room.setOccurrences(range);
                            instance.setRoomLabel(room);
                            LangUtils.sendMessage(
                                    player,
                                    "editor.session.hotbar.room-occurrences-set",
                                    LangUtils.placeholder("occurrences", message));
                        }
                    }
                });
        roomRulesMenu.addMenuItem(
                                new ChatMenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.COMPASS);
                        this.button.setDisplayName("&d&lGeneration Depth");
                        this.button.addLore("&eHow many rooms deep into the dungeon");
                        this.button.addLore("&ethis room should generate.");
                        this.button.addLore("");
                        this.button.addLore("&dSupports ranges like 0-3, or 2+!");
                    }

                    @Override
                                        public void onSelect(Player player) {
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        LangUtils.sendMessage(player, "editor.session.hotbar.room-depth-prompt");
                        LangUtils.sendMessage(
                                player,
                                "editor.session.hotbar.current-value",
                                LangUtils.placeholder("value", room.getDepthString()));
                    }

                    @Override
                                        public void onInput(Player player, String message) {
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        BranchingEditableInstance instance =
                                playerSession.getInstance().as(BranchingEditableInstance.class);
                        if (instance != null) {
                            BranchingRoomDefinition room = playerSession.getActiveRoom();
                            RangedNumber range = new RangedNumber(message);
                            instance.removeRoomLabel(room);
                            room.setDepthString(message);
                            room.setDepth(range);
                            instance.setRoomLabel(room);
                            LangUtils.sendMessage(
                                    player,
                                    "editor.session.hotbar.room-depth-set",
                                    LangUtils.placeholder("depth", message));
                        }
                    }
                });
    }

    /** Returns the initialized function-edit hotbar menu. */
    public static PlayerHotbarMenu getFunctionEditMenu() {
        return functionEditMenu;
    }

    /** Returns the initialized room-edit hotbar menu. */
    public static PlayerHotbarMenu getRoomEditMenu() {
        return roomEditMenu;
    }
}

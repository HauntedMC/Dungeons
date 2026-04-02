package nl.hauntedmc.dungeons.api.generation.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.generation.rooms.Connector;
import nl.hauntedmc.dungeons.api.generation.rooms.ConnectorDoor;
import nl.hauntedmc.dungeons.api.generation.rooms.DungeonRoomContainer;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.generation.rooms.RotatedRoom;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.DungeonPlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public abstract class Layout implements Cloneable {
   protected final DungeonProcedural dungeon;
   protected final YamlConfiguration config;
   protected DungeonPlayerHotbarMenu connectorEditMenu;
   protected DungeonPlayerHotbarMenu connectorDoorMenu;
   protected Material closedConnectorBlock;
   protected int minRooms;
   protected int maxRooms;
   protected boolean strictRooms;
   protected List<DungeonRoomContainer> roomWhitelist = new ArrayList<>();
   protected int roomCount;
   protected int totalRooms;
   protected Map<DungeonRoomContainer, Integer> countsByRoom = new HashMap<>();
   protected Map<Integer, DungeonRoomContainer> requiredRooms = new HashMap<>();
   protected List<BoundingBox> roomAreas = new ArrayList<>();
   protected Map<SimpleLocation, InstanceRoom> roomAnchors = new HashMap<>();
   protected InstanceRoom first;
   protected boolean cancelled = false;
   protected Queue<InstanceRoom> queue = new ArrayDeque<>();
   protected int queuedRoomCount;
   protected List<InstanceRoom> roomsToAdd;
   protected Map<DungeonRoomContainer, Integer> queuedCountsByRoom;
   protected List<BoundingBox> queuedRoomAreas;
   protected Map<SimpleLocation, InstanceRoom> queuedRoomAnchors;
   protected InstanceRoom generationCheckpoint;

   public Layout(DungeonProcedural dungeon, YamlConfiguration config) {
      this.dungeon = dungeon;
      this.config = config;
      this.initConnectorEditMenu();

      try {
         this.closedConnectorBlock = Material.valueOf(config.getString("General.SurroundingBlock", "STONE"));
         if (!this.closedConnectorBlock.isBlock()) {
            throw new IllegalArgumentException("The provided surrounding material is not a block!");
         }
      } catch (IllegalArgumentException var4) {
         Dungeons.inst()
            .getLogger()
            .info(
               HelperUtils.colorize(
                  "&cWARNING :: SurroundingBlock in dungeon " + dungeon.getWorldName() + "'s generator.yml must be a valid block! Using STONE by default..."
               )
            );
         this.closedConnectorBlock = Material.STONE;
      }

      this.minRooms = config.getInt("General.MinRooms", 8);
      this.maxRooms = config.getInt("General.MaxRooms", 16);
      this.strictRooms = config.getBoolean("General.StrictRoomCount", true);
   }

   protected abstract boolean tryConnectors(InstanceRoom var1);

   protected boolean tryConnector(Connector connector, InstanceRoom from) {
      RandomCollection<DungeonRoomContainer> weightedRooms = this.filterRooms(connector, from);
      if (weightedRooms.size() <= 0) {
         return false;
      } else {
         SimpleLocation adjusted = connector.getLocation().clone();
         adjusted.shift(1.0);
         InstanceRoom roomInst = this.findRoom(from.getSource().getOrigin(), connector, adjusted, weightedRooms);
         if (roomInst == null) {
            return false;
         } else {
            roomInst.setDepth(from.getDepth() + 1);
            this.queueRoom(roomInst);
            return true;
         }
      }
   }

   protected boolean verifyLayout() {
      for (DungeonRoomContainer room : this.dungeon.getUniqueRooms().values()) {
         if (this.countsByRoom.getOrDefault(room, 0) < room.getOccurrences().getMin()) {
            Dungeons.inst()
               .getLogger()
               .warning(
                  HelperUtils.colorize(
                     "&cROOM REQUIREMENTS FAILED: "
                        + room.getNamespace()
                        + " ("
                        + this.countsByRoom.getOrDefault(room, 0)
                        + "/"
                        + room.getOccurrences().getMin()
                        + ")"
                  )
               );
            return false;
         }
      }

      return true;
   }

   public Layout.GenerationResult generate() {
      int timeout = Dungeons.inst().getConfig().getInt("Generator.Timeout", 5);
      long startTime = System.currentTimeMillis();
      if (this.dungeon.getStartRooms().isEmpty()) {
         Dungeons.inst().getLogger().warning(HelperUtils.colorize("== WARNING :: Your dungeon doesn't have any spawn rooms! =="));
         Dungeons.inst().getLogger().warning(HelperUtils.colorize("We'll do our best to find a valid spawn point, but it's recommended to define spawn rooms!"));
         Dungeons.inst().getLogger().warning(HelperUtils.colorize("Any room with a spawn point configured will be considered a valid spawn room."));
      }

      for (DungeonRoomContainer room : this.dungeon.getUniqueRooms().values()) {
         if (room.getConnectors().isEmpty()) {
            Dungeons.inst()
               .getLogger()
               .warning(HelperUtils.colorize("== WARNING :: Room '" + room.getNamespace() + "' has no connectors! It won't generate! =="));
         }
      }

      if (this instanceof LayoutBranching && this.config.getConfigurationSection("BranchSettings.TRUNK") == null) {
         Dungeons.inst().getLogger().warning("== WARNING :: This dungeon doesn't have a configured 'TRUNK' branch! Default settings will be used. ==");
      }

      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<Layout.GenerationResult> future = executor.submit(() -> {
         int tries = 0;

         do {
            Layout.GenerationResult resultx = this.tryLayout();
            if (resultx == Layout.GenerationResult.NO_START_ROOM) {
               return resultx;
            }

            if (resultx.passed) {
               long runTime = System.currentTimeMillis() - startTime;
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&aGenerated dungeon layout in " + runTime + "ms! Took " + tries + " tries."));
               return Layout.GenerationResult.SUCCESS;
            }

            tries++;
         } while (System.currentTimeMillis() - startTime <= timeout * 1000L);

         Dungeons.inst().getLogger().severe(HelperUtils.colorize("-- Could not find valid layout after " + tries + " tries... --"));
         return Layout.GenerationResult.TIMED_OUT;
      });

      Layout.GenerationResult result;
      try {
         result = future.get(timeout, TimeUnit.SECONDS);
      } catch (InterruptedException | TimeoutException | ExecutionException var8) {
         future.cancel(true);
         result = Layout.GenerationResult.GENERIC_FAILURE;
         this.cancelled = true;
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var8.getMessage());
      }

      executor.shutdownNow();
      return result;
   }

   protected Layout.GenerationResult tryLayout() {
      Dungeons.inst().getLogger().info(HelperUtils.colorize("&d== GENERATING NEW DUNGEON LAYOUT =="));


      this.roomCount = 0;
      this.totalRooms = 0;
      this.countsByRoom.clear();
      this.roomAreas.clear();
      this.roomAnchors.clear();
      if (!this.selectFirstRoom()) {
         return Layout.GenerationResult.NO_START_ROOM;
      } else {
         this.processQueue();

         for (InstanceRoom room : this.roomsToAdd) {
            this.addRoom(room);
         }

         if (!this.verifyLayout()) {
            return Layout.GenerationResult.BAD_LAYOUT;
         } else {
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&a== FOUND VALID DUNGEON LAYOUT! =="));
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&a" + this.totalRooms + " total rooms."));
            return Layout.GenerationResult.SUCCESS;
         }
      }
   }

   protected boolean selectFirstRoom() {
      if (this.dungeon.getUniqueRooms().isEmpty()) {
         Dungeons.inst().getLogger().severe("ERROR :: Dungeon has no rooms!!");
         return false;
      } else {
         List<DungeonRoomContainer> possibleRooms = new ArrayList<>(this.dungeon.getStartRooms().values());
         if (possibleRooms.isEmpty()) {
            possibleRooms = new ArrayList<>(this.dungeon.getUniqueRooms().values());
         }

         if (!this.roomWhitelist.isEmpty()) {
            possibleRooms.removeIf(roomx -> !this.roomWhitelist.contains(roomx));
         }

         RandomCollection<DungeonRoomContainer> weightedRooms = new RandomCollection<>();

         for (DungeonRoomContainer room : possibleRooms) {
            if (!(room.getDepth().getMin() > 0.0)) {
               weightedRooms.add(room.getWeight() * (room.getOccurrences().getMin() + 1.0), room);
            }
         }

         DungeonRoomContainer first = weightedRooms.next();
         if (first == null) {
            return false;
         } else {
            this.addRoom(new SimpleLocation(0.0, 128.0, 0.0), first.getRandomOrientation());
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&dSelected first room: " + first.getNamespace()));
            return true;
         }
      }
   }

   protected Layout.GenerationResult processQueue() {
      this.roomsToAdd = new ArrayList<>();
      this.queuedRoomCount = this.roomCount;
      this.queuedCountsByRoom = new HashMap<>(this.countsByRoom);
      this.queuedRoomAreas = new ArrayList<>(this.roomAreas);
      this.queuedRoomAnchors = new HashMap<>(this.roomAnchors);
      return this.processQueue(0);
   }

   protected Layout.GenerationResult processQueue(int checkpointDepth) {
      this.generationCheckpoint = null;
      int timeout = Dungeons.inst().getConfig().getInt("Generator.Timeout", 5);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<?> future = executor.submit(() -> {
            int rooms = 0;

            while (!this.queue.isEmpty() && !this.cancelled) {
               InstanceRoom room = this.queue.poll();
               if (!this.getAllRooms().contains(room)) {
                  this.roomsToAdd.add(room);
               }

               this.tryConnectors(room);
               if (checkpointDepth > 0 && ++rooms >= checkpointDepth) {
                  this.generationCheckpoint = this.queue.peek();
                  this.queue.clear();
                  break;
               }
            }
         });
         future.get(timeout, TimeUnit.SECONDS);
      } catch (Exception var5) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var5.getMessage());
         return Layout.GenerationResult.GENERIC_FAILURE;
      }

      executor.shutdownNow();
      return Layout.GenerationResult.SUCCESS;
   }

   protected abstract RandomCollection<DungeonRoomContainer> filterRooms(Connector var1, InstanceRoom var2);

   @Nullable
   protected InstanceRoom findRoom(
      DungeonRoomContainer from, Connector connector, SimpleLocation position, RandomCollection<DungeonRoomContainer> weightedRooms
   ) {
      List<RotatedRoom> orientations = new ArrayList<>();
      RotatedRoom to = null;
      List<DungeonRoomContainer> invalidRooms = new ArrayList<>();
      DungeonRoomContainer nextRoom = null;
      InstanceRoom roomInst = null;
      boolean foundRoom = false;

      while (!foundRoom) {
         orientations.remove(to);
         Map<RotatedRoom, List<Connector>> validConnectors = new HashMap<>();
         if (orientations.isEmpty()) {
            if (nextRoom != null) {
               invalidRooms.add(nextRoom);
            }

            if (invalidRooms.size() == weightedRooms.size()) {
               break;
            }

            nextRoom = weightedRooms.next();
            orientations = new ArrayList<>();
            validConnectors = new HashMap<>();

            for (RotatedRoom room : nextRoom.getOrientations()) {
               boolean foundConnector = false;

               for (Connector nextConnector : room.getConnectors()) {
                  if (connector.getLocation().getDirection().isOpposite(nextConnector.getLocation().getDirection()) && nextConnector.canGenerate(from)) {
                     foundConnector = true;
                     validConnectors.computeIfAbsent(room, k -> new ArrayList<>()).add(nextConnector);
                  }
               }

               if (validConnectors.get(room) != null) {
                  Collections.shuffle(validConnectors.get(room));
               }

               if (foundConnector) {
                  orientations.add(room);
               }
            }
         }

         if (!orientations.isEmpty()) {
            to = orientations.get(MathUtils.getRandomNumberInRange(0, orientations.size() - 1));
            if (validConnectors.get(to) != null) {
               for (Connector nextConnectorx : validConnectors.get(to)) {
                  roomInst = new InstanceRoom(to, position, nextConnectorx.getLocation());
                  if (this.canRoomGenerate(roomInst)) {
                     foundRoom = true;
                     break;
                  }

                  roomInst = null;
               }
            }
         }
      }

      return roomInst;
   }

   protected InstanceRoom addRoom(SimpleLocation anchor, RotatedRoom room) {
      SimpleLocation offset = new SimpleLocation(0.0, 0.0, 0.0);
      offset.shift(room.getBounds().getMin());
      InstanceRoom inst = new InstanceRoom(room, anchor, offset);
      if (this.roomAnchors.isEmpty()) {
         this.first = inst;
      }

      this.countsByRoom.merge(room.getOrigin(), 1, Integer::sum);
      this.roomAnchors.put(anchor, inst);
      this.roomAreas.add(inst.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
      this.roomCount++;
      this.totalRooms++;
      this.queue.add(inst);
      return inst;
   }

   protected void addRoom(InstanceRoom room) {
      if (this.roomAnchors.isEmpty()) {
         this.first = room;
      }

      this.countsByRoom.merge(room.getSource().getOrigin(), 1, Integer::sum);
      this.roomAnchors.put(room.getAnchor(), room);
      this.roomAreas.add(room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
      this.roomCount++;
      this.totalRooms++;
   }

   protected void queueRoom(InstanceRoom room) {
      this.queue.add(room);
      this.queuedCountsByRoom.merge(room.getSource().getOrigin(), 1, Integer::sum);
      this.queuedRoomAnchors.put(room.getAnchor(), room);
      this.queuedRoomAreas.add(room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
      this.queuedRoomCount++;
   }

   protected int getRoomCount(DungeonRoomContainer room) {
      return this.queuedCountsByRoom.getOrDefault(room, 0);
   }

   public boolean isRoomMaxedOut(DungeonRoomContainer room) {
      if (room.getOccurrences().getMax() == 0.0) {
         return false;
      } else {
         int count = this.getRoomCount(room);
         int max = (int)room.getOccurrences().getMax();
         return count >= (max == -1 ? Integer.MAX_VALUE : max);
      }
   }

   public boolean canRoomGenerate(InstanceRoom room) {
      BoundingBox roomBox = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

      for (BoundingBox area : this.roomAreas) {
         if (area.overlaps(roomBox)) {
            return false;
         }
      }

      for (BoundingBox areax : this.queuedRoomAreas) {
         if (areax.overlaps(roomBox)) {
            return false;
         }
      }

      return true;
   }

   public Collection<InstanceRoom> getAllRooms() {
      return this.roomAnchors.values();
   }

   public void initConnectorEditMenu() {
      this.connectorEditMenu = DungeonPlayerHotbarMenu.create();
      this.connectorEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
            this.button.setDisplayName("&c&lBACK");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setActiveConnector(null);
            aPlayer.setConfirmRoomAction(false);
            aPlayer.previousHotbar(true);
         }
      });
      this.connectorEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.OAK_DOOR);
            this.button.setDisplayName("&e&lEdit Door");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            DungeonRoomContainer room = aPlayer.getActiveRoom();
            Connector connector = aPlayer.getActiveConnector();
            if (room != null && connector != null) {
               ConnectorDoor door = connector.getDoor();
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&bBuild and configure a custom door for use with functions and mechanics!"));
               aPlayer.setHotbar(Layout.this.connectorDoorMenu, true);
               aPlayer.setActiveDoor(door);
            }
         }
      });
      this.connectorEditMenu.addMenuItem(new MenuItem() {
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
            Dungeons.inst().getGuiApi().openGUI(player, "connector_whitelist");
         }
      });
      this.connectorEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BOOK);
            this.button.setDisplayName("&3&lCopy Connector");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setCutting(false);
            aPlayer.setCopying(true);
            aPlayer.setCopiedConnector(aPlayer.getActiveConnector());
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eConnector copied!"));
         }
      });
      this.connectorEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.SHEARS);
            this.button.setDisplayName("&3&lCut Connector");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setCopying(false);
            aPlayer.setCutting(true);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eConnector cut!"));
         }
      });
      this.connectorEditMenu.addMenuItem(new MenuItem() {
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
                  DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                  InstanceEditable instance = aPlayer.getInstance().as(InstanceEditableProcedural.class);
                  if (instance != null) {
                     Location targetLocation = interactEvent.getClickedBlock().getLocation();
                     AbstractDungeon dungeon = instance.getDungeon().as(DungeonProcedural.class);
                     DungeonRoomContainer room = aPlayer.getActiveRoom();
                     if (dungeon != null && room != null) {
                        SimpleLocation simpleLoc = SimpleLocation.from(targetLocation);
                        if (aPlayer.isCopying()) {
                           if (room.getConnector(simpleLoc) != null) {
                              player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cThere is already a connector here!"));
                           } else {
                              Connector connector = aPlayer.getCopiedConnector();
                              if (!room.applyDirectionAtEdge(simpleLoc)) {
                                 player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cRoom connectors must be added to the edge of a room!"));
                              } else {
                                 Connector copiedConnector = connector.copy(simpleLoc);
                                 room.addConnector(copiedConnector);
                                 copiedConnector.setSuccessChance(connector.getSuccessChance());
                                 copiedConnector.setRoomWhitelist(new ArrayList<>(connector.getRoomWhitelist()));
                                 copiedConnector.setRoomBlacklist(new ArrayList<>(connector.getRoomBlacklist()));
                                 SimpleLocation.Direction oldDir = connector.getLocation().getDirection();
                                 SimpleLocation.Direction newDir = simpleLoc.getDirection();
                                 int rotation = newDir.getDegrees() - oldDir.getDegrees();
                                 Vector origin = connector.getLocation().asVector();
                                 Vector dest = simpleLoc.asVector();
                                 Vector offset = dest.subtract(origin);
                                 copiedConnector.setDoor(copiedConnector.getDoor().copy(offset, origin, rotation));
                                 aPlayer.setActiveConnector(copiedConnector);
                                 player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&ePasted connector to the clicked block!"));
                              }
                           }
                        } else {
                           if (aPlayer.isCutting()) {
                              if (room.getConnector(simpleLoc) != null) {
                                 player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cThere is already a connector here!"));
                                 return;
                              }

                              Connector connector = aPlayer.getActiveConnector();
                              room.applyDirectionAtEdge(simpleLoc);
                              connector.setLocation(simpleLoc);
                              player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCut-and-pasted connector to the clicked block!"));
                              aPlayer.setCutting(false);
                           }
                        }
                     }
                  }
               }
            }
         }
      });
      this.connectorEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&c&lDelete Connector");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            DungeonRoomContainer room = aPlayer.getActiveRoom();
            Connector connector = aPlayer.getActiveConnector();
            if (room != null && connector != null) {
               if ((!connector.getRoomBlacklist().isEmpty() || !connector.getRoomWhitelist().isEmpty()) && !aPlayer.isConfirmRoomAction()) {
                  player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eAre you sure you want to delete this connector? Click again to confirm."));
                  aPlayer.setConfirmRoomAction(true);
               } else {
                  aPlayer.setConfirmRoomAction(false);
                  room.removeConnector(connector);
                  player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cRemoved connector."));
                  aPlayer.previousHotbar(true);
               }
            }
         }
      });
      this.initConnectorDoorMenu();
   }

   public void initConnectorDoorMenu() {
      this.connectorDoorMenu = DungeonPlayerHotbarMenu.create();
      this.connectorDoorMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
            this.button.setDisplayName("&c&lBACK");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setActiveDoor(null);
            aPlayer.previousHotbar(true);
         }
      });
      this.connectorDoorMenu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.NAME_TAG);
                  this.button.setDisplayName("&d&lSet Door Name");
               }

               @Override
               public void onSelect(Player player) {
                  DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                  DungeonRoomContainer room = aPlayer.getActiveRoom();
                  ConnectorDoor door = aPlayer.getActiveDoor();
                  if (room != null && door != null) {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat is the name of this door?"));
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent name: &6" + door.getNamespace()));
                  }
               }

               @Override
               public void onInput(Player player, String message) {
                  DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                  DungeonRoomContainer room = aPlayer.getActiveRoom();
                  Connector connector = aPlayer.getActiveConnector();
                  if (room != null && connector != null) {
                     ConnectorDoor door = connector.getDoor();
                     if (door == null) {
                        door = new ConnectorDoor(connector);
                        connector.setDoor(door);
                     }

                     door.setNamespace(message);
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet this door's name to '&6" + message + "&a'"));
                     player.sendMessage(
                        HelperUtils.colorize(Dungeons.logPrefix + "&bNOTE: You can use this name to open and close the door with functions and mechanics!")
                     );
                  }
               }
            }
         );
      this.connectorDoorMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.EMERALD);
            this.button.setDisplayName("&d&lAdd/Remove Block");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            Block block = player.getTargetBlockExact(10);
            if (block != null && !block.isEmpty()) {
               DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
               DungeonRoomContainer room = aPlayer.getActiveRoom();
               Connector connector = aPlayer.getActiveConnector();
               if (room != null && connector != null) {
                  ConnectorDoor door = connector.getDoor();
                  if (door == null) {
                     door = new ConnectorDoor(connector);
                     connector.setDoor(door);
                  }

                  SimpleLocation target = SimpleLocation.from(block);
                  if (door.getLocations().contains(target)) {
                     door.removeLocation(target);
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&cRemoved the target block from the list of door blocks."));
                  } else {
                     door.addLocation(target);
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aAdded the target block to the list of door blocks."));
                  }
               }
            }
         }
      });
      this.connectorDoorMenu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.IRON_DOOR);
            this.button.setDisplayName("&d&lToggle Start Open");
         }

         @Override
         public void onSelect(Player player) {
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            DungeonRoomContainer room = aPlayer.getActiveRoom();
            Connector connector = aPlayer.getActiveConnector();
            if (room != null && connector != null) {
               ConnectorDoor door = connector.getDoor();
               if (door == null) {
                  door = new ConnectorDoor(connector);
                  connector.setDoor(door);
               }

               if (!door.isStartOpen()) {
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Door will start &bOPEN &6(if there's an adjacent room)&a'"));
               } else {
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Door will start &cCLOSED&a'"));
               }

               door.setStartOpen(!door.isStartOpen());
            }
         }
      });
      this.connectorDoorMenu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NOTE_BLOCK);
            this.button.setDisplayName("&d&lToggle Sound");
         }

         @Override
         public void onSelect(Player player) {
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            DungeonRoomContainer room = aPlayer.getActiveRoom();
            Connector connector = aPlayer.getActiveConnector();
            if (room != null && connector != null) {
               ConnectorDoor door = connector.getDoor();
               if (door == null) {
                  door = new ConnectorDoor(connector);
                  connector.setDoor(door);
               }

               if (door.isDisableSound()) {
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Play sound when door opens/closes&a'"));
               } else {
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&cDO NOT &6play sound when door opens/closes&a'"));
               }

               door.setDisableSound(!door.isDisableSound());
            }
         }
      });
   }

   public Layout clone() {
      try {
         Layout clone = (Layout)super.clone();
         clone.countsByRoom = new HashMap<>();
         clone.requiredRooms = new HashMap<>();
         clone.roomAreas = new ArrayList<>();
         clone.roomAnchors = new HashMap<>();
         return clone;
      } catch (CloneNotSupportedException var2) {
         throw new AssertionError();
      }
   }

   public DungeonPlayerHotbarMenu getConnectorEditMenu() {
      return this.connectorEditMenu;
   }

   public Material getClosedConnectorBlock() {
      return this.closedConnectorBlock;
   }

   public InstanceRoom getFirst() {
      return this.first;
   }

   public enum GenerationResult {
      SUCCESS(true),
      NO_START_ROOM(false, "No valid start room was found!"),
      TIMED_OUT(false, "Dungeon layout took too long to generate!"),
      BAD_LAYOUT(false, "Dungeon layout didn't generate with the necessary rooms!"),
      GENERIC_FAILURE(false, "Failed to generate layout."),
      CANCELLED(false, "Generation was cancelled.");

      private final boolean passed;
      private final String msg;

      GenerationResult(boolean passed) {
         this(passed, "");
      }

      GenerationResult(boolean passed, String msg) {
         this.passed = passed;
         this.msg = msg;
      }

      public boolean isPassed() {
         return this.passed;
      }

      public String getMsg() {
         return this.msg;
      }
   }
}

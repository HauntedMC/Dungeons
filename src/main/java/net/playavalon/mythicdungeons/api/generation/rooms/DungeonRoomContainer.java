package net.playavalon.mythicdungeons.api.generation.rooms;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.config.AsyncConfiguration;
import net.playavalon.mythicdungeons.api.generation.StructurePiece;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.gui.RoomGUIHandler;
import net.playavalon.mythicdungeons.utility.SimpleLocation;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.SchematicUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.numbers.RangedNumber;
import net.playavalon.mythicdungeons.utility.tasks.ProcessTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

public class DungeonRoomContainer {
   private final DungeonProcedural dungeon;
   private final String namespace;
   private final File configFile;
   private final AsyncConfiguration roomConfig;
   private File schemFile;
   private WeakReference<StructurePiece> schematic;
   private List<RotatedRoom> rooms = new ArrayList<>();
   private boolean changedSinceLastSave;
   private boolean saving = false;
   private BoundingBox bounds;
   private String occurrencesString;
   private RangedNumber occurrences;
   private String depthString;
   private RangedNumber depth;
   private double weight = 1.0;
   private Location spawn;
   private List<WhitelistEntry> roomBlacklist = new ArrayList<>();
   private List<WhitelistEntry> roomWhitelist = new ArrayList<>();
   private List<Connector> connectors = new ArrayList<>();
   private Map<Location, DungeonFunction> functionsMapRelative = new HashMap<>();

   public StructurePiece getSchematic() {
      if (this.schematic != null && this.schematic.get() != null) {
         return this.schematic.get();
      } else {
         try {
            this.schematic = new WeakReference<>(SchematicUtils.loadStructurePiece(this.schemFile));
            return this.schematic.get();
         } catch (IOException var2) {
            var2.printStackTrace();
            return null;
         }
      }
   }

   public DungeonRoomContainer(DungeonProcedural dungeon, String namespace, BoundingBox bounds) {
      this.dungeon = dungeon;
      this.namespace = namespace;
      this.bounds = bounds;
      this.roomConfig = new AsyncConfiguration(MythicDungeons.inst());
      this.configFile = new File(dungeon.getFolder(), "rooms/" + namespace + ".yml");
      this.schemFile = new File(dungeon.getFolder(), "rooms/" + namespace + ".struct");
      this.occurrences = new RangedNumber("0+");
      this.occurrencesString = this.occurrences.toIntString();
      this.depth = new RangedNumber("0+");
      this.depthString = this.depth.toIntString();
      Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> this.captureSchematic(dungeon.getEditSession().getInstanceWorld()));
      RoomGUIHandler.initRoomWhitelist(dungeon, this);
      this.rooms.add(new RotatedRoom(this, 0));
      this.rooms.add(new RotatedRoom(this, 90));
      this.rooms.add(new RotatedRoom(this, 180));
      this.rooms.add(new RotatedRoom(this, 270));
   }

   public DungeonRoomContainer(DungeonProcedural dungeon, File configFile) throws InvalidConfigurationException {
      this.dungeon = dungeon;
      this.configFile = configFile;
      this.namespace = configFile.getName().replace(".yml", "");
      this.schemFile = new File(dungeon.getFolder(), "rooms/" + this.namespace + ".struct");
      this.roomConfig = new AsyncConfiguration(MythicDungeons.inst());
      this.loadRoom(configFile);
      RoomGUIHandler.initRoomWhitelist(dungeon, this);
      this.rooms.add(new RotatedRoom(this, 0));
      this.rooms.add(new RotatedRoom(this, 90));
      this.rooms.add(new RotatedRoom(this, 180));
      this.rooms.add(new RotatedRoom(this, 270));
   }

   public void addToBlacklist(DungeonRoomContainer room) {
   }

   public void addToWhitelist(DungeonRoomContainer room) {
   }

   public void setBounds(Player player, BoundingBox bounds) {
      this.bounds = bounds;
      BoundingBox scaled = bounds.clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

      for (Connector connector : this.connectors) {
         if (!scaled.contains(connector.getLocation().asVector())) {
            LangUtils.sendMessage(player, "instance.editmode.room-connectors-outside");
            break;
         }
      }

      this.changedSinceLastSave = true;
   }

   public void expand(BlockFace face, int amount) {
      this.bounds.expand(face, amount);
      SimpleLocation.Direction direction = SimpleLocation.Direction.fromBlockFace(face);

      for (Connector connector : this.connectors) {
         if (connector.getLocation().getDirection() == direction) {
            connector.getLocation().shift(amount);
         }
      }

      this.changedSinceLastSave = true;
   }

   public void setSpawn(Location loc) {
      this.spawn = loc;
      if (loc != null) {
         this.spawn.setWorld(null);
      }
   }

   public void addFunction(Location loc, DungeonFunction function) {
      this.functionsMapRelative.put(loc, function);
   }

   public void removeFunction(Location loc) {
      this.functionsMapRelative.remove(loc);
   }

   public void saveFunctions() {
      this.roomConfig.set("Functions", new ArrayList<>(this.functionsMapRelative.values()));
      if (MythicDungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskAsynchronously(MythicDungeons.inst(), this::saveRoom);
      } else {
         this.saveRoom();
      }
   }

   public void loadFunctions() {
      List<DungeonFunction> functions = (List<DungeonFunction>) this.roomConfig.getList("Functions");
      if (functions != null) {
         for (DungeonFunction function : functions) {
            function.init();
            Location loc = function.getLocation();
            if (!this.bounds.clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
               MythicDungeons.inst().getLogger().warning("WARNING :: Room " + this.namespace + " contains a function out-of-bounds!");
               MythicDungeons.inst()
                  .getLogger()
                  .info(Util.colorize("&6-- Function is at X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ()));
            } else {
               this.addFunction(loc, function);
            }
         }
      }
   }

   public Connector addConnector(SimpleLocation loc) {
      if (!this.applyDirectionAtEdge(loc)) {
         return null;
      } else {
         Connector connector = new Connector(loc);
         this.addConnector(connector);
         return connector;
      }
   }

   public void addConnector(Connector connector) {
      this.connectors.add(connector);
   }

   public boolean applyDirectionAtEdge(SimpleLocation loc) {
      boolean success = false;
      if ((int)loc.getX() == (int)this.bounds.getMaxX()) {
         loc.setDirection(SimpleLocation.Direction.EAST);
         success = true;
      }

      if ((int)loc.getX() == (int)this.bounds.getMinX()) {
         loc.setDirection(SimpleLocation.Direction.WEST);
         success = true;
      }

      if ((int)loc.getZ() == (int)this.bounds.getMaxZ()) {
         loc.setDirection(SimpleLocation.Direction.SOUTH);
         success = true;
      }

      if ((int)loc.getZ() == (int)this.bounds.getMinZ()) {
         loc.setDirection(SimpleLocation.Direction.NORTH);
         success = true;
      }

      return success;
   }

   public Connector getConnector(SimpleLocation loc) {
      for (Connector connector : this.connectors) {
         if (connector.getLocation().equals(loc)) {
            return connector;
         }
      }

      return null;
   }

   public void removeConnector(SimpleLocation loc) {
      Connector connector = this.getConnector(loc);
      if (connector != null) {
         this.removeConnector(connector);
      }
   }

   public void removeConnector(Connector connector) {
      this.connectors.remove(connector);
   }

   public List<WhitelistEntry> getValidRooms() {
      List<WhitelistEntry> rooms = new ArrayList<>();
      if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
         for (WhitelistEntry entry : this.roomWhitelist) {
            DungeonRoomContainer validRoom = entry.getRoom(this.dungeon);
            if (!validRoom.getConnectors().isEmpty() && validRoom.canGenerate(this)) {
               rooms.add(entry);
            }
         }
      } else {
         for (DungeonRoomContainer validRoom : this.dungeon.getUniqueRooms().values()) {
            if (validRoom != this && validRoom.canGenerate(this)) {
               rooms.add(new WhitelistEntry(validRoom));
            }
         }
      }

      if (this.roomBlacklist != null && !this.roomBlacklist.isEmpty()) {
         for (WhitelistEntry entryx : this.roomBlacklist) {
            rooms.remove(entryx);
         }
      }

      return rooms;
   }

   public List<RotatedRoom> getOrientations() {
      return this.rooms;
   }

   public RotatedRoom getRandomOrientation() {
      return this.rooms.get(MathUtils.getRandomNumberInRange(0, this.rooms.size() - 1));
   }

   public boolean canGenerate(DungeonRoomContainer room) {
      if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
         for (WhitelistEntry entry : this.roomWhitelist) {
            if (entry.getRoomName().equals(room.namespace)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public boolean canRoomGenerateFrom(DungeonRoomContainer room) {
      for (WhitelistEntry entry : this.getValidRooms()) {
         if (entry.getRoom(this.dungeon) == room) {
            return true;
         }
      }

      return false;
   }

   public void loadRoom(File file) throws InvalidConfigurationException {
      this.roomConfig.load(file);
      Location loc1 = Util.readLocation(this.roomConfig.getConfigurationSection("Bounds.Corner1"));
      Location loc2 = Util.readLocation(this.roomConfig.getConfigurationSection("Bounds.Corner2"));
      if (loc1 != null && loc2 != null) {
         this.bounds = BoundingBox.of(loc1, loc2);
         if (this.roomConfig.contains("Config.Occurrences")) {
            this.occurrencesString = this.roomConfig.getString("Config.Occurrences", "-1");
            this.occurrences = new RangedNumber(this.occurrencesString);
         } else {
            int minOccurrences = this.roomConfig.getInt("Config.MinOccurrences", 0);
            int maxOccurrences = this.roomConfig.getInt("Config.MaxOccurrences", -1);
            this.occurrences = new RangedNumber(minOccurrences, maxOccurrences);
            this.occurrencesString = this.occurrences.toIntString();
            this.roomConfig.set("Config.MinOccurrences", null);
            this.roomConfig.set("Config.MaxOccurrences", null);
         }

         if (this.roomConfig.contains("Config.Depth")) {
            this.depthString = this.roomConfig.getString("Config.Depth", "-1");
            this.depth = new RangedNumber(this.depthString);
         } else {
            int minDepth = this.roomConfig.getInt("Config.MinDepth", 0);
            int maxDepth = this.roomConfig.getInt("Config.MaxDepth", -1);
            this.depth = new RangedNumber(minDepth, maxDepth);
            this.depthString = this.depth.toIntString();
            this.roomConfig.set("Config.MinDepth", null);
            this.roomConfig.set("Config.MaxDepth", null);
         }

         this.weight = this.roomConfig.getDouble("Config.Weight", 1.0);
         this.spawn = Util.readLocation(this.roomConfig.getConfigurationSection("Config.SpawnPoint"));
         this.roomBlacklist = this.roomConfig.getListOf(WhitelistEntry.class, "Config.Blacklist");
         this.roomBlacklist.forEach(v -> v.setDungeon(this.dungeon));
         this.roomWhitelist = this.roomConfig.getListOf(WhitelistEntry.class, "Config.Whitelist");
         this.roomWhitelist.forEach(v -> v.setDungeon(this.dungeon));
         ConfigurationSection connectorSection = this.roomConfig.getConfigurationSection("Connectors");
         if (connectorSection != null) {
            for (String path : connectorSection.getKeys(false)) {
               Connector connector = this.roomConfig.get(Connector.class, "Connectors." + path);
               connector.setDungeon(this.dungeon);
               this.connectors.add(connector);
            }
         }

         this.loadFunctions();
      } else {
         throw new InvalidConfigurationException("Config for room " + this.namespace + " has invalid selection area!");
      }
   }

   public void saveRoom() {
      Util.writeLocation("Bounds.Corner1", this.roomConfig, new Location(null, this.bounds.getMinX(), this.bounds.getMinY(), this.bounds.getMinZ()));
      Util.writeLocation("Bounds.Corner2", this.roomConfig, new Location(null, this.bounds.getMaxX(), this.bounds.getMaxY(), this.bounds.getMaxZ()));
      this.roomConfig.set("Config.Occurrences", this.occurrencesString);
      this.roomConfig.set("Config.Depth", this.depthString);
      this.roomConfig.set("Config.Weight", this.weight);
      if (this.spawn != null) {
         Util.writeLocation("Config.SpawnPoint", this.roomConfig, this.spawn);
      } else {
         this.roomConfig.set("Config.SpawnPoint", null);
      }

      this.roomConfig.set("Config.Blacklist", this.roomBlacklist);
      this.roomConfig.set("Config.Whitelist", this.roomWhitelist);
      this.roomConfig.set("Connectors", null);

      for (int i = 0; i < this.connectors.size(); i++) {
         this.roomConfig.set("Connectors." + i, this.connectors.get(i));
      }

      this.roomConfig.save(this.configFile);
      this.rooms.clear();
      this.rooms.add(new RotatedRoom(this, 0));
      this.rooms.add(new RotatedRoom(this, 90));
      this.rooms.add(new RotatedRoom(this, 180));
      this.rooms.add(new RotatedRoom(this, 270));
   }

   public void captureSchematic(World world) {
      if (this.changedSinceLastSave || !this.schemFile.exists()) {
         if (!this.saving) {
            this.changedSinceLastSave = false;
            this.saving = true;
            StructurePiece schematic = new StructurePiece();

            for (double x = this.bounds.getMinX(); x < this.bounds.getMaxX() + 1.0; x++) {
               for (double y = this.bounds.getMinY(); y < this.bounds.getMaxY() + 1.0; y++) {
                  for (double z = this.bounds.getMinZ(); z < this.bounds.getMaxZ() + 1.0; z++) {
                     Block target = world.getBlockAt((int)x, (int)y, (int)z);
                     if (target.getType() != Material.AIR) {
                        schematic.set(target);
                     }
                  }
               }
            }

            Runnable write = () -> {
               new ProcessTimer().run("Saving structure " + this.namespace, () -> {
                  try {
                     SchematicUtils.saveStructurePiece(this.schemFile, schematic);
                  } catch (IOException var3x) {
                     var3x.printStackTrace();
                  }
               });
               this.saving = false;
            };
            if (MythicDungeons.inst().isEnabled()) {
               Bukkit.getScheduler().runTaskAsynchronously(MythicDungeons.inst(), write);
            } else {
               write.run();
            }
         }
      }
   }

   public void delete() {
      this.configFile.delete();
      if (this.schemFile != null) {
         this.schemFile.delete();
      }
   }

   public DungeonProcedural getDungeon() {
      return this.dungeon;
   }

   public String getNamespace() {
      return this.namespace;
   }

   public AsyncConfiguration getRoomConfig() {
      return this.roomConfig;
   }

   public boolean isChangedSinceLastSave() {
      return this.changedSinceLastSave;
   }

   public void setChangedSinceLastSave(boolean changedSinceLastSave) {
      this.changedSinceLastSave = changedSinceLastSave;
   }

   public BoundingBox getBounds() {
      return this.bounds;
   }

   public String getOccurrencesString() {
      return this.occurrencesString;
   }

   public void setOccurrencesString(String occurrencesString) {
      this.occurrencesString = occurrencesString;
   }

   public RangedNumber getOccurrences() {
      return this.occurrences;
   }

   public void setOccurrences(RangedNumber occurrences) {
      this.occurrences = occurrences;
   }

   public String getDepthString() {
      return this.depthString;
   }

   public void setDepthString(String depthString) {
      this.depthString = depthString;
   }

   public RangedNumber getDepth() {
      return this.depth;
   }

   public void setDepth(RangedNumber depth) {
      this.depth = depth;
   }

   public double getWeight() {
      return this.weight;
   }

   public void setWeight(double weight) {
      this.weight = weight;
   }

   public Location getSpawn() {
      return this.spawn;
   }

   public List<WhitelistEntry> getRoomBlacklist() {
      return this.roomBlacklist;
   }

   public List<WhitelistEntry> getRoomWhitelist() {
      return this.roomWhitelist;
   }

   public List<Connector> getConnectors() {
      return this.connectors;
   }

   public Map<Location, DungeonFunction> getFunctionsMapRelative() {
      return this.functionsMapRelative;
   }
}

package nl.hauntedmc.dungeons.util.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Locale;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonClassic;
import nl.hauntedmc.dungeons.dungeons.functions.*;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerDistance;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerDungeonStart;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerInteract;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerKeyItem;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerMobDeath;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerRedstone;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerRemote;
import nl.hauntedmc.dungeons.dungeons.triggers.gates.TriggerGateAnd;
import nl.hauntedmc.dungeons.managers.DungeonTypeManager;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public final class DXLUtils {
   public static boolean convertFromDXL(String worldName) {
      Dungeons.inst().getLogger().info(HelperUtils.colorize("&eBeginning DXL map import..."));
      File dxlWorld = new File(new File(".").getAbsolutePath(), "plugins/DungeonsXL/maps/" + worldName);
      if (!dxlWorld.exists()) {
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: DXL map '" + worldName + "' doesn't exist!"));
         return false;
      } else {
         final File loadedWorld = new File(Bukkit.getWorldContainer(), "DXL_Import");
         final File avnFolder = new File(Dungeons.inst().getDungeonsFolder(), worldName);
         avnFolder.mkdir();

         try {
            DungeonClassic dungeon = (DungeonClassic)DungeonTypeManager.createDungeon(avnFolder);
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&eLoading DXL map..."));
            FileUtils.copyDirectory(dxlWorld, loadedWorld);
            new File(loadedWorld, "uid.dat").delete();
            WorldCreator loader = new WorldCreator("DXL_Import");
            FileConfiguration dxlConfig = new YamlConfiguration();
            File dxlConfigFile = new File(loadedWorld, "config.yml");
            ConfigurationSection messages = null;
            if (dxlConfigFile.exists()) {
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&eConverting config file..."));
               dxlConfig.load(dxlConfigFile);
               dxlConfigFile.delete();
               FileConfiguration avnConfig = dungeon.getConfig();
               String worldDimension = dxlConfig.getString("worldEnvironment", "NORMAL").toUpperCase(Locale.ROOT);
               avnConfig.set("General.Dimension", worldDimension);
               avnConfig.set("General.DisplayName", dxlConfig.getString("title.title", dungeon.getWorldName()));
               avnConfig.set("General.Gamemode", dxlConfig.getString("gameMode", "SURVIVAL"));
               avnConfig.set("General.PlayerLives", dxlConfig.getInt("initialLives", 0));
               avnConfig.set("General.KeepInventoryOnEnter", dxlConfig.getBoolean("keepInventoryOnEnter", true));
               avnConfig.set("General.KickOfflinePlayers", dxlConfig.getInt("timeUntilKickOfflinePlayer", 300) != 0);
               avnConfig.set("General.KickOfflinePlayersDelay", dxlConfig.getInt("timeUntilKickOfflinePlayer", 300));
               boolean lobbyEnabled = !dxlConfig.getBoolean("isLobbyDisabled", false);
               avnConfig.set("General.Lobby.Enabled", lobbyEnabled);
               dungeon.setLobbyEnabled(lobbyEnabled);
               avnConfig.set("Requirements.Permissions", dxlConfig.getStringList("requirements.permission"));
               avnConfig.set("Requirements.MinPartySize", dxlConfig.getInt("requirements.groupSize.minimum", 1));
               avnConfig.set("Requirements.MaxPartySize", dxlConfig.getInt("requirements.groupSize.maximum", 4));
               avnConfig.set("Requirements.DungeonsComplete", dxlConfig.getStringList("requirements.finishedDungeons"));
               avnConfig.set("Rules.AllowBreakBlocks", dxlConfig.getBoolean("breakBlocks", false));
               avnConfig.set("Rules.AllowPlaceBlocks", dxlConfig.getBoolean("placeBlocks", false));
               messages = dxlConfig.getConfigurationSection("message");
               if (messages == null) {
                  messages = dxlConfig.getConfigurationSection("messages");
               }

               dungeon.saveConfig();

               try {
                  loader = loader.environment(Environment.valueOf(worldDimension));
               } catch (IllegalArgumentException var21) {
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: World dimension is invalid: '" + worldDimension + "'"));
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&cMust be one of the following: NORMAL, NETHER, THE_END"));
               }
            }

            World map = loader.createWorld();
            if (map == null) {
               return false;
            }

            Dungeons.inst().getLogger().info(HelperUtils.colorize("&eConverting signs into functions and triggers..."));
            ObjectInputStream os = new ObjectInputStream(new FileInputStream(new File(loadedWorld, "DXLData.data")));
            int length = os.readInt();

            for (int i = 0; i < length; i++) {
               int x = os.readInt();
               int y = os.readInt();
               int z = os.readInt();
               Block block = map.getBlockAt(x, y, z);
               if (block.getState() instanceof Sign sign) {
                  DungeonFunction function = signToFunction(dungeon, sign, messages);
                  if (function != null) {
                     dungeon.addFunction(sign.getLocation(), function);
                  }
               }
            }

            os.close();
            new File(loadedWorld, "DXLData.data").delete();
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&eSaving functions and triggers..."));
            dungeon.saveFunctions();
            map.save();
            Bukkit.unloadWorld(map, false);
            (new BukkitRunnable() {
               public void run() {
                  try {
                     Dungeons.inst().getLogger().info(HelperUtils.colorize("&eSaving world to Dungeons..."));
                     FileUtils.copyDirectory(loadedWorld, avnFolder);
                     FileUtils.deleteDirectory(loadedWorld);
                  } catch (IOException var2) {
                     Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: Could not save world to Dungeons!"));
                     Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var2.getMessage());
                  }
               }
            }).runTaskAsynchronously(Dungeons.inst());
            Dungeons.inst().getDungeonManager().put(dungeon);
         } catch (Exception var22) {
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: Could not import DXL map!"));
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var22.getMessage());

            try {
               Bukkit.unloadWorld(loadedWorld.getName(), false);
               FileUtils.deleteDirectory(loadedWorld);
            } catch (IOException var20) {
               Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var20.getMessage());
            }

            return false;
         }

         Dungeons.inst().getLogger().info(HelperUtils.colorize("&aDXL map import complete!"));
         return true;
      }
   }

   private static DungeonFunction signToFunction(DungeonClassic dungeon, Sign sign, ConfigurationSection messageData) {
      String[] lines = sign.getSide(Side.FRONT).lines().stream().map(HelperUtils::plainText).toArray(String[]::new);
      DungeonFunction function = null;
      DungeonTrigger trigger = null;
      String var7 = lines[0].toLowerCase(Locale.ROOT);
      switch (var7) {
         case "[dungeonchest]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&d-- [ALERT] Found DUNGEONCHEST sign! You will need to configure this manually!"));
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&d---- [ALERT] Sign is at X: "
                        + sign.getLocation().getBlockX()
                        + ", Y: "
                        + sign.getLocation().getBlockY()
                        + ", Z: "
                        + sign.getLocation().getBlockZ()
                  )
               );
            break;
         case "[chest]":
         case "[rewardchest]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&d-- [ALERT] Found REWARDCHEST sign! You will need to configure this manually!"));
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&d---- [ALERT] Sign is at X: "
                        + sign.getLocation().getBlockX()
                        + ", Y: "
                        + sign.getLocation().getBlockY()
                        + ", Z: "
                        + sign.getLocation().getBlockZ()
                  )
               );
            break;
         case "[msg]":
         case "[actionbar]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading MSG sign!"));
            function = new FunctionMessage();
            FunctionMessage messageFunction = (FunctionMessage)function;
            if (messageData != null) {
               String message = messageData.getString(lines[1], "0");
               messageFunction.setMessage(message);
            } else {
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&c---- [WARN] MSG sign has invalid message ID! You will need to set this manually."));
               Dungeons.inst()
                  .getLogger()
                  .info(
                     HelperUtils.colorize(
                        "&c---- [WARN] Sign is at X: "
                           + sign.getLocation().getBlockX()
                           + ", Y: "
                           + sign.getLocation().getBlockY()
                           + ", Z: "
                           + sign.getLocation().getBlockZ()
                     )
                  );
            }

            if (lines[0].equalsIgnoreCase("[msg]")) {
               messageFunction.setMessageType(0);
            } else {
               messageFunction.setMessageType(1);
            }
            break;
         case "[title]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading TITLE sign!"));
            function = new FunctionTitle();
            FunctionTitle titleFunction = (FunctionTitle)function;
            if (messageData != null) {
               String[] split = getLineArgs(lines[1]);
               titleFunction.setTitle(messageData.getString(split[0], "0"));
               if (split.length >= 2) {
                  titleFunction.setSubtitle(messageData.getString(split[1], "0"));
               }
            } else {
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&c---- [WARN] TITLE sign has invalid message ID! You will need to set this manually."));
               Dungeons.inst()
                  .getLogger()
                  .info(
                     HelperUtils.colorize(
                        "&c---- [WARN] Sign is at X: "
                           + sign.getLocation().getBlockX()
                           + ", Y: "
                           + sign.getLocation().getBlockY()
                           + ", Z: "
                           + sign.getLocation().getBlockZ()
                     )
                  );
            }
            break;
         case "[start]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading START sign!"));
            World world = Bukkit.getWorld("DXL_Import");
            if (world != null) {
               world.setSpawnLocation(sign.getLocation());
            }
            break;
         case "[lobby]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading LOBBY sign!"));
            dungeon.setLobbySpawn(sign.getLocation());
            break;
         case "[ready]": {
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading READY sign!"));
            function = new FunctionStartDungeon();
            function.setTargetType(FunctionTargetType.PLAYER);
            trigger = new TriggerInteract();
            TriggerInteract interactTrigger = (TriggerInteract)trigger;
            interactTrigger.setAllowRetrigger(false);
            break;
         }
         case "[end]": {
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading END sign!"));
            function = new FunctionFinishDungeon();
            FunctionFinishDungeon finishFunction = (FunctionFinishDungeon)function;
            finishFunction.setTargetType(FunctionTargetType.PARTY);
            finishFunction.setLeave(true);
            trigger = new TriggerInteract();
            TriggerInteract interactTrigger = (TriggerInteract)trigger;
            interactTrigger.setAllowRetrigger(false);
            break;
         }
         case "[leave]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading LEAVE sign!"));
            function = new FunctionLeaveDungeon();
            function.setTargetType(FunctionTargetType.PARTY);
            trigger = new TriggerInteract();
            trigger.setAllowRetrigger(false);
            break;
         case "[lives]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading LIVES sign!"));
            function = new FunctionLives();
            String var39 = lines[2].toLowerCase();
            switch (var39) {
               case "player":
                  function.setTargetType(FunctionTargetType.PLAYER);
               case "game":
               case "group":
               default:
                  function.setTargetType(FunctionTargetType.PARTY);
                  break;
            }

            ((FunctionLives)function).setMode(0);

            try {
               ((FunctionLives)function).setAmount(Integer.parseInt(lines[1]));
            } catch (NumberFormatException var35) {
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&c---- [WARN] LIVES sign has an invalid number format! (" + lines[1] + ")"));
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&c---- [WARN] Examples of valid numbers include: 1, 7, 42, etc."));
            }
            break;
         case "[trigger]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading TRIGGER sign!"));
            function = new FunctionRemoteTrigger();
            FunctionRemoteTrigger triggerFunction = (FunctionRemoteTrigger)function;
            triggerFunction.setTriggerName(lines[1]);
            break;
         case "[interact]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading INTERACT sign!"));
            function = new FunctionRemoteTrigger();
            function.setTargetType(FunctionTargetType.PARTY);
            FunctionRemoteTrigger interactFunction = (FunctionRemoteTrigger)function;
            interactFunction.setTriggerName("I" + lines[1]);
            trigger = new TriggerInteract();
            trigger.setAllowRetrigger(false);
            break;
         case "[mob]":
         case "[externalmob]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading MOB sign!"));
            function = new FunctionSpawnMob();
            FunctionSpawnMob mobFunction = (FunctionSpawnMob)function;
            String mob = lines[1];
            String[] args = getLineArgs(lines[2]);
            mobFunction.setMob(mob);
            mobFunction.setDelay(Integer.parseInt(args[0]) * 20);
            mobFunction.setInterval(Integer.parseInt(args[0]) * 20);
            mobFunction.setMaxCount(Integer.parseInt(args[1]));
            break;
         case "[door]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading DOOR sign!"));
            function = new FunctionDoorControl();
            break;
         case "[redstone]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading REDSTONE sign!"));
            function = new FunctionRedstoneBlock();
            break;
         case "[soundmsg]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading SOUND sign!"));
            function = new FunctionPlaySound();
            FunctionPlaySound soundFunction = (FunctionPlaySound)function;
            soundFunction.setSound(lines[1]);
            String[] soundArgs = getLineArgs(lines[2]);

            try {
               soundFunction.setSoundCategory(soundArgs[0]);

               try {
                  soundFunction.setVolume(Double.parseDouble(soundArgs[1]));
               } catch (NumberFormatException var33) {
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&c--- [WARN] SOUNDMSG volume has an invalid number format! (" + soundArgs[1] + ")"));
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&c--- [WARN] Examples of valid numbers include: 0.1, 1, 10.5, etc."));
               }

               try {
                  soundFunction.setVolume(Double.parseDouble(soundArgs[2]));
               } catch (NumberFormatException var32) {
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&c--- [WARN] SOUNDMSG pitch has an invalid number format! (" + soundArgs[2] + ")"));
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&c--- [WARN] Examples of valid numbers include: 0.5, 1, 1.75, etc."));
               }
            } catch (IndexOutOfBoundsException ignored) {
            }
            break;
         case "[teleport]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading TELEPORT sign!"));
            function = new FunctionTeleport();
            FunctionTeleport teleportFunction = (FunctionTeleport)function;
            Location loc;
            if (lines[1].isEmpty()) {
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&e--- Teleport location is empty! Using the sign's location..."));
               loc = sign.getLocation().clone();
               loc.setWorld(null);
            } else {
               String[] coords = lines[1].split(",");
               double x = Double.parseDouble(coords[0]);
               double y = 64.0;
               if (coords.length >= 2) {
                  y = Double.parseDouble(coords[1]);
               }

               double z = 0.0;
               if (coords.length >= 3) {
                  z = Double.parseDouble(coords[2]);
               }

               loc = new Location(null, x, y, z);
            }

            if (!lines[2].isEmpty()) {
               String var42 = lines[2].toUpperCase();
               switch (var42) {
                  case "NNE":
                     loc.setYaw(-157.5F);
                     break;
                  case "NE":
                     loc.setYaw(-135.0F);
                     break;
                  case "ENE":
                     loc.setYaw(-112.5F);
                     break;
                  case "E":
                     loc.setYaw(-90.0F);
                     break;
                  case "ESE":
                     loc.setYaw(-67.5F);
                     break;
                  case "SE":
                     loc.setYaw(-45.0F);
                     break;
                  case "SSE":
                     loc.setYaw(-22.5F);
                     break;
                  case "S":
                     loc.setYaw(0.0F);
                     break;
                  case "SSW":
                     loc.setYaw(22.5F);
                     break;
                  case "SW":
                     loc.setYaw(45.0F);
                     break;
                  case "WSW":
                     loc.setYaw(67.5F);
                     break;
                  case "W":
                     loc.setYaw(90.0F);
                     break;
                  case "WNW":
                     loc.setYaw(112.5F);
                     break;
                  case "NW":
                     loc.setYaw(135.0F);
                     break;
                  case "NNW":
                     loc.setYaw(157.5F);
                  case "N":
                  default:
                     loc.setYaw(-180.0F);
                     break;
               }
            }

            teleportFunction.setTeleportTarget(loc);
            break;
         case "[command]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&d-- [ALERT] Found COMMAND sign! You will need to configure this manually!"));
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&d---- [ALERT] Sign is at X: "
                        + sign.getLocation().getBlockX()
                        + ", Y: "
                        + sign.getLocation().getBlockY()
                        + ", Z: "
                        + sign.getLocation().getBlockZ()
                  )
               );
            break;
         case "[place]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading PLACE sign!"));
            function = new FunctionAllowBlock();
            trigger = new TriggerDungeonStart();
            break;
         case "[block]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading BLOCK sign!"));
            function = new FunctionBlockEditor();

            try {
               Material.valueOf(lines[2]);
               ((FunctionBlockEditor)function).setBlockType(lines[2].toUpperCase());
            } catch (IllegalArgumentException var31) {
               Dungeons.inst()
                  .getLogger()
                  .info(HelperUtils.colorize("&c---- [WARN] BLOCK sign has an invalid block material " + lines[1] + "! You may need to configure it manually."));
               Dungeons.inst()
                  .getLogger()
                  .info(
                     HelperUtils.colorize(
                        "&c---- [WARN] Sign is at X: "
                           + sign.getLocation().getBlockX()
                           + ", Y: "
                           + sign.getLocation().getBlockY()
                           + ", Z: "
                           + sign.getLocation().getBlockZ()
                     )
                  );
            }
            break;
         case "[drop]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading DROP sign!"));
            function = new FunctionGiveItem();

            try {
               Material mat = Material.valueOf(lines[1]);
               ItemStack item = new ItemStack(mat);
               String[] dropData = lines[2].split(",");

               try {
                  int quantity = Integer.parseInt(dropData[0]);
                  item.setAmount(quantity);
               } catch (NumberFormatException var29) {
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&c--- [WARN] DROP sign has an invalid number format! (" + dropData[0] + ")"));
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("&c--- [WARN] Examples of valid numbers include: 1, 7, 42, etc."));
               }

               ((FunctionGiveItem)function).setItem(item);
            } catch (IllegalArgumentException var30) {
               Dungeons.inst()
                  .getLogger()
                  .info(HelperUtils.colorize("&c---- [WARN] BLOCK sign has an invalid block material " + lines[1] + "! You may need to configure it manually."));
               Dungeons.inst()
                  .getLogger()
                  .info(
                     HelperUtils.colorize(
                        "&c---- [WARN] Sign is at X: "
                           + sign.getLocation().getBlockX()
                           + ", Y: "
                           + sign.getLocation().getBlockY()
                           + ", Z: "
                           + sign.getLocation().getBlockZ()
                     )
                  );
            }
            break;
         case "[checkpoint]":
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&e-- Reading CHECKPOINT sign!"));
            function = new FunctionCheckpoint();
            break;
         default:
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&d-- Unrecognized sign type! " + lines[0].toLowerCase(Locale.ROOT)));
      }

      if (function != null) {
         function.setLocation(sign.getLocation());
         if (trigger == null) {
            trigger = signToTrigger(function, lines[3]);
         }

         if (trigger != null) {
            function.setTrigger(trigger);
         } else {
            Dungeons.inst()
               .getLogger()
               .info(HelperUtils.colorize("&c---- [WARN] " + lines[0] + " sign has unrecognized trigger! (" + lines[3] + ") You may need to configure it manually."));
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&c---- [WARN] Sign is at X: "
                        + sign.getLocation().getBlockX()
                        + ", Y: "
                        + sign.getLocation().getBlockY()
                        + ", Z: "
                        + sign.getLocation().getBlockZ()
                  )
               );
            function.setTrigger(new TriggerDungeonStart());
         }

         function.init();
      }

      return function;
   }

   private static DungeonTrigger signToTrigger(DungeonFunction function, String triggerInfo) {
      if (triggerInfo.contains(",")) {
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&d-- [ALERT] Adapting sign with multiple triggers into AND trigger..."));
          return adaptMultiTrigger(function, triggerInfo);
      } else {
         return adaptTrigger(function, triggerInfo);
      }
   }

   private static TriggerGateAnd adaptMultiTrigger(DungeonFunction function, String triggerLine) {
      TriggerGateAnd andTrigger = new TriggerGateAnd();
      String[] triggerInfos = triggerLine.split(",");

      for (String triggerInfo : triggerInfos) {
         DungeonTrigger trigger = adaptTrigger(function, triggerInfo);
         if (trigger != null) {
            andTrigger.addTrigger(trigger);
         }
      }

      return andTrigger;
   }

   private static DungeonTrigger adaptTrigger(DungeonFunction function, String triggerInfo) {
      DungeonTrigger trigger = null;
      if (triggerInfo.startsWith("D") || triggerInfo.startsWith("P")) {
         trigger = new TriggerDistance();
         TriggerDistance distanceTrigger = (TriggerDistance)trigger;

         try {
            String distString = triggerInfo.substring(1).replaceAll("\\s+", "");
            int distance = Integer.parseInt(distString);
            distanceTrigger.setRadius(distance);
            distanceTrigger.setCount(1);
            if (triggerInfo.startsWith("P")) {
               distanceTrigger.setForEachPlayer(true);
            }
         } catch (NumberFormatException var6) {
            return null;
         }
      }

      if (triggerInfo.startsWith("R")) {
         trigger = new TriggerRedstone();
         TriggerRedstone redstoneTrigger = (TriggerRedstone)trigger;
         redstoneTrigger.setAllowRetrigger(false);
      }

      if (triggerInfo.startsWith("T")) {
         trigger = new TriggerRemote();
         TriggerRemote remoteTrigger = (TriggerRemote)trigger;
         String trigName = triggerInfo.substring(1).replaceAll("\\s+", "");
         remoteTrigger.setTriggerName(trigName);
         remoteTrigger.setAllowRetrigger(false);
      }

      if (triggerInfo.startsWith("I")) {
         trigger = new TriggerRemote();
         TriggerRemote remoteTrigger = (TriggerRemote)trigger;
         String trigName = triggerInfo.substring(1).replaceAll("\\s+", "");
         remoteTrigger.setTriggerName("I" + trigName);
         remoteTrigger.setAllowRetrigger(false);
      }

      if (triggerInfo.startsWith("M")) {
         trigger = new TriggerMobDeath();
         TriggerMobDeath deathTrigger = (TriggerMobDeath)trigger;
         String mob = triggerInfo.substring(1).replaceAll("\\s+", "");
         deathTrigger.setMob(mob);
         deathTrigger.setAllowRetrigger(false);
      }

      if (triggerInfo.startsWith("U")) {
         trigger = new TriggerKeyItem();
         TriggerKeyItem itemTrigger = (TriggerKeyItem)trigger;
         String materialName = triggerInfo.substring(1);
         Material material = Material.getMaterial(materialName);
         if (material == null) {
            material = Material.STICK;
            Dungeons.inst()
               .getLogger()
               .info(HelperUtils.colorize("&c---- [WARN] Use Item trigger has an invalid item: '" + materialName + "'! Defaulting to STICK..."));
            Dungeons.inst()
               .getLogger()
               .info(
                  HelperUtils.colorize(
                     "&c---- [WARN] Sign is at X: "
                        + function.getLocation().getBlockX()
                        + ", Y: "
                        + function.getLocation().getBlockY()
                        + ", Z: "
                        + function.getLocation().getBlockZ()
                  )
               );
         }

         itemTrigger.setItem(new ItemStack(material));
         itemTrigger.setConsumeItem(false);
         itemTrigger.setUseAnywhere(true);
      }

      return trigger;
   }

   private static String[] getLineArgs(String line) {
      return line.split(",");
   }
}

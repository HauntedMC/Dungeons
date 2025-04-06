package net.playavalon.mythicdungeons.utility.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Locale;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonClassic;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionAllowBlock;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionBlockEditor;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionCheckpoint;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionDoorControl;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionFinishDungeon;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionGiveItem;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionLeaveDungeon;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionLives;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionMessage;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionPlaySound;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionRedstoneBlock;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionRemoteTrigger;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionSpawnMythicMob;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionStartDungeon;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionTeleport;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionTitle;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerDistance;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerDungeonStart;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerInteract;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerKeyItem;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerMobDeath;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerMythicMobDeath;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerRedstone;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerRemote;
import net.playavalon.mythicdungeons.dungeons.triggers.gates.TriggerGateAnd;
import net.playavalon.mythicdungeons.managers.DungeonTypeManager;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public final class DXLUtils {
   public static boolean convertFromDXL(String worldName) {
      MythicDungeons.inst().getLogger().info(Util.colorize("&eBeginning DXL map import..."));
      File dxlWorld = new File(new File(".").getAbsolutePath(), "plugins/DungeonsXL/maps/" + worldName);
      if (!dxlWorld.exists()) {
         MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: DXL map '" + worldName + "' doesn't exist!"));
         return false;
      } else {
         final File loadedWorld = new File(Bukkit.getWorldContainer(), "DXL_Import");
         final File avnFolder = new File(MythicDungeons.inst().getDungeonsFolder(), worldName);
         avnFolder.mkdir();

         try {
            DungeonClassic dungeon = (DungeonClassic)DungeonTypeManager.createDungeon(avnFolder);
            MythicDungeons.inst().getLogger().info(Util.colorize("&eLoading DXL map..."));
            FileUtils.copyDirectory(dxlWorld, loadedWorld);
            new File(loadedWorld, "uid.dat").delete();
            WorldCreator loader = new WorldCreator("DXL_Import");
            FileConfiguration dxlConfig = new YamlConfiguration();
            File dxlConfigFile = new File(loadedWorld, "config.yml");
            ConfigurationSection messages = null;
            if (dxlConfigFile.exists()) {
               MythicDungeons.inst().getLogger().info(Util.colorize("&eConverting config file..."));
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
                  MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: World dimension is invalid: '" + worldDimension + "'"));
                  MythicDungeons.inst().getLogger().info(Util.colorize("&cMust be one of the following: NORMAL, NETHER, THE_END"));
               }
            }

            World map = loader.createWorld();
            if (map == null) {
               return false;
            }

            MythicDungeons.inst().getLogger().info(Util.colorize("&eConverting signs into functions and triggers..."));
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&eSaving functions and triggers..."));
            dungeon.saveFunctions();
            map.save();
            Bukkit.unloadWorld(map, false);
            (new BukkitRunnable() {
               public void run() {
                  try {
                     MythicDungeons.inst().getLogger().info(Util.colorize("&eSaving world to MythicDungeons..."));
                     FileUtils.copyDirectory(loadedWorld, avnFolder);
                     FileUtils.deleteDirectory(loadedWorld);
                  } catch (IOException var2) {
                     MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: Could not save world to MythicDungeons!"));
                     var2.printStackTrace();
                  }
               }
            }).runTaskAsynchronously(MythicDungeons.inst());
            MythicDungeons.inst().getDungeonManager().put(dungeon);
         } catch (Exception var22) {
            MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: Could not import DXL map!"));
            var22.printStackTrace();

            try {
               Bukkit.unloadWorld(loadedWorld.getName(), false);
               FileUtils.deleteDirectory(loadedWorld);
            } catch (IOException var20) {
               var20.printStackTrace();
            }

            return false;
         }

         MythicDungeons.inst().getLogger().info(Util.colorize("&aDXL map import complete!"));
         return true;
      }
   }

   private static DungeonFunction signToFunction(DungeonClassic dungeon, Sign sign, ConfigurationSection messageData) {
      String[] lines = sign.getLines();
      DungeonFunction function = null;
      DungeonTrigger trigger = null;
      String var7 = lines[0].toLowerCase(Locale.ROOT);
      switch (var7) {
         case "[dungeonchest]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&d-- [ALERT] Found DUNGEONCHEST sign! You will need to configure this manually!"));
            MythicDungeons.inst()
               .getLogger()
               .info(
                  Util.colorize(
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&d-- [ALERT] Found REWARDCHEST sign! You will need to configure this manually!"));
            MythicDungeons.inst()
               .getLogger()
               .info(
                  Util.colorize(
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading MSG sign!"));
            function = new FunctionMessage();
            FunctionMessage messageFunction = (FunctionMessage)function;
            if (messageData != null) {
               String message = messageData.getString(lines[1], "0");
               messageFunction.setMessage(message);
            } else {
               MythicDungeons.inst().getLogger().info(Util.colorize("&c---- [WARN] MSG sign has invalid message ID! You will need to set this manually."));
               MythicDungeons.inst()
                  .getLogger()
                  .info(
                     Util.colorize(
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading TITLE sign!"));
            function = new FunctionTitle();
            FunctionTitle titleFunction = (FunctionTitle)function;
            if (messageData != null) {
               String[] split = getLineArgs(lines[1]);
               titleFunction.setTitle(messageData.getString(split[0], "0"));
               if (split.length >= 2) {
                  titleFunction.setSubtitle(messageData.getString(split[1], "0"));
               }
            } else {
               MythicDungeons.inst().getLogger().info(Util.colorize("&c---- [WARN] TITLE sign has invalid message ID! You will need to set this manually."));
               MythicDungeons.inst()
                  .getLogger()
                  .info(
                     Util.colorize(
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading START sign!"));
            World world = Bukkit.getWorld("DXL_Import");
            if (world != null) {
               world.setSpawnLocation(sign.getLocation());
            }
            break;
         case "[lobby]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading LOBBY sign!"));
            dungeon.setLobbySpawn(sign.getLocation());
            break;
         case "[ready]": {
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading READY sign!"));
            function = new FunctionStartDungeon();
            function.setTargetType(FunctionTargetType.PLAYER);
            trigger = new TriggerInteract();
            TriggerInteract interactTrigger = (TriggerInteract)trigger;
            interactTrigger.setAllowRetrigger(false);
            break;
         }
         case "[end]": {
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading END sign!"));
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading LEAVE sign!"));
            function = new FunctionLeaveDungeon();
            function.setTargetType(FunctionTargetType.PARTY);
            trigger = new TriggerInteract();
            trigger.setAllowRetrigger(false);
            break;
         case "[lives]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading LIVES sign!"));
            function = new FunctionLives();
            String var39 = lines[2].toLowerCase();
            switch (var39) {
               case "game":
               case "group":
               default:
                  function.setTargetType(FunctionTargetType.PARTY);
                  break;
               case "player":
                  function.setTargetType(FunctionTargetType.PLAYER);
            }

            ((FunctionLives)function).setMode(0);

            try {
               ((FunctionLives)function).setAmount(Integer.parseInt(lines[1]));
            } catch (NumberFormatException var35) {
               MythicDungeons.inst().getLogger().info(Util.colorize("&c---- [WARN] LIVES sign has an invalid number format! (" + lines[1] + ")"));
               MythicDungeons.inst().getLogger().info(Util.colorize("&c---- [WARN] Examples of valid numbers include: 1, 7, 42, etc."));
            }
            break;
         case "[trigger]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading TRIGGER sign!"));
            function = new FunctionRemoteTrigger();
            FunctionRemoteTrigger triggerFunction = (FunctionRemoteTrigger)function;
            triggerFunction.setTriggerName(lines[1]);
            break;
         case "[interact]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading INTERACT sign!"));
            function = new FunctionRemoteTrigger();
            function.setTargetType(FunctionTargetType.PARTY);
            FunctionRemoteTrigger interactFunction = (FunctionRemoteTrigger)function;
            interactFunction.setTriggerName("I" + lines[1]);
            trigger = new TriggerInteract();
            trigger.setAllowRetrigger(false);
            break;
         case "[mob]":
         case "[externalmob]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading MOB sign!"));
            function = new FunctionSpawnMythicMob();
            FunctionSpawnMythicMob mythicMobFunction = (FunctionSpawnMythicMob)function;
            String mythicMob = lines[1];
            String[] mythicArgs = getLineArgs(lines[2]);
            mythicMobFunction.setMob(mythicMob);
            mythicMobFunction.setDelay(Integer.parseInt(mythicArgs[0]) * 20);
            mythicMobFunction.setInterval(Integer.parseInt(mythicArgs[0]) * 20);
            mythicMobFunction.setMaxCount(Integer.parseInt(mythicArgs[1]));
            break;
         case "[door]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading DOOR sign!"));
            function = new FunctionDoorControl();
            break;
         case "[redstone]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading REDSTONE sign!"));
            function = new FunctionRedstoneBlock();
            break;
         case "[soundmsg]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading SOUND sign!"));
            function = new FunctionPlaySound();
            FunctionPlaySound soundFunction = (FunctionPlaySound)function;
            soundFunction.setSound(lines[1]);
            String[] soundArgs = getLineArgs(lines[2]);

            try {
               soundFunction.setSoundCategory(soundArgs[0]);

               try {
                  soundFunction.setVolume(Double.parseDouble(soundArgs[1]));
               } catch (NumberFormatException var33) {
                  MythicDungeons.inst().getLogger().info(Util.colorize("&c--- [WARN] SOUNDMSG volume has an invalid number format! (" + soundArgs[1] + ")"));
                  MythicDungeons.inst().getLogger().info(Util.colorize("&c--- [WARN] Examples of valid numbers include: 0.1, 1, 10.5, etc."));
               }

               try {
                  soundFunction.setVolume(Double.parseDouble(soundArgs[2]));
               } catch (NumberFormatException var32) {
                  MythicDungeons.inst().getLogger().info(Util.colorize("&c--- [WARN] SOUNDMSG pitch has an invalid number format! (" + soundArgs[2] + ")"));
                  MythicDungeons.inst().getLogger().info(Util.colorize("&c--- [WARN] Examples of valid numbers include: 0.5, 1, 1.75, etc."));
               }
            } catch (IndexOutOfBoundsException var34) {
            }
            break;
         case "[teleport]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading TELEPORT sign!"));
            function = new FunctionTeleport();
            FunctionTeleport teleportFunction = (FunctionTeleport)function;
            Location loc;
            if (lines[1].equals("")) {
               MythicDungeons.inst().getLogger().info(Util.colorize("&e--- Teleport location is empty! Using the sign's location..."));
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

            if (!lines[2].equals("")) {
               String var42 = lines[2].toUpperCase();
               switch (var42) {
                  case "N":
                  default:
                     loc.setYaw(-180.0F);
                     break;
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
               }
            }

            teleportFunction.setTeleportTarget(loc);
            break;
         case "[command]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&d-- [ALERT] Found COMMAND sign! You will need to configure this manually!"));
            MythicDungeons.inst()
               .getLogger()
               .info(
                  Util.colorize(
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading PLACE sign!"));
            function = new FunctionAllowBlock();
            trigger = new TriggerDungeonStart();
            break;
         case "[block]":
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading BLOCK sign!"));
            function = new FunctionBlockEditor();

            try {
               Material.valueOf(lines[2]);
               ((FunctionBlockEditor)function).setBlockType(lines[2].toUpperCase());
            } catch (IllegalArgumentException var31) {
               MythicDungeons.inst()
                  .getLogger()
                  .info(Util.colorize("&c---- [WARN] BLOCK sign has an invalid block material " + lines[1] + "! You may need to configure it manually."));
               MythicDungeons.inst()
                  .getLogger()
                  .info(
                     Util.colorize(
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading DROP sign!"));
            function = new FunctionGiveItem();

            try {
               Material mat = Material.valueOf(lines[1]);
               ItemStack item = new ItemStack(mat);
               String[] dropData = lines[2].split(",");

               try {
                  int quantity = Integer.parseInt(dropData[0]);
                  item.setAmount(quantity);
               } catch (NumberFormatException var29) {
                  MythicDungeons.inst().getLogger().info(Util.colorize("&c--- [WARN] DROP sign has an invalid number format! (" + dropData[0] + ")"));
                  MythicDungeons.inst().getLogger().info(Util.colorize("&c--- [WARN] Examples of valid numbers include: 1, 7, 42, etc."));
               }

               ((FunctionGiveItem)function).setItem(item);
            } catch (IllegalArgumentException var30) {
               MythicDungeons.inst()
                  .getLogger()
                  .info(Util.colorize("&c---- [WARN] BLOCK sign has an invalid block material " + lines[1] + "! You may need to configure it manually."));
               MythicDungeons.inst()
                  .getLogger()
                  .info(
                     Util.colorize(
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
            MythicDungeons.inst().getLogger().info(Util.colorize("&e-- Reading CHECKPOINT sign!"));
            function = new FunctionCheckpoint();
            break;
         default:
            MythicDungeons.inst().getLogger().info(Util.colorize("&d-- Unrecognized sign type! " + lines[0].toLowerCase(Locale.ROOT)));
      }

      if (function != null) {
         function.setLocation(sign.getLocation());
         if (trigger == null) {
            trigger = signToTrigger(function, lines[3]);
         }

         if (trigger != null) {
            function.setTrigger(trigger);
         } else {
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&c---- [WARN] " + lines[0] + " sign has unrecognized trigger! (" + lines[3] + ") You may need to configure it manually."));
            MythicDungeons.inst()
               .getLogger()
               .info(
                  Util.colorize(
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
         MythicDungeons.inst().getLogger().info(Util.colorize("&d-- [ALERT] Adapting sign with multiple triggers into AND trigger..."));
         DungeonTrigger trigger = adaptMultiTrigger(function, triggerInfo);
         return trigger;
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
         if (MythicDungeons.inst().getMythicApi() != null) {
            trigger = new TriggerMythicMobDeath();
            TriggerMythicMobDeath deathTrigger = (TriggerMythicMobDeath)trigger;
            String mob = triggerInfo.substring(1).replaceAll("\\s+", "");
            deathTrigger.setMob(mob);
            deathTrigger.setAllowRetrigger(false);
         } else {
            trigger = new TriggerMobDeath();
            TriggerMobDeath deathTrigger = (TriggerMobDeath)trigger;
            String mob = triggerInfo.substring(1).replaceAll("\\s+", "");
            deathTrigger.setMob(mob);
            deathTrigger.setAllowRetrigger(false);
         }
      }

      if (triggerInfo.startsWith("U")) {
         trigger = new TriggerKeyItem();
         TriggerKeyItem itemTrigger = (TriggerKeyItem)trigger;
         String materialName = triggerInfo.substring(1);
         Material material = Material.getMaterial(materialName);
         if (material == null) {
            material = Material.STICK;
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&c---- [WARN] Use Item trigger has an invalid item: '" + materialName + "'! Defaulting to STICK..."));
            MythicDungeons.inst()
               .getLogger()
               .info(
                  Util.colorize(
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

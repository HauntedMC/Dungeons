package net.playavalon.mythicdungeons.managers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.exceptions.DungeonInitException;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.gui.PlayGUIHandler;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class DungeonManager {
   private HashMap<String, AbstractDungeon> dungeons = new HashMap<>();

   public DungeonManager() {
      File defaultConfig = new File(MythicDungeons.inst().getDungeonsFolder(), "default-config.yml");
      if (!defaultConfig.exists()) {
         MythicDungeons.inst().saveResource("maps/default-config.yml", false);
      }

      File defaultGenerator = new File(MythicDungeons.inst().getDungeonsFolder(), "default-generator.yml");
      if (!defaultGenerator.exists()) {
         MythicDungeons.inst().saveResource("maps/default-generator.yml", false);
      }

      try {
         MythicDungeons.inst().getDefaultDungeonConfig().load(defaultConfig);
      } catch (InvalidConfigurationException | IOException var4) {
         var4.printStackTrace();
      }

      Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
         File[] files = MythicDungeons.inst().getDungeonsFolder().listFiles();
         if (files != null) {
            for (File file : files) {
               if (file.isDirectory()) {
                  this.loadDungeon(file);
               }
            }
         }
      }, 1L);
   }

   public AbstractDungeon loadDungeon(File folder) {
      return this.loadDungeon(folder, "", "");
   }

   public AbstractDungeon loadDungeon(File folder, String dungeonType, String generator) {
      MythicDungeons.inst().getLogger().info(Util.colorize("&bLoading dungeon: " + folder.getName()));

      try {
         AbstractDungeon dungeon = null;
         File configFile = new File(folder, "config.yml");
         YamlConfiguration config = null;
         if (configFile.exists()) {
            config = new YamlConfiguration();
            config.load(configFile);
            dungeonType = config.getString("General.DungeonType", "classic");
         }

         if (dungeonType.isEmpty()) {
            dungeonType = "classic";
         }

         try {
            dungeon = DungeonTypeManager.createDungeon(dungeonType, folder, config);
         } catch (InvocationTargetException var9) {
            Throwable ex = var9.getTargetException();
            if (ex instanceof DungeonInitException) {
               ((DungeonInitException)ex).printError(folder);
            } else {
               MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
               MythicDungeons.inst().getLogger().info(Util.colorize("&c└─ An unexpected error was encountered during loading! Please report the error below!"));
               var9.printStackTrace();
            }
         } catch (InstantiationException | IllegalAccessException | NoSuchMethodException var10) {
            MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&c├─ There was an error creating a dungeon of type '" + dungeonType + "'! See the error below!"));
            MythicDungeons.inst().getLogger().info(Util.colorize("&c└─ Error: " + var10.getMessage()));
            var10.printStackTrace();
         }

         if (dungeon == null) {
            return null;
         } else {
            dungeon.setGenerator(generator);
            this.put(dungeon);
            if (dungeon.isUseDifficultyLevels()) {
               PlayGUIHandler.initDifficultySelector(dungeon);
            }

            return dungeon;
         }
      } catch (InvalidConfigurationException | IOException var11) {
         MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
         MythicDungeons.inst().getLogger().info(Util.colorize("&c├─ The dungeon's config file was invalid! See error below..."));
         var11.printStackTrace();
         return null;
      }
   }

   public void put(AbstractDungeon dungeon) {
      this.dungeons.put(dungeon.getWorldName(), dungeon);
   }

   public void remove(AbstractDungeon dungeon) {
      this.dungeons.remove(dungeon.getWorldName());
   }

   public AbstractDungeon get(String dungeonName) {
      return this.dungeons.get(dungeonName);
   }

   public Collection<AbstractDungeon> getAll() {
      return this.dungeons.values();
   }

   public boolean createInstance(String dungeonName, Player player) {
      return this.createInstance(dungeonName, player, "DEFAULT");
   }

   public boolean createInstance(String dungeonName, Player player, String difficulty) {
      AbstractDungeon dungeon = this.get(dungeonName);
      if (dungeon == null) {
         return false;
      } else if (dungeon.isSaving()) {
         LangUtils.sendMessage(player, "instance.is-saving");
         return false;
      } else {
         dungeon.instantiate(player, difficulty);
         return true;
      }
   }

   public boolean editDungeon(String dungeonName, Player player) {
      AbstractDungeon dungeon = this.get(dungeonName);
      if (dungeon == null) {
         return false;
      } else if (dungeon.isSaving()) {
         LangUtils.sendMessage(player, "instance.is-saving");
         return false;
      } else {
         dungeon.edit(player);
         return true;
      }
   }
}

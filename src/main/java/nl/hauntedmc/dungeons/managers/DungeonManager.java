package nl.hauntedmc.dungeons.managers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.exceptions.DungeonInitException;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.gui.inv.PlayGUIHandler;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class DungeonManager {
   private final HashMap<String, AbstractDungeon> dungeons = new HashMap<>();

   public DungeonManager() {
      File defaultConfig = new File(Dungeons.inst().getDungeonsFolder(), "default-config.yml");
      if (!defaultConfig.exists()) {
         Dungeons.inst().saveResource("maps/default-config.yml", false);
      }

      File defaultGenerator = new File(Dungeons.inst().getDungeonsFolder(), "default-generator.yml");
      if (!defaultGenerator.exists()) {
         Dungeons.inst().saveResource("maps/default-generator.yml", false);
      }

      try {
         Dungeons.inst().getDefaultDungeonConfig().load(defaultConfig);
      } catch (InvalidConfigurationException | IOException var4) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var4.getMessage());
      }

      Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
         File[] files = Dungeons.inst().getDungeonsFolder().listFiles();
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
      Dungeons.inst().getLogger().info(HelperUtils.colorize("&bLoading dungeon: " + folder.getName()));

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
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&c└─ An unexpected error was encountered during loading! Please report the error below!"));
               Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var9.getMessage());
            }
         } catch (InstantiationException | IllegalAccessException | NoSuchMethodException var10) {
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
            Dungeons.inst()
               .getLogger()
               .info(HelperUtils.colorize("&c├─ There was an error creating a dungeon of type '" + dungeonType + "'! See the error below!"));
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&c└─ Error: " + var10.getMessage()));
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var10.getMessage());
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
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: The dungeon '" + folder.getName() + "' was not loaded:"));
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&c├─ The dungeon's config file was invalid! See error below..."));
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var11.getMessage());
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

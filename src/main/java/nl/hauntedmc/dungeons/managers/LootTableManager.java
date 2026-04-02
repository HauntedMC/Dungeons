package nl.hauntedmc.dungeons.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.dungeons.rewards.LootTable;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LootTableManager {
   private final Map<String, LootTable> tables = new HashMap<>();
   private FileConfiguration tablesConfig;

   public LootTableManager() {
      try {
         File tablesFile = new File(Dungeons.inst().getDataFolder(), "loottables.yml");
         if (!tablesFile.exists()) {
            tablesFile.createNewFile();
         }

         this.tablesConfig = new YamlConfiguration();
         this.tablesConfig.load(tablesFile);
         ConfigurationSection tablesSection = this.tablesConfig.getConfigurationSection("Tables");
         if (tablesSection == null) {
            return;
         }

         for (String path : tablesSection.getKeys(false)) {
            this.tables.put(path, (LootTable)tablesSection.get(path));
         }
      } catch (IOException | InvalidConfigurationException var5) {
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&cCould not initialize loot tables!!"));
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var5.getMessage());
      }
   }

   public void put(LootTable table) {
      this.tables.put(table.getNamespace(), table);
      this.saveTablesConfig();
   }

   public boolean contains(String namespace) {
      return this.tables.containsKey(namespace);
   }

   public LootTable get(String namespace) {
      return this.tables.get(namespace);
   }

   public Collection<LootTable> getTables() {
      return this.tables.values();
   }

   public void remove(String namespace) {
      this.tables.remove(namespace);
      this.saveTablesConfig();
   }

   public void saveTablesConfig() {
      this.tablesConfig.set("Tables", this.tables);

      try {
         File tablesFile = new File(Dungeons.inst().getDataFolder(), "loottables.yml");
         this.tablesConfig.save(tablesFile);
      } catch (IOException var2) {
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&cCould not save loot tables!!"));
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var2.getMessage());
      }
   }
}

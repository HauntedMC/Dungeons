package net.playavalon.mythicdungeons.api.parents.dungeons;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.MythicItem;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.exceptions.DungeonInitException;
import net.playavalon.mythicdungeons.api.parents.DungeonDifficulty;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.parents.instances.InstanceEditable;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonClassic;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionReward;
import net.playavalon.mythicdungeons.dungeons.rewards.CooldownPeriod;
import net.playavalon.mythicdungeons.dungeons.rewards.LootCooldown;
import net.playavalon.mythicdungeons.dungeons.rewards.PlayerLootData;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;
import org.zeroturnaround.zip.ZipUtil;

public abstract class AbstractDungeon {
   protected String worldName;
   protected File folder;
   protected FileConfiguration config;
   protected FileConfiguration lootConfig;
   protected String displayName;
   protected boolean lobbyEnabled;
   protected Location lobbySpawn;
   protected Location startSpawn;
   protected Location exitLoc;
   protected boolean alwaysUseExit;
   protected boolean useDifficultyLevels;
   protected boolean showDifficultyMenu;
   protected Map<String, DungeonDifficulty> difficultyLevels;
   protected List<ItemStack> customBannedItems;
   protected List<String> bannedItems;
   protected List<ItemStack> validKeys;
   protected List<Material> placeWhitelist;
   protected List<Material> breakWhitelist;
   protected List<Material> placeBlacklist;
   protected List<Material> breakBlacklist;
   protected boolean breakPlacedBlocks;
   protected List<EntityType> damageProtectedEntities;
   protected List<EntityType> interactProtectedEntities;
   protected boolean accessCooldownEnabled;
   protected Map<UUID, Date> accessCooldownsByPlayer;
   protected boolean cooldownOnFinish;
   protected boolean cooldownOnLeave;
   protected boolean cooldownOnLoseLives;
   protected boolean cooldownOnStart;
   protected Map<UUID, FileConfiguration> playerData;
   protected boolean cooldownsPerReward;
   protected List<PlayerLootData> lootCooldowns;
   protected Map<UUID, PlayerLootData> lootCooldownsbyPlayer;
   protected List<AbstractInstance> instances;
   protected InstanceEditable editSession;
   protected boolean saving;
   protected boolean markedForDelete;

   public AbstractDungeon(@NotNull File folder, @Nullable YamlConfiguration loadedConfig) throws DungeonInitException {
      this.worldName = folder.getName();
      this.folder = folder;
      if (loadedConfig != null) {
         this.config = loadedConfig;
      } else {
         this.config = new YamlConfiguration();

         try {
            File configFile = new File(folder, "config.yml");
            if (!configFile.exists()) {
               MythicDungeons.inst().getLogger().info("Creating fresh config file for " + folder.getName());
               FileUtils.copyFile(new File(MythicDungeons.inst().getDungeonsFolder(), "default-config.yml"), configFile);
            }

            this.config.load(configFile);
         } catch (IOException var20) {
            throw new DungeonInitException(
               "Access of config.yml file failed!", false, "There may be another process accessing the file, or we may not have permission."
            );
         } catch (InvalidConfigurationException | IllegalArgumentException | YAMLException var21) {
            throw new DungeonInitException("Dungeon config has invalid YAML! See error below...", true);
         }
      }

      this.displayName = Util.fullColor(this.config.getString("General.DisplayName", "&c" + this.worldName));
      this.lobbyEnabled = this.config.getBoolean("General.Lobby.Enabled", true);
      Object genericLoc = this.config.get("General.Lobby.Location");
      if (genericLoc instanceof Location) {
         this.lobbySpawn = (Location)genericLoc;
      } else {
         this.lobbySpawn = Util.readLocation(this.config.getConfigurationSection("General.Lobby.Location"), new Location(null, 0.0, 64.0, 0.0));
      }

      genericLoc = this.config.get("General.StartLocation");
      if (genericLoc instanceof Location) {
         this.startSpawn = (Location)genericLoc;
      } else {
         this.startSpawn = Util.readLocation(this.config.getConfigurationSection("General.StartLocation"));
      }

      ConfigurationSection exitLocSect = this.config.getConfigurationSection("General.ExitLocation");
      if (exitLocSect != null) {
         this.exitLoc = Util.readLocation(exitLocSect);
      }

      if (this.exitLoc != null && this.exitLoc.getWorld() == null && !this.exitLoc.isWorldLoaded()) {
         this.exitLoc = null;
         MythicDungeons.inst()
            .getLogger()
            .info(Util.colorize("&cERROR :: Exit location for dungeon '" + this.worldName + "' has an invalid world! (World not loaded.)"));
      }

      this.alwaysUseExit = this.config.getBoolean("General.AlwaysUseExit", false);
      this.useDifficultyLevels = this.config.getBoolean("Difficulty.EnableDifficultyLevels", false);
      this.showDifficultyMenu = this.config.getBoolean("Difficulty.EnableDifficultyMenu", false);
      this.difficultyLevels = new LinkedHashMap<>();
      if (this.useDifficultyLevels) {
         ConfigurationSection difficulties = this.config.getConfigurationSection("Difficulty.Levels");
         if (difficulties != null) {
            for (String path : difficulties.getKeys(false)) {
               this.difficultyLevels.put(path, new DungeonDifficulty(difficulties.getConfigurationSection(path)));
            }
         }
      }

      this.bannedItems = this.config.getStringList("Rules.BannedItems");
      this.customBannedItems = (List<ItemStack>)this.config.get("Rules.CustomBannedItems");
      if (this.customBannedItems == null) {
         this.customBannedItems = new ArrayList<>();
      }

      this.validKeys = (List<ItemStack>)this.config.get("AccessKeys.KeyItems");
      if (this.validKeys == null) {
         this.validKeys = new ArrayList<>();
      }

      this.placeWhitelist = new ArrayList<>();
      this.placeBlacklist = new ArrayList<>();
      this.breakWhitelist = new ArrayList<>();
      this.breakBlacklist = new ArrayList<>();
      this.breakPlacedBlocks = this.config.getBoolean("Rules.AllowBreakPlacedBlocks", false);

      for (String name : this.config.getStringList("Rules.BlockPlaceWhitelist")) {
         try {
            Material mat = Material.valueOf(name);
            this.placeWhitelist.add(mat);
         } catch (IllegalArgumentException var19) {
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&cERROR :: Dungeon " + this.worldName + " has an invalid block in its place whitelist: " + name));
         }
      }

      for (String name : this.config.getStringList("Rules.BlockBreakWhitelist")) {
         try {
            Material mat = Material.valueOf(name);
            this.breakWhitelist.add(mat);
         } catch (IllegalArgumentException var18) {
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&cERROR :: Dungeon " + this.worldName + " has an invalid block in its break whitelist: " + name));
         }
      }

      for (String name : this.config.getStringList("Rules.BlockPlaceBlacklist")) {
         try {
            Material mat = Material.valueOf(name);
            this.placeBlacklist.add(mat);
         } catch (IllegalArgumentException var17) {
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&cERROR :: Dungeon " + this.worldName + " has an invalid block in its place blacklist: " + name));
         }
      }

      for (String name : this.config.getStringList("Rules.BlockBreakBlacklist")) {
         try {
            Material mat = Material.valueOf(name);
            this.breakBlacklist.add(mat);
         } catch (IllegalArgumentException var16) {
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&cERROR :: Dungeon " + this.worldName + " has an invalid block in its break blacklist: " + name));
         }
      }

      this.damageProtectedEntities = new ArrayList<>();
      this.interactProtectedEntities = new ArrayList<>();

      for (String name : this.config.getStringList("Rules.DamageProtectedEntities")) {
         try {
            EntityType type = EntityType.valueOf(name);
            this.damageProtectedEntities.add(type);
         } catch (IllegalArgumentException var15) {
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&cERROR :: Dungeon " + this.worldName + " has an invalid entity type in its damage protect entities list: " + name));
         }
      }

      for (String name : this.config.getStringList("Rules.InteractProtectedEntities")) {
         try {
            EntityType type = EntityType.valueOf(name);
            this.interactProtectedEntities.add(type);
         } catch (IllegalArgumentException var14) {
            MythicDungeons.inst()
               .getLogger()
               .info(Util.colorize("&cERROR :: Dungeon " + this.worldName + " has an invalid entity type in its interact protect entities list: " + name));
         }
      }

      this.accessCooldownEnabled = this.config.getBoolean("General.AccessCooldown.Enabled", false);
      this.accessCooldownsByPlayer = new HashMap<>();
      this.cooldownOnFinish = this.config.getBoolean("General.AccessCooldown.CooldownOnFinish", true);
      this.cooldownOnLeave = this.config.getBoolean("General.AccessCooldown.CooldownOnLeave", false);
      this.cooldownOnLoseLives = this.config.getBoolean("General.AccessCooldown.CooldownOnLoseLives", false);
      this.cooldownOnStart = this.config.getBoolean("General.AccessCooldown.CooldownOnStart", false);
      this.cooldownsPerReward = this.config.getBoolean("General.LootCooldown.PerReward", true);
      this.lootConfig = new YamlConfiguration();
      this.lootCooldowns = new ArrayList<>();
      this.lootCooldownsbyPlayer = new HashMap<>();
      this.playerData = new HashMap<>();
      File playersFolder = new File(folder, "players");
      if (!playersFolder.isDirectory()) {
         playersFolder.mkdir();
      }

      File[] playerFiles = playersFolder.listFiles();
      if (playerFiles != null) {
         for (File playerFile : playerFiles) {
            if (FilenameUtils.getExtension(playerFile.getName()).equals("yml")) {
               FileConfiguration data = new YamlConfiguration();

               try {
                  data.load(playerFile);
               } catch (InvalidConfigurationException | IOException var13) {
                  var13.printStackTrace();
               }

               UUID playerUUID = UUID.fromString(playerFile.getName().replace(".yml", ""));
               this.playerData.put(playerUUID, data);
               this.loadCooldowns(playerUUID);
            }
         }
      }

      this.instances = new ArrayList<>();
      this.cleanDeadInstances();
   }

   public boolean hasAvailableInstances() {
      int maxInstances = this.config.getInt("General.MaxInstances", 0);
      return maxInstances == 0 ? true : this.instances.size() < maxInstances;
   }

   protected void cleanDeadInstances() {
      File[] worlds = Bukkit.getWorldContainer().listFiles();
      if (worlds != null) {
         for (File world : worlds) {
            if (world.isDirectory() && world.getName().contains(this.folder.getName()) && Bukkit.getWorld(world.getName()) == null) {
               try {
                  FileUtils.deleteDirectory(world);
               } catch (IOException var7) {
                  var7.printStackTrace();
               }
            }
         }
      }
   }

   public void backupDungeon() {
      Bukkit.getScheduler().runTaskAsynchronously(MythicDungeons.inst(), () -> {
         Date date = new Date(System.currentTimeMillis());
         DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");
         String strDate = dateFormat.format(date);
         File zipFile = new File(MythicDungeons.inst().getBackupFolder(), this.folder.getName() + "_" + strDate + ".zip");
         ZipUtil.pack(this.folder, zipFile);
         File[] allBackups = MythicDungeons.inst().getBackupFolder().listFiles();
         if (allBackups != null) {
            Map<Long, File> backupFiles = new HashMap<>();

            for (File file : allBackups) {
               if (file.getName().contains(this.folder.getName())) {
                  backupFiles.put(file.lastModified(), file);
               }
            }

            this.deleteExcessBackups(backupFiles);
         }
      });
   }

   protected void deleteExcessBackups(Map<Long, File> backupFiles) {
      if (backupFiles.size() > this.config.getInt("General.MaxBackups", 7)) {
         long oldestTime = System.currentTimeMillis();

         for (Entry<Long, File> pair : backupFiles.entrySet()) {
            long fileTime = pair.getKey();
            if (fileTime < oldestTime) {
               oldestTime = fileTime;
            }
         }

         backupFiles.get(oldestTime).delete();
         backupFiles.remove(oldestTime);
         this.deleteExcessBackups(backupFiles);
      }
   }

   public boolean instantiate(Player player) {
      return this.instantiate(player, "DEFAULT");
   }

   public boolean instantiate(Player player, String difficultyName) {
      return this.prepInstance(player, difficultyName);
   }

   public abstract boolean prepInstance(Player var1, String var2);

   public void edit(Player player) {
      if (this.editSession != null) {
         Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> this.editSession.addPlayer(MythicDungeons.inst().getMythicPlayer(player)));
      } else {
         CountDownLatch latch = new CountDownLatch(1);
         this.editSession = this.createEditSession(latch);
         this.instances.add(this.editSession);
         MythicDungeons.inst().getActiveInstances().add(this.editSession);

         try {
            boolean loaded = latch.await(10L, TimeUnit.SECONDS);
            if (!loaded) {
               this.removeInstance(this.editSession);
               this.editSession = null;
               LangUtils.sendMessage(player, "instance.timed-out");
               return;
            }

            MythicPlayer dPlayer = MythicDungeons.inst().getMythicPlayer(player);
            Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> this.editSession.addPlayer(dPlayer));
         } catch (InterruptedException var5) {
            this.removeInstance(this.editSession);
            this.editSession = null;
            var5.printStackTrace();
         }
      }
   }

   public abstract InstancePlayable createPlaySession(CountDownLatch var1);

   public abstract InstanceEditable createEditSession(CountDownLatch var1);

   public void removeInstance(AbstractInstance instance) {
      this.instances.remove(instance);
      MythicDungeons.inst().getActiveInstances().remove(instance);
   }

   public void timeout(AbstractInstance inst, MythicPlayer mPlayer) {
      Player player = mPlayer.getPlayer();
      this.cancelInstance(inst, mPlayer);
      LangUtils.sendMessage(player, "instance.timed-out");
   }

   public void cancelInstance(AbstractInstance inst, MythicPlayer mPlayer) {
      this.removeInstance(inst);
      IDungeonParty party = mPlayer.getDungeonParty();
      if (party != null) {
         party.setAwaitingDungeon(false);
      } else {
         mPlayer.setAwaitingDungeon(false);
      }
   }

   public abstract void addFunction(Location var1, DungeonFunction var2);

   public abstract void removeFunction(Location var1);

   public abstract void saveFunctions();

   public abstract Map<Location, DungeonFunction> getFunctions();

   public void addAccessCooldown(Player player) {
      this.addAccessCooldown(player, this.getNextUnlockTime());
   }

   public void addAccessCooldown(Player player, Date resetTime) {
      this.accessCooldownsByPlayer.put(player.getUniqueId(), resetTime);
   }

   public boolean hasAccessCooldown(Player player) {
      Date cooldown = this.accessCooldownsByPlayer.get(player.getUniqueId());
      if (cooldown == null) {
         return false;
      } else if (new Date(System.currentTimeMillis()).after(cooldown)) {
         this.accessCooldownsByPlayer.remove(player.getUniqueId());
         return false;
      } else {
         return true;
      }
   }

   public Date getAccessCooldown(Player player) {
      return !this.hasAccessCooldown(player) ? null : this.accessCooldownsByPlayer.get(player.getUniqueId());
   }

   public void addLootCooldown(Player player, FunctionReward reward) {
      this.addLootCooldown(player, reward, this.getNextLootTime());
   }

   public void addLootCooldown(Player player, FunctionReward reward, Date resetTime) {
      PlayerLootData playerCooldown = this.lootCooldownsbyPlayer.get(player.getUniqueId());
      if (playerCooldown == null) {
         playerCooldown = new PlayerLootData(player);
         this.lootCooldowns.add(playerCooldown);
         this.lootCooldownsbyPlayer.put(player.getUniqueId(), playerCooldown);
      }

      playerCooldown.addLootCooldown(reward, resetTime);
   }

   public boolean hasLootCooldown(Player player, FunctionReward reward) {
      PlayerLootData playerCooldown = this.lootCooldownsbyPlayer.get(player.getUniqueId());
      if (playerCooldown == null) {
         playerCooldown = new PlayerLootData(player);
         this.lootCooldowns.add(playerCooldown);
         this.lootCooldownsbyPlayer.put(player.getUniqueId(), playerCooldown);
      }

      return playerCooldown.hasLootOnCooldown(reward);
   }

   public Date getLootUnlockTime(Player player, FunctionReward function) {
      PlayerLootData lootData = this.getPlayerLootData(player);
      LootCooldown cd = lootData.getLootCooldown(function);
      return cd == null ? null : cd.getResetTime();
   }

   public PlayerLootData getPlayerLootData(Player player) {
      return this.lootCooldownsbyPlayer.get(player.getUniqueId());
   }

   public void loadCooldowns(UUID playerID) {
      FileConfiguration data = this.playerData.get(playerID);
      if (data.get("RewardsInfo") != null) {
         PlayerLootData playerData = (PlayerLootData)data.get("RewardsInfo");
         if (playerData != null) {
            this.lootCooldowns.add(playerData);
            this.lootCooldownsbyPlayer.put(playerData.getPlayerID(), playerData);
         }
      }

      if (data.get("AccessCooldown", null) != null) {
         this.accessCooldownsByPlayer.put(playerID, new Date(data.getLong("AccessCooldown")));
      }
   }

   public void saveCooldowns(Player player) {
      UUID playerID = player.getUniqueId();
      PlayerLootData playerData = this.lootCooldownsbyPlayer.get(player.getUniqueId());
      if (playerData == null) {
         playerData = new PlayerLootData(player);
         this.lootCooldowns.add(playerData);
         this.lootCooldownsbyPlayer.put(player.getUniqueId(), playerData);
      }

      FileConfiguration data = this.playerData.getOrDefault(playerID, new YamlConfiguration());
      this.playerData.putIfAbsent(playerID, data);
      data.set("RewardsInfo", playerData);
      Date cooldown = this.accessCooldownsByPlayer.get(playerID);
      if (cooldown != null) {
         data.set("AccessCooldown", cooldown.getTime());
      }
   }

   public void savePlayerData(Player player) {
      File playersFolder = new File(this.folder, "players");
      UUID playerID = player.getUniqueId();
      FileConfiguration data = this.playerData.getOrDefault(playerID, new YamlConfiguration());
      this.playerData.putIfAbsent(playerID, data);

      try {
         File targetFile = new File(playersFolder, playerID + ".yml");
         data.save(targetFile);
      } catch (IOException var6) {
         var6.printStackTrace();
      }
   }

   @Nullable
   public FileConfiguration getPlayerData(UUID playerID) {
      return this.playerData.get(playerID);
   }

   public boolean hasPlayerCompletedDungeon(Player player) {
      UUID playerID = player.getUniqueId();
      FileConfiguration data = this.playerData.get(playerID);
      return data == null ? false : data.getBoolean("Finished", false);
   }

   public void setPlayerCompletedDungeon(Player player) {
      this.setPlayerCompletedDungeon(player, true);
   }

   public void setPlayerCompletedDungeon(Player player, boolean complete) {
      UUID playerID = player.getUniqueId();
      FileConfiguration data = this.playerData.getOrDefault(playerID, new YamlConfiguration());
      this.playerData.putIfAbsent(playerID, data);
      data.set("Finished", complete);
   }

   public boolean partyMeetsRequirements(Player player) {
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (MythicDungeons.inst().isPartiesEnabled() && aPlayer.getDungeonParty() != null) {
         return this.partyMeetsRequirements(aPlayer.getDungeonParty());
      } else {
         int minPartySize = this.config.getInt("Requirements.MinPartySize", 1);
         if (minPartySize > 1) {
            player.sendMessage(LangUtils.getMessage("instance.requirements.party-too-small", String.valueOf(minPartySize)));
            aPlayer.setAwaitingDungeon(false);
            return false;
         } else if (!this.meetsRequirements(player)) {
            aPlayer.setAwaitingDungeon(false);
            return false;
         } else {
            if (!this.validKeys.isEmpty()) {
               int amount = this.getFirstKeyAmount(player);
               ItemStack key = this.getFirstKey(player);
               if (key == null || amount == -1) {
                  LangUtils.sendMessage(player, "instance.requirements.no-access-key");
                  aPlayer.setAwaitingDungeon(false);
                  return false;
               }

               if (this.config.getBoolean("AccessKeys.Consume", true)) {
                  key.setAmount(key.getAmount() - amount);
               }
            }

            if (MythicDungeons.inst().getEconomy() != null) {
               double cost = this.config.getDouble("Requirements.Cost", 0.0);
               if (cost > 0.0) {
                  MythicDungeons.inst().getEconomy().withdrawPlayer(player, cost);
                  LangUtils.sendMessage(player, "instance.requirements.money-deducted", MythicDungeons.inst().getEconomy().format(cost));
               }
            }

            return true;
         }
      }
   }

   public boolean partyMeetsRequirements(@NotNull IDungeonParty party) {
      int minPartySize = this.config.getInt("Requirements.MinPartySize", 1);
      int maxPartySize = this.config.getInt("Requirements.MaxPartySize", 4);
      if (party.getPlayers().size() > maxPartySize) {
         party.partyMessage(LangUtils.getMessage("instance.requirements.party-too-big", String.valueOf(maxPartySize)));
         party.setAwaitingDungeon(false);
         return false;
      } else if (party.getPlayers().size() < minPartySize) {
         party.partyMessage(LangUtils.getMessage("instance.requirements.party-too-small", String.valueOf(minPartySize)));
         party.setAwaitingDungeon(false);
         return false;
      } else {
         boolean deny = false;

         for (Player partyPlayer : party.getPlayers()) {
            if (!this.meetsRequirements(partyPlayer)) {
               deny = true;
               party.partyMessage(LangUtils.getMessage("instance.requirements.requirements-not-met", partyPlayer.getName()));
            }
         }

         if (deny) {
            party.setAwaitingDungeon(false);
            return false;
         } else {
            for (Player partyPlayerx : party.getPlayers()) {
               if (!this.validKeys.isEmpty()) {
                  if (Util.hasPermissionSilent(partyPlayerx, "dungeons.bypasskeys")
                     || Util.hasPermissionSilent(partyPlayerx, "dungeons.bypasskeys." + this.worldName)) {
                     continue;
                  }

                  int amount = this.getFirstKeyAmount(partyPlayerx);
                  ItemStack key = this.getFirstKey(partyPlayerx);
                  if (key == null || amount == -1) {
                     party.partyMessage(LangUtils.getMessage("instance.requirements.requirements-not-met", partyPlayerx.getName()));
                     LangUtils.sendMessage(partyPlayerx, "instance.requirements.no-access-key");
                     party.setAwaitingDungeon(false);
                     return false;
                  }

                  if (this.config.getBoolean("AccessKeys.Consume", true)) {
                     key.setAmount(key.getAmount() - amount);
                  }
               }

               if (MythicDungeons.inst().getEconomy() != null) {
                  double cost = this.config.getDouble("Requirements.Cost", 0.0);
                  if (cost > 0.0) {
                     MythicDungeons.inst().getEconomy().withdrawPlayer(partyPlayerx, cost);
                     LangUtils.sendMessage(partyPlayerx, "instance.requirements.money-deducted", MythicDungeons.inst().getEconomy().format(cost));
                  }
               }
            }

            return true;
         }
      }
   }

   public boolean meetsRequirements(Player player) {
      for (String permission : this.config.getStringList("Requirements.Permissions")) {
         if (!Util.hasPermissionSilent(player, permission)) {
            LangUtils.sendMessage(player, "instance.requirements.no-permission", permission);
            return false;
         }
      }

      if (!Util.hasPermissionSilent(player, "dungeons.bypasscooldown")
         && !Util.hasPermissionSilent(player, "dungeons.bypasscooldown." + this.worldName)
         && this.hasAccessCooldown(player)) {
         LangUtils.sendMessage(player, "instance.requirements.on-cooldown");
         Date unlockTime = this.accessCooldownsByPlayer.get(player.getUniqueId());
         if (unlockTime != null) {
            SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d, hh:mm aaa z");
            LangUtils.sendMessage(player, "instance.requirements.cooldown-time", format.format(unlockTime));
         }

         return false;
      } else {
         for (String requiredDungeon : this.config.getStringList("Requirements.DungeonsComplete")) {
            AbstractDungeon dungeon = MythicDungeons.inst().getDungeonManager().get(requiredDungeon);
            if (dungeon != null && !dungeon.hasPlayerCompletedDungeon(player)) {
               LangUtils.sendMessage(player, "instance.requirements.complete-required", dungeon.getDisplayName());
               return false;
            }
         }

         if (!Util.hasPermissionSilent(player, "dungeons.bypasscost")
            && !Util.hasPermissionSilent(player, "dungeons.bypasscost." + this.worldName)
            && MythicDungeons.inst().getEconomy() != null) {
            double cost = this.config.getDouble("Requirements.Cost");
            if (cost > 0.0) {
               double balance = MythicDungeons.inst().getEconomy().getBalance(player);
               if (balance < cost) {
                  LangUtils.sendMessage(player, "instance.requirements.not-enough-money", MythicDungeons.inst().getEconomy().format(cost));
                  return false;
               }
            }
         }

         boolean foundBannedItems = false;

         for (ItemStack item : player.getInventory()) {
            if (item != null && ItemUtils.isItemBanned(this, item)) {
               String displayName = ItemUtils.getItemDisplayName(item);
               LangUtils.sendMessage(player, "instance.requirements.banned-item", displayName);
               foundBannedItems = true;
            }
         }

         if (foundBannedItems) {
            return false;
         } else {
            if (!this.validKeys.isEmpty()
               && !Util.hasPermissionSilent(player, "dungeons.bypasskeys")
               && !Util.hasPermissionSilent(player, "dungeons.bypasskeys." + this.worldName)) {
               ItemStack key = this.getFirstKey(player);
               if (key == null || key.getType() == Material.AIR) {
                  LangUtils.sendMessage(player, "instance.requirements.no-access-key");
                  return false;
               }
            }

            return true;
         }
      }
   }

   public void banItem(ItemStack item) {
      ItemStack vanillaItem = new ItemStack(item.getType());
      if (item.isSimilar(vanillaItem)) {
         this.bannedItems.add(item.getType().toString());
      } else {
         this.customBannedItems.add(item);
      }

      this.config.set("Rules.BannedItems", this.bannedItems);
      this.config.set("Rules.CustomBannedItems", this.customBannedItems);

      try {
         this.config.save(new File(this.folder, "config.yml"));
      } catch (IOException var4) {
         var4.printStackTrace();
      }
   }

   public boolean unbanItem(ItemStack item) {
      ItemStack vanillaItem = new ItemStack(item.getType());
      if (!this.bannedItems.contains(item.getType().toString()) && !this.customBannedItems.contains(item)) {
         return false;
      } else {
         if (item.isSimilar(vanillaItem)) {
            this.bannedItems.remove(item.getType().toString());
         } else {
            this.customBannedItems.remove(item);
         }

         this.config.set("Rules.BannedItems", this.bannedItems);
         this.config.set("Rules.CustomBannedItems", this.customBannedItems);

         try {
            this.config.save(new File(this.folder, "config.yml"));
         } catch (IOException var4) {
            var4.printStackTrace();
         }

         return true;
      }
   }

   public void addAccessKey(ItemStack item) {
      this.validKeys.add(item.clone());
      this.config.set("AccessKeys.KeyItems", this.validKeys);
      if (this.config.get("AccessKeys.Consume") == null) {
         this.config.set("AccessKeys.Consume", true);
      }

      try {
         this.config.save(new File(this.folder, "config.yml"));
      } catch (IOException var3) {
         var3.printStackTrace();
      }
   }

   public boolean removeAccessKey(ItemStack item) {
      boolean keyFound = false;

      for (ItemStack key : new ArrayList<>(this.validKeys)) {
         if (MythicDungeons.inst().getMythicApi() != null) {
            String mythicItem = ItemUtils.getMythicItemType(item);
            String mythicKey = ItemUtils.getMythicItemType(key);
            if (mythicItem != null && mythicItem.equals(mythicKey)) {
               this.validKeys.remove(key);
               keyFound = true;
            }
         }

         if (item.isSimilar(key)) {
            this.validKeys.remove(key);
            keyFound = true;
         }
      }

      if (keyFound) {
         this.config.set("AccessKeys.KeyItems", this.validKeys);
         if (this.config.get("AccessKeys.Consume") == null) {
            this.config.set("AccessKeys.Consume", true);
         }

         try {
            this.config.save(new File(this.folder, "config.yml"));
         } catch (IOException var8) {
            var8.printStackTrace();
         }
      }

      return keyFound;
   }

   public ItemStack isValidKey(ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         for (ItemStack key : this.validKeys) {
            if (MythicDungeons.inst().getMythicApi() != null) {
               String mythicItem = ItemUtils.getMythicItemType(item);
               String mythicKey = ItemUtils.getMythicItemType(key);
               if (mythicItem != null && mythicItem.equals(mythicKey)) {
                  Optional<MythicItem> mItemOpt = MythicBukkit.inst().getItemManager().getItem(mythicKey);
                  if (mItemOpt.isPresent()) {
                     return key;
                  }

                  return null;
               }
            }

            if (item.isSimilar(key)) {
               return key;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public int getFirstKeyAmount(Player player) {
      PlayerInventory inv = player.getInventory();

      for (ItemStack item : inv.getContents()) {
         ItemStack key;
         if ((key = this.isValidKey(item)) != null) {
            if (key.getAmount() > item.getAmount()) {
               return -1;
            }

            return key.getAmount();
         }
      }

      return -1;
   }

   public ItemStack getFirstKey(Player player) {
      PlayerInventory inv = player.getInventory();

      for (ItemStack item : inv.getContents()) {
         if (this.isValidKey(item) != null) {
            return item;
         }
      }

      return null;
   }

   public void setGenerator(@NotNull String generator) {
      if (!generator.isEmpty()) {
         this.config.set("General.ChunkGenerator", generator);
         this.saveConfig();
      }
   }

   public void setLobbySpawn(Location lobbySpawn) {
      this.lobbySpawn = lobbySpawn;
      this.config.set("General.Lobby.Enabled", this.lobbyEnabled);
      Location lobbyLoc = lobbySpawn.clone();
      lobbyLoc.setWorld(null);
      Util.writeLocation("General.Lobby.Location", this.config, lobbyLoc);
      this.saveConfig();
   }

   public void setStartSpawn(Location startSpawn) {
      this.startSpawn = startSpawn;
      Location startLoc = startSpawn.clone();
      startLoc.setWorld(null);
      Util.writeLocation("General.StartLocation", this.config, startLoc);
      this.saveConfig();
   }

   public void setExit(Location exitLoc) {
      this.exitLoc = exitLoc;
      Util.writeLocation("General.ExitLocation", this.config, this.exitLoc);
      this.saveConfig();
   }

   public Date getNextUnlockTime() {
      if (!this.config.getBoolean("General.AccessCooldown.Enabled", false)) {
         return new Date();
      } else {
         CooldownPeriod period = CooldownPeriod.valueOf(this.config.getString("General.AccessCooldown.CooldownType", "DAILY"));
         int cooldownTime = this.config.getInt("General.AccessCooldown.CooldownTime", 1);
         if (period == CooldownPeriod.TIMER) {
            return period.fromNow(cooldownTime);
         } else {
            Calendar cal = Calendar.getInstance();
            if (cooldownTime > cal.get(11)) {
               cal.set(5, cal.get(5) - 1);
            }

            cal.set(11, cooldownTime);
            cal.set(12, 0);
            cal.set(13, 0);
            cal.set(14, 0);
            if (period == CooldownPeriod.WEEKLY) {
               int dayOfWeek = this.config.getInt("General.AccessCooldown.ResetDay", 1);
               if (dayOfWeek > cal.get(7)) {
                  cal.set(4, cal.get(4) - 1);
               }

               cal.set(7, dayOfWeek);
            }

            if (period == CooldownPeriod.MONTHLY) {
               int dayOfMonth = this.config.getInt("General.AccessCooldown.ResetDay", 1);
               if (dayOfMonth > cal.get(7)) {
                  cal.set(2, cal.get(2) - 1);
               }

               cal.set(5, dayOfMonth);
            }

            return period.fromDate(cal.getTime());
         }
      }
   }

   public Date getNextLootTime() {
      if (!this.config.getBoolean("General.LootCooldown.Enabled", false)) {
         return new Date();
      } else {
         CooldownPeriod period = CooldownPeriod.valueOf(this.config.getString("General.LootCooldown.CooldownType", "DAILY"));
         int cooldownTime = this.config.getInt("General.LootCooldown.CooldownTime", 1);
         if (period == CooldownPeriod.TIMER) {
            return period.fromNow(cooldownTime);
         } else {
            Calendar cal = Calendar.getInstance();
            if (cooldownTime > cal.get(11)) {
               cal.set(5, cal.get(5) - 1);
            }

            cal.set(11, cooldownTime);
            cal.set(12, 0);
            cal.set(13, 0);
            cal.set(14, 0);
            if (period == CooldownPeriod.WEEKLY) {
               int dayOfWeek = this.config.getInt("General.LootCooldown.ResetDay", 1);
               if (dayOfWeek > cal.get(7)) {
                  cal.set(4, cal.get(4) - 1);
               }

               cal.set(7, dayOfWeek);
            }

            if (period == CooldownPeriod.MONTHLY) {
               int dayOfMonth = this.config.getInt("General.LootCooldown.ResetDay", 1);
               if (dayOfMonth > cal.get(7)) {
                  cal.set(2, cal.get(2) - 1);
               }

               cal.set(5, dayOfMonth);
            }

            return period.fromDate(cal.getTime());
         }
      }
   }

   public void setSaveConfig(String path, Object value) {
      this.config.set(path, value);
      this.saveConfig();
   }

   public void saveConfig() {
      try {
         this.config.save(new File(this.folder, "config.yml"));
      } catch (IOException var2) {
         var2.printStackTrace();
      }
   }

   @Nullable
   public DungeonClassic asClassic() {
      return this.as(DungeonClassic.class);
   }

   @Nullable
   public DungeonProcedural asProcedural() {
      return this.as(DungeonProcedural.class);
   }

   @Nullable
   public <T extends AbstractDungeon> T as(Class<T> clazz) {
      return (T)(clazz.isInstance(this) ? this : null);
   }

   public String getWorldName() {
      return this.worldName;
   }

   public File getFolder() {
      return this.folder;
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public boolean isLobbyEnabled() {
      return this.lobbyEnabled;
   }

   public void setLobbyEnabled(boolean lobbyEnabled) {
      this.lobbyEnabled = lobbyEnabled;
   }

   public Location getLobbySpawn() {
      return this.lobbySpawn;
   }

   public Location getStartSpawn() {
      return this.startSpawn;
   }

   public Location getExitLoc() {
      return this.exitLoc;
   }

   public boolean isAlwaysUseExit() {
      return this.alwaysUseExit;
   }

   public boolean isUseDifficultyLevels() {
      return this.useDifficultyLevels;
   }

   public boolean isShowDifficultyMenu() {
      return this.showDifficultyMenu;
   }

   public Map<String, DungeonDifficulty> getDifficultyLevels() {
      return this.difficultyLevels;
   }

   public List<ItemStack> getCustomBannedItems() {
      return this.customBannedItems;
   }

   public List<String> getBannedItems() {
      return this.bannedItems;
   }

   public List<ItemStack> getValidKeys() {
      return this.validKeys;
   }

   public List<Material> getPlaceWhitelist() {
      return this.placeWhitelist;
   }

   public List<Material> getBreakWhitelist() {
      return this.breakWhitelist;
   }

   public List<Material> getPlaceBlacklist() {
      return this.placeBlacklist;
   }

   public List<Material> getBreakBlacklist() {
      return this.breakBlacklist;
   }

   public boolean isBreakPlacedBlocks() {
      return this.breakPlacedBlocks;
   }

   public List<EntityType> getDamageProtectedEntities() {
      return this.damageProtectedEntities;
   }

   public List<EntityType> getInteractProtectedEntities() {
      return this.interactProtectedEntities;
   }

   public boolean isAccessCooldownEnabled() {
      return this.accessCooldownEnabled;
   }

   public boolean isCooldownOnFinish() {
      return this.cooldownOnFinish;
   }

   public boolean isCooldownOnLeave() {
      return this.cooldownOnLeave;
   }

   public boolean isCooldownOnLoseLives() {
      return this.cooldownOnLoseLives;
   }

   public boolean isCooldownOnStart() {
      return this.cooldownOnStart;
   }

   public boolean isCooldownsPerReward() {
      return this.cooldownsPerReward;
   }

   public List<PlayerLootData> getLootCooldowns() {
      return this.lootCooldowns;
   }

   public List<AbstractInstance> getInstances() {
      return this.instances;
   }

   public InstanceEditable getEditSession() {
      return this.editSession;
   }

   public void setEditSession(InstanceEditable editSession) {
      this.editSession = editSession;
   }

   public boolean isSaving() {
      return this.saving;
   }

   public void setSaving(boolean saving) {
      this.saving = saving;
   }

   public boolean isMarkedForDelete() {
      return this.markedForDelete;
   }

   public void setMarkedForDelete(boolean markedForDelete) {
      this.markedForDelete = markedForDelete;
   }
}

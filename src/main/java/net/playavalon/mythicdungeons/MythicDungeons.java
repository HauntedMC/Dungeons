package net.playavalon.mythicdungeons;

import com.onarandombox.multiverseinventories.MultiverseInventories;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.papermc.paper.entity.TeleportFlag;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.citizensnpcs.Citizens;
import net.milkbowl.vault.economy.Economy;
import net.playavalon.mythicdungeons.api.MythicDungeonsService;
import net.playavalon.mythicdungeons.api.annotations.DeclaredCondition;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.chunkgenerators.FullBlockGenerator;
import net.playavalon.mythicdungeons.api.chunkgenerators.VoidGenerator;
import net.playavalon.mythicdungeons.api.config.AvalonSerializable;
import net.playavalon.mythicdungeons.api.generation.layout.Layout;
import net.playavalon.mythicdungeons.api.generation.layout.LayoutBranching;
import net.playavalon.mythicdungeons.api.generation.layout.LayoutMinecrafty;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.api.queue.QueueData;
import net.playavalon.mythicdungeons.avngui.AvnAPI;
import net.playavalon.mythicdungeons.avngui.AvnGUI;
import net.playavalon.mythicdungeons.compatibility.citizens.MythicNPCRegistry;
import net.playavalon.mythicdungeons.dungeons.conditions.ConditionMythic;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonClassic;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonContinuous;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionMythicSignal;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionMythicSkill;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionSpawnNPC;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionLootTableRewards;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionMythicLootTableRewards;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionRandomReward;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionReward;
import net.playavalon.mythicdungeons.dungeons.rewards.LootCooldown;
import net.playavalon.mythicdungeons.dungeons.rewards.LootTable;
import net.playavalon.mythicdungeons.dungeons.rewards.LootTableItem;
import net.playavalon.mythicdungeons.dungeons.rewards.PlayerLootData;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerMobDeath;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerMythicMobDeath;
import net.playavalon.mythicdungeons.dungeons.variables.VariableEditMode;
import net.playavalon.mythicdungeons.gui.GUIHandler;
import net.playavalon.mythicdungeons.gui.HotbarMenuHandler;
import net.playavalon.mythicdungeons.gui.PlayGUIHandler;
import net.playavalon.mythicdungeons.gui.RecruitGUIHandler;
import net.playavalon.mythicdungeons.gui.RoomGUIHandler;
import net.playavalon.mythicdungeons.listeners.AvalonListener;
import net.playavalon.mythicdungeons.listeners.DynamicListener;
import net.playavalon.mythicdungeons.listeners.MythicListener;
import net.playavalon.mythicdungeons.listeners.MythicPartyListener;
import net.playavalon.mythicdungeons.listeners.PAPIPlaceholders;
import net.playavalon.mythicdungeons.listeners.PaperListener;
import net.playavalon.mythicdungeons.listeners.dungeonlisteners.EditListener;
import net.playavalon.mythicdungeons.listeners.dungeonlisteners.InstanceListener;
import net.playavalon.mythicdungeons.listeners.dungeonlisteners.PlayListener;
import net.playavalon.mythicdungeons.managers.CommandManager;
import net.playavalon.mythicdungeons.managers.ConditionManager;
import net.playavalon.mythicdungeons.managers.DungeonManager;
import net.playavalon.mythicdungeons.managers.DungeonTypeManager;
import net.playavalon.mythicdungeons.managers.FunctionManager;
import net.playavalon.mythicdungeons.managers.HotbarMenuManager;
import net.playavalon.mythicdungeons.managers.LayoutManager;
import net.playavalon.mythicdungeons.managers.LootTableManager;
import net.playavalon.mythicdungeons.managers.MovingBlockManager;
import net.playavalon.mythicdungeons.managers.PartyManager;
import net.playavalon.mythicdungeons.managers.PartyProviderManager;
import net.playavalon.mythicdungeons.managers.PartyRecruitmentManager;
import net.playavalon.mythicdungeons.managers.PlayerManager;
import net.playavalon.mythicdungeons.managers.QueueManager;
import net.playavalon.mythicdungeons.managers.TriggerManager;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.PartyWrapper;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import net.playavalon.mythicdungeons.utility.ServerVersion;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.ReflectionUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.tasks.ProcessTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;

public final class MythicDungeons extends JavaPlugin implements MythicDungeonsService {
   private static MythicDungeons plugin;
   public static String debugPrefix;
   private FileConfiguration config;
   private FileConfiguration defaultDungeonConfig;
   private Material functionBuilderMaterial;
   private Material roomEditorMaterial;
   private boolean stuckKillsPlayer;
   private boolean inheritedVelocityEnabled;
   private boolean paperSupport;
   private boolean supportsTeleportFlags;
   private boolean supportsTeleportAsync = true;
   private boolean supportsFAWE;
   private AvnAPI avnAPI;
   private MythicBukkit mythicApi;
   private Citizens citizensApi;
   private MythicNPCRegistry mythicNPCRegistry;
   private Economy economy;
   private MultiverseInventories multiverseInv;
   private boolean betonEnabled;
   private boolean faweEnabled;
   private boolean partiesEnabled;
   private String partyPluginName;
   private boolean thirdPartyProvider;
   private File dungeonFiles;
   private File backupFolder;
   private File playerDataFolder;
   private PlayerManager playerManager;
   private PartyManager partyManager;
   private PartyProviderManager providerManager;
   private DungeonManager dungeons;
   private List<AbstractInstance> activeInstances;
   private FunctionManager functionManager;
   private TriggerManager triggerManager;
   private ConditionManager conditionManager;
   private QueueManager queueManager;
   private LootTableManager lootTableManager;
   private MovingBlockManager movingBlockManager;
   private PartyRecruitmentManager listingManager;
   private HotbarMenuManager hotbarMenus;
   private CommandManager commandManager;
   private DynamicListener elementEventHandler = new DynamicListener();
   private DynamicListener instanceEventHandler = new DynamicListener();

   public void onEnable() {
      plugin = this;
      this.saveDefaultConfig();
      this.config = this.getConfig();
      this.defaultDungeonConfig = new YamlConfiguration();
      debugPrefix = Util.fullColor("<#9753f5>[Dungeons] ");
      if (!ServerVersion.get().isAfterOrEqual(ServerVersion.v1_19_3)) {
         inst().getLogger().severe(Util.colorize(debugPrefix + "ALERT :: UNSUPPORTED MINECRAFT VERSION. Mythic Dungeons is designed for Minecraft 1.19.3+!!"));
         inst()
            .getLogger()
            .severe(Util.colorize(debugPrefix + "-- Function labels will not appear when editing dungeons and there may be an increase in memory leaks!"));
      }

      try {
         Class.forName("com.destroystokyo.paper.ParticleBuilder");
         this.paperSupport = true;

         try {
            Class.forName("io.papermc.paper.entity.TeleportFlag");
            this.supportsTeleportFlags = true;

            try {
               Class<?> clazz = Class.forName("org.bukkit.entity.Entity");
               clazz.getMethod("teleportAsync", Location.class, TeleportCause.class, TeleportFlag[].class);
            } catch (NoSuchMethodException | ClassNotFoundException var7) {
               this.supportsTeleportAsync = false;
            }
         } catch (ClassNotFoundException var8) {
         }
      } catch (ClassNotFoundException var9) {
         inst()
            .getLogger()
            .info(
               Util.colorize(
                  debugPrefix
                     + "&eWARNING :: You're not using Paper Spigot! It is highly advised to use Paper (or a fork of Paper) for better dungeon performance!"
               )
            );
      }

      ReflectionUtils.prepMemoryLeakKiller();
      this.dungeonFiles = new File(plugin.getDataFolder(), "maps");
      this.backupFolder = new File(plugin.getDataFolder(), "backups");
      if (!this.backupFolder.exists() || !this.backupFolder.isDirectory()) {
         this.backupFolder.mkdir();
      }

      this.playerDataFolder = new File(plugin.getDataFolder(), "globalplayerdata");
      if (!this.playerDataFolder.exists()) {
         this.playerDataFolder.mkdir();
      }

      LangUtils.init();
      LangUtils.saveMissingValues();

      try {
         this.functionBuilderMaterial = Material.getMaterial(this.config.getString("General.FunctionBuilderItem", "FEATHER"));
      } catch (IllegalArgumentException var6) {
         inst().getLogger().info(Util.colorize("&cWARNING :: FunctionBuilderItem in config.yml must be a valid material! Using FEATHER by default..."));
         this.functionBuilderMaterial = Material.FEATHER;
      }

      try {
         this.roomEditorMaterial = Material.getMaterial(this.config.getString("General.RoomEditorItem", "GOLDEN_AXE"));
      } catch (IllegalArgumentException var5) {
         inst().getLogger().info(Util.colorize("&cWARNING :: RoomEditorItem in config.yml must be a valid material! Using GOLDEN_AXE by default..."));
         this.roomEditorMaterial = Material.GOLDEN_AXE;
      }

      this.stuckKillsPlayer = this.config.getBoolean("General.StuckKillsPlayer", false);
      this.inheritedVelocityEnabled = this.config.getBoolean("Experimental.MovingBlocksMoveEntities", false);
      this.avnAPI = new AvnAPI(this);
      AvnGUI.debug = false;
      ConfigurationSerialization.registerClass(PlayerLootData.class);
      ConfigurationSerialization.registerClass(LootCooldown.class);
      ConfigurationSerialization.registerClass(FunctionTargetType.class);
      ConfigurationSerialization.registerClass(LootTable.class);
      ConfigurationSerialization.registerClass(LootTableItem.class);
      ConfigurationSerialization.registerClass(VariableEditMode.class);
      this.partyPluginName = this.config.getString("General.PartyPlugin", "Default");
      this.playerManager = new PlayerManager();
      this.partyManager = new PartyManager();
      this.providerManager = new PartyProviderManager();
      this.activeInstances = new ArrayList<>();
      this.functionManager = new FunctionManager();
      this.triggerManager = new TriggerManager();
      this.conditionManager = new ConditionManager();
      this.queueManager = new QueueManager();
      this.lootTableManager = new LootTableManager();
      this.commandManager = new CommandManager(this);
      this.movingBlockManager = new MovingBlockManager();
      if (this.paperSupport) {
         this.getLogger().info("Using PaperListener...");
         Bukkit.getPluginManager().registerEvents(new PaperListener(), this);
      } else {
         Bukkit.getPluginManager().registerEvents(new AvalonListener(), this);
      }

      Bukkit.getPluginManager().registerEvents(new MythicPartyListener(), this);
      if (!this.partyPluginName.equalsIgnoreCase("Default") && !this.partyPluginName.equalsIgnoreCase("DungeonParties")) {
         if (Bukkit.getPluginManager().getPlugin(this.partyPluginName) != null) {
            this.partiesEnabled = true;
            String beton = this.partyPluginName.toLowerCase();
            switch (beton) {
//               case "heroes":
//                  this.thirdPartyProvider = true;
//                  this.listingManager = new PartyRecruitmentManager();
//                  Bukkit.getPluginManager().registerEvents(new HeroesListener(), plugin);
//                  break;
//               case "parties":
//                  this.thirdPartyProvider = true;
//                  this.listingManager = new PartyRecruitmentManager();
//                  Bukkit.getPluginManager().registerEvents(new PartiesListener(), plugin);
//                  break;
               default:
                  if (Bukkit.getPluginManager().getPlugin(this.partyPluginName) == null) {
                     this.partiesEnabled = false;
                     inst().getLogger().info(Util.colorize("&cERROR :: '" + this.partyPluginName + "' was not found! Party support not enabled."));
                  }
            }

            if (this.partiesEnabled) {
               inst().getLogger().info(Util.fullColor("&d" + this.partyPluginName + " plugin found! Enabled party support."));
               RecruitGUIHandler.initPartyBrowser();
               PartyWrapper.setPartyPlugin(this.partyPluginName);
            }
         } else {
            inst().getLogger().info(Util.colorize("&cERROR :: Party plugin is set to '" + this.partyPluginName + "', but no such plugin was found!"));
         }
      } else {
         this.partiesEnabled = true;
         this.listingManager = new PartyRecruitmentManager();
         System.out.println(Util.fullColor("Using default parties! Enabled party support."));
         RecruitGUIHandler.initPartyBrowser();
      }

      this.hotbarMenus = new HotbarMenuManager();
      Bukkit.getPluginManager().registerEvents(this.hotbarMenus, this);
      if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
         inst().getLogger().info(Util.fullColor("&dMythicMobs plugin found! Enabled MythicMobs support."));
         this.mythicApi = MythicBukkit.inst();
         Bukkit.getPluginManager().registerEvents(new MythicListener(), this);
      }

      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         new PAPIPlaceholders(this).register();
      }

      if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
         this.citizensApi = (Citizens)Bukkit.getPluginManager().getPlugin("Citizens");
         this.mythicNPCRegistry = new MythicNPCRegistry();
      }

      this.setupEconomy();
      AvalonSerializable.register(this);
      this.registerInstanceListener(PlayListener.class);
      this.registerInstanceListener(EditListener.class);
      this.registerDungeonType(DungeonClassic.class, "classic", "default");
      this.registerDungeonType(DungeonContinuous.class, "continuous", "ongoing");
      this.registerDungeonType(DungeonProcedural.class, "procedural", "generated", "room");
      this.registerLayout(LayoutMinecrafty.class, "minecrafty", "minecraft", "vanilla", "random");
      this.registerLayout(LayoutBranching.class, "branching", "branch", "linear");
      this.registerFunctions("net.playavalon.mythicdungeons.dungeons.functions");
      this.registerFunction(FunctionReward.class);
      this.registerFunction(FunctionRandomReward.class);
      this.registerTriggers("net.playavalon.mythicdungeons.dungeons.triggers");
      this.registerConditions("net.playavalon.mythicdungeons.dungeons.conditions");
      if (this.mythicApi != null) {
         this.registerFunction(FunctionMythicLootTableRewards.class);
         this.registerFunction(FunctionMythicSkill.class);
         this.registerFunction(FunctionMythicSignal.class);
         this.registerTrigger(TriggerMythicMobDeath.class);
         this.registerCondition(ConditionMythic.class);
      } else {
         this.registerFunction(FunctionLootTableRewards.class);
         this.registerTrigger(TriggerMobDeath.class);
      }

      if (this.citizensApi != null) {
         this.registerFunction(FunctionSpawnNPC.class);
      }

      inst().getLogger().info(Util.fullColor("&d* Loaded &6" + this.functionManager.getAll().size() + " &dfunctions."));
      inst().getLogger().info(Util.fullColor("&d* Loaded &6" + this.triggerManager.getAll().size() + " &dtriggers."));
      inst().getLogger().info(Util.fullColor("&d* Loaded &6" + this.conditionManager.getAll().size() + " &dconditions."));
      GUIHandler.initFunctionMenu();
      GUIHandler.initTriggerMenu();
      GUIHandler.initConditionsMenus();
      GUIHandler.initGateTriggerMenus();
      GUIHandler.initItemSelectTriggerMenu();
      GUIHandler.initItemSelectFunctionMenu();
      PlayGUIHandler.initRewardMenu();
      PlayGUIHandler.initRevivalMenu();
      GUIHandler.initMultiFunctionMenus();
      RoomGUIHandler.initConnectorWhitelist();
      HotbarMenuHandler.initEditMenu();
      HotbarMenuHandler.initRoomEditMenu();
      HotbarMenuHandler.initRoomRulesMenu();
      inst().getLogger().info(Util.fullColor("&dGUI menus initialized!"));
      this.dungeons = new DungeonManager();
      if (Bukkit.getOnlinePlayers().size() > 0) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getPlayerManager().put(player);
         }
      }

      inst().getLogger().info(Util.fullColor("&dMythic Dungeons v" + this.getVersion() + " &ainitialized! Happy dungeon-ing!"));
      this.getServer().getServicesManager().register(MythicDungeonsService.class, this, this, ServicePriority.Normal);
      Bukkit.getScheduler()
         .runTaskLater(this, () -> this.multiverseInv = (MultiverseInventories)Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Inventories"), 1L);
   }

   private boolean setupEconomy() {
      if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
         return false;
      } else {
         RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
         if (rsp == null) {
            return false;
         } else {
            this.economy = (Economy)rsp.getProvider();
            return this.economy != null;
         }
      }
   }

   public void onDisable() {
      inst().getLogger().info(Util.fullColor("&dCleaning up dungeons..."));

      for (Player player : Bukkit.getOnlinePlayers()) {
         MythicPlayer aPlayer = this.getMythicPlayer(player);
         aPlayer.restoreHotbar();
         AbstractInstance instance = aPlayer.getInstance();
         if (instance != null) {
            aPlayer.getInstance().removePlayer(aPlayer);
         }
      }

      if (this.dungeons != null) {
         for (AbstractDungeon dungeon : this.dungeons.getAll()) {
            for (AbstractInstance instance : new ArrayList<>(dungeon.getInstances())) {
               instance.dispose();
            }
         }

         this.movingBlockManager.disable();
      }
   }

   public void reload() {
      this.reloadAllDungeons();
      this.reloadConfigs();
   }

   public void reloadAllDungeons() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         MythicPlayer aPlayer = this.getMythicPlayer(player);
         aPlayer.restoreHotbar();
         AbstractInstance instance = aPlayer.getInstance();
         if (instance != null) {
            instance.removePlayer(aPlayer);
            aPlayer.getPlayer().sendMessage(debugPrefix + Util.colorize("&cThe dungeon was reloaded by an admin!"));
         }
      }

      new ProcessTimer().run("Reloading Dungeons", () -> this.dungeons = new DungeonManager());
   }

   public void reloadDungeon(AbstractDungeon dungeon) {
      for (AbstractInstance instance : new ArrayList<>(dungeon.getInstances())) {
         for (MythicPlayer aPlayer : new ArrayList<>(instance.getPlayers())) {
            aPlayer.restoreHotbar();
            instance.removePlayer(aPlayer);
            aPlayer.getPlayer().sendMessage(debugPrefix + Util.colorize("&cThe dungeon was reloaded by an admin!"));
         }
      }

      this.dungeons.remove(dungeon);
      this.dungeons.loadDungeon(dungeon.getFolder());
   }

   public void reloadConfigs() {
      this.reloadConfig();
      this.config = this.getConfig();

      try {
         this.functionBuilderMaterial = Material.getMaterial(this.config.getString("General.FunctionBuilderItem", "FEATHER"));
      } catch (IllegalArgumentException var2) {
         inst().getLogger().info(Util.colorize("&cWARNING: FunctionBuilderItem in config.yml must be a valid material! Using FEATHER by default..."));
         this.functionBuilderMaterial = Material.FEATHER;
      }

      this.stuckKillsPlayer = this.config.getBoolean("General.StuckKillsPlayer", false);
      LangUtils.init();
      this.lootTableManager = new LootTableManager();
   }

   public String getVersion() {
      return this.getDescription().getVersion().split("-")[0];
   }

   public String getBuildNumber() {
      String[] split = this.getDescription().getVersion().split("-");
      if (split.length == 2) {
         return split[1];
      } else {
         return split.length == 3 ? split[2] : "????";
      }
   }

   @Nullable
   public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
      if (id == null) {
         return super.getDefaultWorldGenerator(worldName, id);
      } else if (id.toLowerCase().startsWith("block") && id.contains(".")) {
         String[] split = id.split("\\.", 2);
         Material material = Material.STONE;

         try {
            material = Material.valueOf(split[1]);
         } catch (IllegalArgumentException var6) {
         }

         return new FullBlockGenerator(material);
      } else {
         String split = id.toLowerCase();
         byte material = -1;
         switch (split.hashCode()) {
            case 3625364:
               if (split.equals("void")) {
                  material = 0;
               }
            default:
               switch (material) {
                  case 0:
                     return new VoidGenerator();
                  default:
                     return super.getDefaultWorldGenerator(worldName, id);
               }
         }
      }
   }

   public MythicPlayer getMythicPlayer(Player player) {
      return this.getMythicPlayer(player.getUniqueId());
   }

   public MythicPlayer getMythicPlayer(UUID uuid) {
      return this.playerManager.get(uuid);
   }

   public boolean sendToDungeon(Player player, String dungeonName) {
      return this.sendToDungeon(player, dungeonName, "DEFAULT");
   }

   public boolean sendToDungeon(Player player, String dungeonName, String difficulty) {
      MythicPlayer aPlayer = this.getMythicPlayer(player);
      AbstractDungeon targetDungeon = this.dungeons.get(dungeonName);
      if (this.partiesEnabled) {
         IDungeonParty party = aPlayer.getDungeonParty();
         if (this.thirdPartyProvider) {
            if (party == null) {
               party = new PartyWrapper(aPlayer);
            }

            if (party.getPlayers().size() == 1) {
               party = null;
            }
         }

         if (party != null) {
            if (this.config.getBoolean("General.LeaderOnlyQueue", true) && player != aPlayer.getDungeonParty().getLeader()) {
               LangUtils.sendMessage(player, "commands.play.party-lead-only");
               return false;
            }

            boolean success = true;

            for (Player partyPlayer : party.getPlayers()) {
               MythicPlayer mPlayer = this.getMythicPlayer(partyPlayer);
               if (mPlayer.getInstance() != null) {
                  LangUtils.sendMessage(player, "commands.play.player-in-dungeon", partyPlayer.getName());
                  success = false;
               }
            }

            if (!success) {
               return false;
            }

            if (!targetDungeon.partyMeetsRequirements(party)) {
               return false;
            }

            party.setAwaitingDungeon(true);
         } else {
            if (!targetDungeon.partyMeetsRequirements(player)) {
               return false;
            }

            aPlayer.setAwaitingDungeon(true);
         }
      } else {
         if (!targetDungeon.partyMeetsRequirements(player)) {
            return false;
         }

         aPlayer.setAwaitingDungeon(true);
      }

      QueueData queue = new QueueData(aPlayer, targetDungeon, difficulty);
      this.queueManager.enqueue(queue);
      if (this.activeInstances.size() < this.config.getInt("General.MaxInstances", 10) && targetDungeon.hasAvailableInstances()) {
         boolean immediate = aPlayer.getDungeonParty() == null
            || aPlayer.getDungeonParty().getPlayers().size() == 1
            || !this.config.getBoolean("General.ReadyCheckOnCommand", true);
         queue.enterDungeon(immediate);
         return true;
      } else {
         LangUtils.sendMessage(player, "commands.play.instances-full");
         LangUtils.sendMessage(player, "commands.play.how-to-cancel");
         return true;
      }
   }

   public File getDungeonsFolder() {
      return this.dungeonFiles;
   }

   public DungeonManager getDungeonManager() {
      return this.dungeons;
   }

   public <T extends AbstractDungeon> void registerDungeonType(Class<T> type, String name, String... aliases) {
      DungeonTypeManager.register(type, name, aliases);
   }

   public <T extends Layout> void registerLayout(Class<T> type, String name, String... aliases) {
      LayoutManager.register(type, name, aliases);
   }

   public <T extends InstanceListener> void registerInstanceListener(Class<T> type) {
      List<Method> eventMethods = new ArrayList<>();
      ReflectionUtils.getAnnotatedMethods(eventMethods, type, EventHandler.class);

      for (Method method : eventMethods) {
         EventHandler handler = method.getAnnotation(EventHandler.class);
         Class<?> rawEvent = method.getParameterTypes()[0];
         if (Event.class.isAssignableFrom(rawEvent)) {
            Class<? extends Event> eventClass = rawEvent.asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(eventClass, inst().getInstanceEventHandler(), handler.priority(), (listener, event) -> {
               for (AbstractInstance inst : inst().getActiveInstances()) {
                  if (eventClass.isAssignableFrom(event.getClass())) {
                     InstanceListener instListener = inst.getListener();
                     if (instListener != null && type.isAssignableFrom(instListener.getClass())) {
                        try {
                           method.invoke(instListener, event);
                        } catch (Exception var10) {
                           var10.printStackTrace();
                        }
                     }
                  }
               }
            }, inst());
         }
      }
   }

   public <T extends DungeonFunction> void registerFunction(Class<T> function) {
      this.functionManager.register(function);
      ConfigurationSerialization.registerClass(function);
   }

   public <T extends DungeonTrigger> void registerTrigger(Class<T> trigger) {
      this.triggerManager.register(trigger);
      ConfigurationSerialization.registerClass(trigger);
   }

   public <T extends TriggerCondition> void registerCondition(Class<T> condition) {
      this.conditionManager.register(condition);
      ConfigurationSerialization.registerClass(condition);
   }

   @SuppressWarnings("unchecked")
   public void registerFunctions(String functionsPackage) {
      for (Class<?> clazz : new Reflections(functionsPackage).getTypesAnnotatedWith(DeclaredFunction.class)) {
         if (DungeonFunction.class.isAssignableFrom(clazz)) {
            this.registerFunction((Class<? extends DungeonFunction>) clazz);
         }
      }
   }

   @SuppressWarnings("unchecked")
   public void registerTriggers(String triggersPackage) {
      for (Class<?> clazz : new Reflections(triggersPackage).getTypesAnnotatedWith(DeclaredTrigger.class)) {
         if (DungeonTrigger.class.isAssignableFrom(clazz)) {
            this.registerTrigger((Class<? extends DungeonTrigger>) clazz);
         }
      }
   }

   @SuppressWarnings("unchecked")
   public void registerConditions(String conditionsPackage) {
      for (Class<?> clazz : new Reflections(conditionsPackage).getTypesAnnotatedWith(DeclaredCondition.class)) {
         if (TriggerCondition.class.isAssignableFrom(clazz)) {
            this.registerCondition((Class<? extends TriggerCondition>) clazz);
         }
      }
   }

   public static MythicDungeons inst() {
      return (MythicDungeons)getPlugin(MythicDungeons.class);
   }

   private boolean checkIfInternalPartySystem() {
      if (!this.partiesEnabled || !this.partyPluginName.equals("DungeonParties") && !this.partyPluginName.equals("Default")) {
         this.getLogger().info("Cannot process API call. Party system is not enabled or the internal party system is not selected.");
         return false;
      } else {
         return true;
      }
   }

   @Override
   public boolean isPlayerInDungeon(Player player) {
      return !this.getPlayerManager().contains(player) ? false : this.getPlayerManager().get(player.getUniqueId()).getInstance() != null;
   }

   @Override
   public AbstractInstance getDungeonInstance(Player player) {
      return !this.getPlayerManager().contains(player) ? null : this.getPlayerManager().get(player.getUniqueId()).getInstance();
   }

   @Override
   public AbstractInstance getDungeonInstance(String worldName) {
      for (AbstractInstance instance : this.activeInstances) {
         if (instance.getInstanceWorld().getName().equals(worldName)) {
            return instance;
         }
      }

      return null;
   }

   @Override
   public Collection<AbstractDungeon> getAllDungeons() {
      return this.dungeons.getAll();
   }

   @Override
   public boolean initiateDungeonForPlayer(Player player, String dungeonName) {
      AbstractDungeon dungeon;
      if ((dungeon = this.getDungeonManager().get(dungeonName)) == null) {
         LangUtils.sendMessage(player, "commands.play.dungeon-not-found", dungeonName);
         return false;
      } else {
         MythicPlayer mPlayer = this.getMythicPlayer(player);
         if (mPlayer.getInstance() != null && mPlayer.getInstance().getDungeon() != dungeon) {
            LangUtils.sendMessage(player, "commands.play.player-in-dungeon", player.getName());
            return false;
         } else if (!Util.hasPermission(player, "dungeons.play")) {
            LangUtils.sendMessage(player, "commands.play.requirements-not-met", player.getName());
            return false;
         } else {
            this.sendToDungeon(player, dungeonName);
            return false;
         }
      }
   }

   @Override
   public boolean createParty(Player target) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(target);
         if (mythicPlayer.getMythicParty() != null) {
            return false;
         } else {
            mythicPlayer.setMythicParty(new MythicParty(target));
            return true;
         }
      }
   }

   @Override
   public boolean removeFromParty(Player target) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(target);
         MythicParty party = mythicPlayer.getMythicParty();
         if (party == null) {
            return false;
         } else {
            party.removePlayer(mythicPlayer.getPlayer());
            return true;
         }
      }
   }

   @Override
   public boolean disbandParty(MythicParty party) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         party.disband();
         return true;
      }
   }

   @Override
   public boolean disbandParty(Player target) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(target);
         MythicParty party = mythicPlayer.getMythicParty();
         if (party == null) {
            return false;
         } else {
            party.disband();
            return true;
         }
      }
   }

   @Override
   public boolean inviteToParty(Player source, Player target) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         MythicPlayer sourceMythicPlayer = this.getMythicPlayer(source);
         if (!sourceMythicPlayer.hasParty()) {
            return false;
         } else {
            MythicPlayer targetMythicPlayer = this.getMythicPlayer(target);
            if (targetMythicPlayer.hasParty()) {
               return false;
            } else {
               targetMythicPlayer.setInviteFrom(source);
               return true;
            }
         }
      }
   }

   @Override
   public boolean acceptPartyInvite(Player target) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(target);
         if (mythicPlayer.hasParty()) {
            return false;
         } else if (mythicPlayer.getInviteFrom() == null) {
            return false;
         } else {
            Player inviter = mythicPlayer.getInviteFrom();
            MythicPlayer inviterMythicPlayer = this.getMythicPlayer(inviter);
            MythicParty party = inviterMythicPlayer.getMythicParty();
            if (party == null) {
               return false;
            } else {
               party.addPlayer(mythicPlayer.getPlayer());
               return true;
            }
         }
      }
   }

   @Override
   public boolean declinePartyInvite(Player target) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(target);
         if (mythicPlayer.hasParty()) {
            return false;
         } else if (mythicPlayer.getInviteFrom() == null) {
            return false;
         } else {
            mythicPlayer.setInviteFrom(null);
            return false;
         }
      }
   }

   @Override
   public boolean setPartyLeader(Player player) {
      if (!this.checkIfInternalPartySystem()) {
         return false;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(player);
         if (!mythicPlayer.hasParty()) {
            return false;
         } else {
            MythicParty party = mythicPlayer.getMythicParty();
            party.setMythicLeader(player);
            return true;
         }
      }
   }

   @Override
   public MythicParty getParty(Player player) {
      if (!this.checkIfInternalPartySystem()) {
         return null;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(player);
         return !mythicPlayer.hasParty() ? null : mythicPlayer.getMythicParty();
      }
   }

   @Nullable
   @Override
   public String isPartyQueuedForDungeon(Player player) {
      MythicPlayer mythicPlayer = this.getMythicPlayer(player);
      QueueData queue = this.queueManager.getQueue(mythicPlayer);
      return queue == null ? null : queue.getDungeon().getWorldName();
   }

   @Nullable
   @Override
   public String isPartyQueuedForDungeon(IDungeonParty party) {
      if (party.getLeader() == null) {
         return null;
      } else {
         MythicPlayer mythicPlayer = this.getMythicPlayer(party.getLeader().getUniqueId());
         QueueData queue = this.queueManager.getQueue(mythicPlayer);
         return queue == null ? null : queue.getDungeon().getWorldName();
      }
   }

   public FileConfiguration getDefaultDungeonConfig() {
      return this.defaultDungeonConfig;
   }

   public Material getFunctionBuilderMaterial() {
      return this.functionBuilderMaterial;
   }

   public Material getRoomEditorMaterial() {
      return this.roomEditorMaterial;
   }

   public boolean isStuckKillsPlayer() {
      return this.stuckKillsPlayer;
   }

   public boolean isInheritedVelocityEnabled() {
      return this.inheritedVelocityEnabled;
   }

   public boolean isPaperSupport() {
      return this.paperSupport;
   }

   public boolean isSupportsTeleportFlags() {
      return this.supportsTeleportFlags;
   }

   public boolean isSupportsTeleportAsync() {
      return this.supportsTeleportAsync;
   }

   public boolean isSupportsFAWE() {
      return this.supportsFAWE;
   }

   public AvnAPI getAvnAPI() {
      return this.avnAPI;
   }

   public MythicBukkit getMythicApi() {
      return this.mythicApi;
   }

   public Citizens getCitizensApi() {
      return this.citizensApi;
   }

   public MythicNPCRegistry getMythicNPCRegistry() {
      return this.mythicNPCRegistry;
   }

   public Economy getEconomy() {
      return this.economy;
   }

   public MultiverseInventories getMultiverseInv() {
      return this.multiverseInv;
   }

   public boolean isBetonEnabled() {
      return this.betonEnabled;
   }

   public void setBetonEnabled(boolean betonEnabled) {
      this.betonEnabled = betonEnabled;
   }

   public boolean isFaweEnabled() {
      return this.faweEnabled;
   }

   public void setFaweEnabled(boolean faweEnabled) {
      this.faweEnabled = faweEnabled;
   }

   public boolean isPartiesEnabled() {
      return this.partiesEnabled;
   }

   public String getPartyPluginName() {
      return this.partyPluginName;
   }

   public boolean isThirdPartyProvider() {
      return this.thirdPartyProvider;
   }

   public File getDungeonFiles() {
      return this.dungeonFiles;
   }

   public File getBackupFolder() {
      return this.backupFolder;
   }

   public File getPlayerDataFolder() {
      return this.playerDataFolder;
   }

   public PlayerManager getPlayerManager() {
      return this.playerManager;
   }

   public PartyManager getPartyManager() {
      return this.partyManager;
   }

   public PartyProviderManager getProviderManager() {
      return this.providerManager;
   }

   public DungeonManager getDungeons() {
      return this.dungeons;
   }

   public List<AbstractInstance> getActiveInstances() {
      return this.activeInstances;
   }

   public FunctionManager getFunctionManager() {
      return this.functionManager;
   }

   public TriggerManager getTriggerManager() {
      return this.triggerManager;
   }

   public ConditionManager getConditionManager() {
      return this.conditionManager;
   }

   public QueueManager getQueueManager() {
      return this.queueManager;
   }

   public LootTableManager getLootTableManager() {
      return this.lootTableManager;
   }

   public MovingBlockManager getMovingBlockManager() {
      return this.movingBlockManager;
   }

   public PartyRecruitmentManager getListingManager() {
      return this.listingManager;
   }

   public HotbarMenuManager getHotbarMenus() {
      return this.hotbarMenus;
   }

   public CommandManager getCommandManager() {
      return this.commandManager;
   }

   public DynamicListener getElementEventHandler() {
      return this.elementEventHandler;
   }

   public DynamicListener getInstanceEventHandler() {
      return this.instanceEventHandler;
   }
}

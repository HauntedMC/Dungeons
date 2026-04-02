package nl.hauntedmc.dungeons;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import nl.hauntedmc.dungeons.api.annotations.DeclaredCondition;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.chunkgenerators.FullBlockGenerator;
import nl.hauntedmc.dungeons.api.chunkgenerators.VoidGenerator;
import nl.hauntedmc.dungeons.api.config.SerializableFile;
import nl.hauntedmc.dungeons.api.generation.layout.Layout;
import nl.hauntedmc.dungeons.api.generation.layout.LayoutBranching;
import nl.hauntedmc.dungeons.api.generation.layout.LayoutMinecrafty;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.api.queue.QueueData;
import nl.hauntedmc.dungeons.api.gui.GUIAPI;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonClassic;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonContinuous;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import nl.hauntedmc.dungeons.dungeons.functions.rewards.FunctionLootTableRewards;
import nl.hauntedmc.dungeons.dungeons.functions.rewards.FunctionRandomReward;
import nl.hauntedmc.dungeons.dungeons.functions.rewards.FunctionReward;
import nl.hauntedmc.dungeons.dungeons.rewards.LootCooldown;
import nl.hauntedmc.dungeons.dungeons.rewards.LootTable;
import nl.hauntedmc.dungeons.dungeons.rewards.LootTableItem;
import nl.hauntedmc.dungeons.dungeons.rewards.PlayerLootData;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerMobDeath;
import nl.hauntedmc.dungeons.dungeons.variables.VariableEditMode;
import nl.hauntedmc.dungeons.gui.inv.GUIHandler;
import nl.hauntedmc.dungeons.gui.inv.HotbarMenuHandler;
import nl.hauntedmc.dungeons.gui.inv.PlayGUIHandler;
import nl.hauntedmc.dungeons.gui.inv.RecruitGUIHandler;
import nl.hauntedmc.dungeons.gui.inv.RoomGUIHandler;
import nl.hauntedmc.dungeons.listeners.DynamicListener;
import nl.hauntedmc.dungeons.listeners.PAPIPlaceholders;
import nl.hauntedmc.dungeons.listeners.PaperListener;
import nl.hauntedmc.dungeons.listeners.dungeonlisteners.EditListener;
import nl.hauntedmc.dungeons.listeners.dungeonlisteners.InstanceListener;
import nl.hauntedmc.dungeons.listeners.dungeonlisteners.PlayListener;
import nl.hauntedmc.dungeons.managers.CommandManager;
import nl.hauntedmc.dungeons.managers.ConditionManager;
import nl.hauntedmc.dungeons.managers.DungeonManager;
import nl.hauntedmc.dungeons.managers.DungeonTypeManager;
import nl.hauntedmc.dungeons.managers.FunctionManager;
import nl.hauntedmc.dungeons.managers.HotbarMenuManager;
import nl.hauntedmc.dungeons.managers.LayoutManager;
import nl.hauntedmc.dungeons.managers.LootTableManager;
import nl.hauntedmc.dungeons.managers.MovingBlockManager;
import nl.hauntedmc.dungeons.managers.PartyProviderManager;
import nl.hauntedmc.dungeons.managers.PartyRecruitmentManager;
import nl.hauntedmc.dungeons.managers.PlayerManager;
import nl.hauntedmc.dungeons.managers.QueueManager;
import nl.hauntedmc.dungeons.managers.TriggerManager;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.player.party.DungeonPartyWrapper;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.version.ReflectionUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.tasks.ProcessTimer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;

public final class Dungeons extends JavaPlugin {
    public static String logPrefix;
    private FileConfiguration config;
    private FileConfiguration defaultDungeonConfig;
    private Material functionBuilderMaterial;
    private Material roomEditorMaterial;
    private boolean stuckKillsPlayer;
    private boolean inheritedVelocityEnabled;
    private GUIAPI GUIAPI;
    private Economy economy;
    private boolean partiesEnabled;
    private String partyPluginName;
    private boolean thirdPartyProvider;
    private File dungeonFiles;
    private File backupFolder;
    private PlayerManager playerManager;
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
    private final DynamicListener elementEventHandler = new DynamicListener();
    private final DynamicListener instanceEventHandler = new DynamicListener();

    public void onEnable() {
        Dungeons plugin = this;
        this.saveDefaultConfig();
        this.config = this.getConfig();
        this.defaultDungeonConfig = new YamlConfiguration();
        this.thirdPartyProvider = false;
        logPrefix = HelperUtils.fullColor("<#9753f5>[Dungeons] ");

        this.dungeonFiles = new File(plugin.getDataFolder(), "maps");
        this.backupFolder = new File(plugin.getDataFolder(), "backups");
        if (!this.backupFolder.exists() || !this.backupFolder.isDirectory()) {
            this.backupFolder.mkdir();
        }

        File playerDataFolder = new File(plugin.getDataFolder(), "globalplayerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdir();
        }

        LangUtils.init();
        LangUtils.saveMissingValues();

        try {
            this.functionBuilderMaterial = Material.getMaterial(this.config.getString("General.FunctionBuilderItem", "FEATHER"));
        } catch (IllegalArgumentException var6) {
            inst().getLogger().info(HelperUtils.colorize("&cWARNING :: FunctionBuilderItem in config.yml must be a valid material! Using FEATHER by default..."));
            this.functionBuilderMaterial = Material.FEATHER;
        }

        try {
            this.roomEditorMaterial = Material.getMaterial(this.config.getString("General.RoomEditorItem", "GOLDEN_AXE"));
        } catch (IllegalArgumentException var5) {
            inst().getLogger().info(HelperUtils.colorize("&cWARNING :: RoomEditorItem in config.yml must be a valid material! Using GOLDEN_AXE by default..."));
            this.roomEditorMaterial = Material.GOLDEN_AXE;
        }

        this.stuckKillsPlayer = this.config.getBoolean("General.StuckKillsPlayer", false);
        this.inheritedVelocityEnabled = this.config.getBoolean("Experimental.MovingBlocksMoveEntities", false);
        this.GUIAPI = new GUIAPI(this);
        ConfigurationSerialization.registerClass(PlayerLootData.class);
        ConfigurationSerialization.registerClass(LootCooldown.class);
        ConfigurationSerialization.registerClass(FunctionTargetType.class);
        ConfigurationSerialization.registerClass(LootTable.class);
        ConfigurationSerialization.registerClass(LootTableItem.class);
        ConfigurationSerialization.registerClass(VariableEditMode.class);
        this.partyPluginName = this.config.getString("General.PartyPlugin", "Default");
        this.playerManager = new PlayerManager();
        this.providerManager = new PartyProviderManager();
        this.activeInstances = new ArrayList<>();
        this.functionManager = new FunctionManager();
        this.triggerManager = new TriggerManager();
        this.conditionManager = new ConditionManager();
        this.queueManager = new QueueManager();
        this.lootTableManager = new LootTableManager();
        new CommandManager(this);
        this.movingBlockManager = new MovingBlockManager();
        Bukkit.getPluginManager().registerEvents(new PaperListener(), this);

        if (this.partyPluginName.equalsIgnoreCase("Default")) {
            this.partiesEnabled = true;
            this.listingManager = new PartyRecruitmentManager();
            RecruitGUIHandler.initPartyBrowser();
        } else if (this.partyPluginName.equalsIgnoreCase("None")) {
            this.partiesEnabled = false;
        } else {
            if (Bukkit.getPluginManager().getPlugin(this.partyPluginName) != null) {
                this.partiesEnabled = true;

                if (Bukkit.getPluginManager().getPlugin(this.partyPluginName) == null) {
                    this.partiesEnabled = false;
                    inst().getLogger().info(HelperUtils.colorize("&cERROR :: '" + this.partyPluginName + "' was not found! Party support not enabled."));
                }

                if (this.partiesEnabled) {
                    inst().getLogger().info(HelperUtils.fullColor("&d" + this.partyPluginName + " plugin found! Enabled party support."));
                    RecruitGUIHandler.initPartyBrowser();
                    DungeonPartyWrapper.setPartyPlugin(this.partyPluginName);
                }
            } else {
                inst().getLogger().info(HelperUtils.colorize("&cERROR :: Party plugin is set to '" + this.partyPluginName + "', but no such plugin was found!"));
            }
        }

        HotbarMenuManager hotbarMenus = new HotbarMenuManager();
        Bukkit.getPluginManager().registerEvents(hotbarMenus, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIPlaceholders().register();
        }

        this.setupEconomy();
        SerializableFile.register(this);
        this.registerInstanceListener(PlayListener.class);
        this.registerInstanceListener(EditListener.class);
        this.registerDungeonType(DungeonClassic.class, "classic", "default");
        this.registerDungeonType(DungeonContinuous.class, "continuous", "ongoing");
        this.registerDungeonType(DungeonProcedural.class, "procedural", "generated", "room");
        this.registerLayout(LayoutMinecrafty.class, "minecrafty", "minecraft", "vanilla", "random");
        this.registerLayout(LayoutBranching.class, "branching", "branch", "linear");
        this.registerFunctions("nl.hauntedmc.dungeons.dungeons.functions");
        this.registerFunction(FunctionReward.class);
        this.registerFunction(FunctionRandomReward.class);
        this.registerTriggers("nl.hauntedmc.dungeons.dungeons.triggers");
        this.registerConditions("nl.hauntedmc.dungeons.dungeons.conditions");
        this.registerFunction(FunctionLootTableRewards.class);
        this.registerTrigger(TriggerMobDeath.class);

        inst().getLogger().info(HelperUtils.fullColor("&d* Loaded &6" + this.functionManager.getAll().size() + " &dfunctions."));
        inst().getLogger().info(HelperUtils.fullColor("&d* Loaded &6" + this.triggerManager.getAll().size() + " &dtriggers."));
        inst().getLogger().info(HelperUtils.fullColor("&d* Loaded &6" + this.conditionManager.getAll().size() + " &dconditions."));
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
        inst().getLogger().info(HelperUtils.fullColor("&dGUI menus initialized!"));
        this.dungeons = new DungeonManager();
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getPlayerManager().put(player);
            }
        }

        inst().getLogger().info(HelperUtils.fullColor("&dDungeons v" + this.getVersion() + " &ainitialized! Happy dungeon-ing!"));
    }

    private void setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
            }
        }
    }

    public void onDisable() {
        inst().getLogger().info(HelperUtils.fullColor("&dCleaning up dungeons..."));

        for (Player player : Bukkit.getOnlinePlayers()) {
            DungeonPlayer aPlayer = this.getDungeonPlayer(player);
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

    public void reloadAllDungeons() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            DungeonPlayer aPlayer = this.getDungeonPlayer(player);
            aPlayer.restoreHotbar();
            AbstractInstance instance = aPlayer.getInstance();
            if (instance != null) {
                instance.removePlayer(aPlayer);
                aPlayer.getPlayer().sendMessage(logPrefix + HelperUtils.colorize("&cThe dungeon was reloaded by an admin!"));
            }
        }

        new ProcessTimer().run("Reloading Dungeons", () -> this.dungeons = new DungeonManager());
    }

    public void reloadDungeon(AbstractDungeon dungeon) {
        for (AbstractInstance instance : new ArrayList<>(dungeon.getInstances())) {
            for (DungeonPlayer aPlayer : new ArrayList<>(instance.getPlayers())) {
                aPlayer.restoreHotbar();
                instance.removePlayer(aPlayer);
                aPlayer.getPlayer().sendMessage(logPrefix + HelperUtils.colorize("&cThe dungeon was reloaded by an admin!"));
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
            inst().getLogger().info(HelperUtils.colorize("&cWARNING: FunctionBuilderItem in config.yml must be a valid material! Using FEATHER by default..."));
            this.functionBuilderMaterial = Material.FEATHER;
        }

        this.stuckKillsPlayer = this.config.getBoolean("General.StuckKillsPlayer", false);
        LangUtils.init();
        this.lootTableManager = new LootTableManager();
    }

    public String getVersion() {
        return this.getPluginMeta().getVersion().split("-")[0];
    }

    @Nullable
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        if (id == null) {
            return super.getDefaultWorldGenerator(worldName, null);
        } else if (id.toLowerCase().startsWith("block") && id.contains(".")) {
            String[] split = id.split("\\.", 2);
            Material material = Material.STONE;

            try {
                material = Material.valueOf(split[1]);
            } catch (IllegalArgumentException ignored) {
            }

            return new FullBlockGenerator(material);
        } else {
            String split = id.toLowerCase();
            byte material = -1;
            if (split.hashCode() == 3625364) {
                if (split.equals("void")) {
                    material = 0;
                }
            }
            if (material == 0) {
                return new VoidGenerator();
            }
            return super.getDefaultWorldGenerator(worldName, id);
        }
    }

    public DungeonPlayer getDungeonPlayer(Player player) {
        return this.getDungeonPlayer(player.getUniqueId());
    }

    public DungeonPlayer getDungeonPlayer(UUID uuid) {
        return this.playerManager.get(uuid);
    }

    public void sendToDungeon(Player player, String dungeonName) {
        this.sendToDungeon(player, dungeonName, "DEFAULT");
    }

    public void sendToDungeon(Player player, String dungeonName, String difficulty) {
        DungeonPlayer aPlayer = this.getDungeonPlayer(player);
        AbstractDungeon targetDungeon = this.dungeons.get(dungeonName);
        if (this.partiesEnabled) {
            IDungeonParty party = aPlayer.getiDungeonParty();
            if (this.thirdPartyProvider) {
                if (party == null) {
                    party = new DungeonPartyWrapper(aPlayer);
                }

                if (party.getPlayers().size() == 1) {
                    party = null;
                }
            }

            if (party != null) {
                if (this.config.getBoolean("General.LeaderOnlyQueue", true) && player != aPlayer.getiDungeonParty().getLeader()) {
                    LangUtils.sendMessage(player, "commands.play.party-lead-only");
                    return;
                }

                boolean success = true;

                for (Player partyPlayer : party.getPlayers()) {
                    DungeonPlayer mPlayer = this.getDungeonPlayer(partyPlayer);
                    if (mPlayer.getInstance() != null) {
                        LangUtils.sendMessage(player, "commands.play.player-in-dungeon", partyPlayer.getName());
                        success = false;
                    }
                }

                if (!success) {
                    return;
                }

                if (!targetDungeon.partyMeetsRequirements(party)) {
                    return;
                }

                party.setAwaitingDungeon(true);
            } else {
                if (!targetDungeon.partyMeetsRequirements(player)) {
                    return;
                }

                aPlayer.setAwaitingDungeon(true);
            }
        } else {
            if (!targetDungeon.partyMeetsRequirements(player)) {
                return;
            }

            aPlayer.setAwaitingDungeon(true);
        }

        QueueData queue = new QueueData(aPlayer, targetDungeon, difficulty);
        this.queueManager.enqueue(queue);
        if (this.activeInstances.size() < this.config.getInt("General.MaxInstances", 10) && targetDungeon.hasAvailableInstances()) {
            boolean immediate = aPlayer.getiDungeonParty() == null
                    || aPlayer.getiDungeonParty().getPlayers().size() == 1
                    || !this.config.getBoolean("General.ReadyCheckOnCommand", true);
            queue.enterDungeon(immediate);
        } else {
            LangUtils.sendMessage(player, "commands.play.instances-full");
            LangUtils.sendMessage(player, "commands.play.how-to-cancel");
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
                                } catch (Exception ignored) {
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

    public static Dungeons inst() {
        return getPlugin(Dungeons.class);
    }

    public boolean isPlayerInDungeon(Player player) {
        return this.getPlayerManager().contains(player) && this.getPlayerManager().get(player.getUniqueId()).getInstance() != null;
    }

    public AbstractInstance getDungeonInstance(Player player) {
        return !this.getPlayerManager().contains(player) ? null : this.getPlayerManager().get(player.getUniqueId()).getInstance();
    }


    public AbstractInstance getDungeonInstance(String worldName) {
        for (AbstractInstance instance : this.activeInstances) {
            if (instance.getInstanceWorld().getName().equals(worldName)) {
                return instance;
            }
        }

        return null;
    }

    public Collection<AbstractDungeon> getAllDungeons() {
        return this.dungeons.getAll();
    }


    public boolean initiateDungeonForPlayer(Player player, String dungeonName) {
        AbstractDungeon dungeon;
        if ((dungeon = this.getDungeonManager().get(dungeonName)) == null) {
            LangUtils.sendMessage(player, "commands.play.dungeon-not-found", dungeonName);
            return false;
        } else {
            DungeonPlayer mPlayer = this.getDungeonPlayer(player);
            if (mPlayer.getInstance() != null && mPlayer.getInstance().getDungeon() != dungeon) {
                LangUtils.sendMessage(player, "commands.play.player-in-dungeon", player.getName());
                return false;
            } else if (!HelperUtils.hasPermission(player, "dungeons.play")) {
                LangUtils.sendMessage(player, "commands.play.requirements-not-met", player.getName());
                return false;
            } else {
                this.sendToDungeon(player, dungeonName);
                return false;
            }
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

    public GUIAPI getAvnAPI() {
        return this.GUIAPI;
    }

    public Economy getEconomy() {
        return this.economy;
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

    public PlayerManager getPlayerManager() {
        return this.playerManager;
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

    public DynamicListener getElementEventHandler() {
        return this.elementEventHandler;
    }

    public DynamicListener getInstanceEventHandler() {
        return this.instanceEventHandler;
    }
}

package nl.hauntedmc.dungeons.model.dungeon;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.config.ConfigSyncUtils;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.config.DungeonConfigView;
import nl.hauntedmc.dungeons.config.DungeonRuleConfiguration;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.cooldown.CooldownPeriod;
import nl.hauntedmc.dungeons.model.cooldown.CooldownSchedule;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.PluginEnvironment;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonAccessKeyService;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonIdentity;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonPlayerDataStore;
import nl.hauntedmc.dungeons.runtime.dungeon.InstanceWorldCleanup;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.team.TeamRequirementPolicy;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
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
import org.slf4j.Logger;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Base model for a dungeon definition loaded from disk.
 *
 * <p>The dungeon object owns configuration-backed gameplay rules, persistence-backed player data,
 * and the collection of live instances spawned from the map.
 */
public abstract class DungeonDefinition {
    private static final int DEFAULT_MAX_TEAM_SIZE = 4;
    private final DungeonsRuntime runtime;
    private final UUID dungeonId;
    protected final String worldName;
    protected final File folder;
    protected final FileConfiguration config;
    protected boolean enabled;
    protected String displayName;
    protected int maxTeamSize;
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
    protected List<String> joinCommands;
    protected List<AccessKeyDefinition> accessKeys;
    protected List<ItemStack> validKeys;
    protected boolean onlyLeaderNeedsKey;
    protected List<Material> placeWhitelist;
    protected List<Material> breakWhitelist;
    protected List<Material> placeBlacklist;
    protected List<Material> breakBlacklist;
    protected boolean breakPlacedBlocks;
    protected List<EntityType> damageProtectedEntities;
    protected List<EntityType> interactProtectedEntities;
    protected boolean accessCooldownEnabled;
    protected boolean onlyLeaderNeedsCooldown;
    protected boolean cooldownOnFinish;
    protected boolean cooldownOnLeave;
    protected boolean cooldownOnLoseLives;
    protected boolean cooldownOnStart;

    private final DungeonPlayerDataStore playerDataStore;
    private final DungeonAccessKeyService accessKeyService;
    protected List<DungeonInstance> instances;
    protected EditableInstance editSession;
    protected volatile boolean saving;
    private String durableConfiguration;
    private boolean repositoryInitialized;

    public boolean isRepositoryInitialized() {
        return repositoryInitialized;
    }

    public void refreshRepositoryState() {
        repositoryInitialized =
                nl.hauntedmc.dungeons.repository.RepoLocal.valid(folder.toPath(), dungeonId);
    }

    /** Loads a dungeon definition from disk or from a preloaded configuration snapshot. */
    public DungeonDefinition(
            @NotNull DungeonsRuntime runtime,
            @NotNull File folder,
            @Nullable YamlConfiguration loadedConfig)
            throws DungeonLoadException {
        this.runtime = runtime;
        this.worldName = folder.getName();
        this.folder = folder;
        this.config = new DungeonRuleConfiguration(this.runtime::config);
        try {
            if (loadedConfig != null) {
                this.config.loadFromString(loadedConfig.saveToString());
            } else {
                File configFile = new File(folder, "config.yml");
                if (!configFile.exists()) {
                    this.logger().info("Creating fresh config file for '{}'.", folder.getName());
                    this.config.load(new File(this.runtime.dungeonFiles(), "config_default.yml"));
                    this.config.set("rules", null);
                } else {
                    this.config.load(configFile);
                }
            }
            DungeonIdentity.existing(this.config);
            File configFile = new File(folder, "config.yml");
            if (!configFile.exists()) {
                this.config.save(configFile);
            }
        } catch (IOException exception) {
            DungeonLoadException failure =
                    new DungeonLoadException(
                            "Access of config.yml failed!", false, exception.getMessage());
            failure.initCause(exception);
            throw failure;
        } catch (InvalidConfigurationException
                | IllegalArgumentException
                | YAMLException exception) {
            DungeonLoadException failure =
                    new DungeonLoadException(
                            "Dungeon config is invalid!", true, exception.getMessage());
            failure.initCause(exception);
            throw failure;
        }

        this.dungeonId =
                DungeonIdentity.initialize(
                        this.plugin(), this.config, new File(folder, "config.yml"));
        this.refreshRepositoryState();
        this.syncConfigWithDefaults();
        this.loadRuntimeSettingsFromConfig();
        this.accessKeyService =
                new DungeonAccessKeyService(runtime, folder, this.dungeonId, () -> this.accessKeys);
        this.playerDataStore = new DungeonPlayerDataStore(this.plugin(), this.dungeonId);

        this.instances = new ArrayList<>();
        this.cleanDeadInstances();
        this.durableConfiguration = this.config.saveToString();
    }

    /**
     * Synchronizes the dungeon config with the shipped default template and writes the result back
     * to disk when keys changed.
     */
    private void syncConfigWithDefaults() throws DungeonLoadException {
        FileConfiguration defaultConfig = this.runtime.defaultDungeonConfig();
        if (defaultConfig.getKeys(true).isEmpty()) {
            this.logger()
                    .warn(
                            "Skipping config sync for dungeon '{}' because the default dungeon"
                                    + " config is empty.",
                            this.worldName);
            return;
        }

        if (!ConfigSyncUtils.syncMissingAndObsolete(
                defaultConfig,
                this.config,
                Set.of(
                        "rules",
                        "dungeon.id",
                        "statistics.custom_metrics",
                        "statistics.rankings"))) {
            return;
        }

        try {
            this.config.save(new File(this.folder, "config.yml"));
        } catch (IOException exception) {
            throw new DungeonLoadException(
                    "Failed to synchronize dungeon config with the default template!",
                    false,
                    "Check the config.yml file permissions for this map.");
        }
    }

    /** Returns the runtime that owns this dungeon definition. */
    public final DungeonsRuntime getRuntime() {
        return this.runtime;
    }

    /** Persistent identity shared by copies of this map across server deployments. */
    public final UUID getDungeonId() {
        return this.dungeonId;
    }

    protected final DungeonsRuntime runtime() {
        return this.runtime;
    }

    public final PluginEnvironment getEnvironment() {
        return this.runtime.environment();
    }

    protected final DungeonsPlugin plugin() {
        return this.runtime.environment().plugin();
    }

    protected final Logger logger() {
        return this.runtime.environment().logger();
    }

    protected final PlayerSessionRegistry playerSessions() {
        return this.runtime.playerSessions();
    }

    protected final ActiveInstanceRegistry activeInstances() {
        return this.runtime.activeInstances();
    }

    /**
     * Loads all gameplay-relevant settings from the current configuration snapshot into runtime
     * fields for faster access during play.
     */
    protected void loadRuntimeSettingsFromConfig() {
        this.config.setComments(
                "dungeon.enabled", List.of("LOCAL — NOT SYNCED: playable on this server only."));
        this.config.setComments(
                "locations.exit",
                List.of("LOCAL — NOT SYNCED: external destination on this server."));
        this.enabled = this.config.getBoolean("dungeon.enabled", true);
        this.displayName =
                ColorUtils.fullColor(
                        this.config.getString("dungeon.display_name", "&c" + this.worldName));
        int configuredMaxTeamSize = this.config.getInt("team.max_size", DEFAULT_MAX_TEAM_SIZE);
        if (configuredMaxTeamSize < 1) {
            this.logger()
                    .warn(
                            "Dungeon '{}' has invalid team.max_size value '{}'. Falling back to"
                                    + " {}.",
                            this.worldName,
                            configuredMaxTeamSize,
                            DEFAULT_MAX_TEAM_SIZE);
            configuredMaxTeamSize = DEFAULT_MAX_TEAM_SIZE;
        }

        this.maxTeamSize = configuredMaxTeamSize;
        this.lobbyEnabled = DungeonConfigView.isLobbyEnabled(this.config);

        Object genericLoc = this.config.get("locations.lobby.spawn");
        if (genericLoc instanceof Location) {
            this.lobbySpawn = (Location) genericLoc;
        } else {
            this.lobbySpawn =
                    LocationUtils.readLocation(
                            this.config.getConfigurationSection("locations.lobby.spawn"),
                            new Location(null, 0.0, 64.0, 0.0));
        }

        genericLoc = this.config.get("locations.start");
        if (genericLoc instanceof Location) {
            this.startSpawn = (Location) genericLoc;
        } else {
            this.startSpawn =
                    LocationUtils.readLocation(
                            this.config.getConfigurationSection("locations.start"));
        }

        ConfigurationSection exitLocSect = this.config.getConfigurationSection("locations.exit");
        this.exitLoc = exitLocSect == null ? null : LocationUtils.readExternalLocation(exitLocSect);
        if (this.exitLoc != null
                && this.exitLoc.getWorld() == null
                && !this.exitLoc.isWorldLoaded()) {
            this.exitLoc = null;
            this.logger()
                    .warn(
                            "Exit location for dungeon '{}' references an unloaded world. Clearing"
                                    + " exit location.",
                            this.worldName);
        }

        this.alwaysUseExit = this.config.getBoolean("locations.exit_on_leave", false);
        this.useDifficultyLevels = this.config.getBoolean("difficulty.enabled", false);
        this.showDifficultyMenu = this.config.getBoolean("difficulty.use_menu", false);
        this.difficultyLevels = new LinkedHashMap<>();
        if (this.useDifficultyLevels) {
            ConfigurationSection difficulties =
                    this.config.getConfigurationSection("difficulty.presets");
            if (difficulties != null) {
                for (String path : difficulties.getKeys(false)) {
                    ConfigurationSection difficultySection =
                            difficulties.getConfigurationSection(path);
                    if (difficultySection != null) {
                        this.difficultyLevels.put(path, new DungeonDifficulty(difficultySection));
                    }
                }
            }
        }

        this.refreshRuleSettings();
        this.joinCommands = new ArrayList<>(this.config.getStringList("players.join_commands"));
        this.accessKeys = this.loadAccessKeys();
        this.rebuildValidKeys();
        this.onlyLeaderNeedsKey = this.config.getBoolean("access.keys.leader_only", false);
        this.accessCooldownEnabled = this.config.getBoolean("access.cooldown.enabled", false);
        this.onlyLeaderNeedsCooldown = this.config.getBoolean("access.cooldown.leader_only", false);
        this.cooldownOnFinish = this.config.getBoolean("access.cooldown.on_finish", true);
        this.cooldownOnLeave = DungeonConfigView.isAccessCooldownAppliedOnLeave(this.config);
        this.cooldownOnLoseLives =
                this.config.getBoolean("access.cooldown.on_lives_depleted", false);
        this.cooldownOnStart = this.config.getBoolean("access.cooldown.on_start", false);
    }

    protected List<Material> loadMaterialList(String path) {
        List<Material> materials = new ArrayList<>();
        for (String name : this.config.getStringList(path)) {
            try {
                materials.add(Material.valueOf(name));
            } catch (IllegalArgumentException exception) {
                this.logger()
                        .warn(
                                "Dungeon '{}' has invalid material '{}' in {}.",
                                this.worldName,
                                name,
                                path,
                                exception);
            }
        }
        return materials;
    }

    private List<AccessKeyDefinition> loadAccessKeys() {
        List<AccessKeyDefinition> loadedKeys = new ArrayList<>();
        List<?> storedKeys = this.config.getList("access.keys.items");
        if (storedKeys == null) {
            return loadedKeys;
        }

        for (Object storedKey : storedKeys) {
            AccessKeyDefinition accessKey = AccessKeyDefinition.fromConfigValue(storedKey);
            if (accessKey != null) {
                loadedKeys.add(accessKey);
            } else {
                this.logger()
                        .warn(
                                "Skipping unsupported or invalid access key entry while loading"
                                        + " dungeon '{}'.",
                                this.worldName);
            }
        }

        return loadedKeys;
    }

    private void rebuildValidKeys() {
        this.validKeys =
                this.accessKeys.stream()
                        .map(AccessKeyDefinition::createItemCopy)
                        .filter(Objects::nonNull)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    protected List<EntityType> loadEntityTypeList(String path) {
        List<EntityType> types = new ArrayList<>();
        for (String name : this.config.getStringList(path)) {
            try {
                types.add(EntityType.valueOf(name));
            } catch (IllegalArgumentException exception) {
                this.logger()
                        .warn(
                                "Dungeon '{}' has invalid entity type '{}' in {}.",
                                this.worldName,
                                name,
                                path,
                                exception);
            }
        }
        return types;
    }

    /** Refreshes cached gameplay rules after global configuration changes. */
    public void refreshRuleSettings() {
        this.bannedItems =
                new ArrayList<>(this.config.getStringList("rules.items.banned_materials"));
        this.customBannedItems = new ArrayList<>();
        for (Object entry : this.config.getList("rules.items.banned_items", List.of())) {
            if (entry instanceof ItemStack item) {
                this.customBannedItems.add(item);
            } else {
                this.logger()
                        .warn(
                                "Skipping invalid banned item in dungeon '{}': {}",
                                this.worldName,
                                entry);
            }
        }

        this.placeWhitelist = this.loadMaterialList("rules.building.place_whitelist");
        this.breakWhitelist = this.loadMaterialList("rules.building.break_whitelist");
        this.placeBlacklist = this.loadMaterialList("rules.building.place_blacklist");
        this.breakBlacklist = this.loadMaterialList("rules.building.break_blacklist");
        this.breakPlacedBlocks =
                this.config.getBoolean("rules.building.break_placed_blocks", false);
        this.damageProtectedEntities = this.loadEntityTypeList("rules.entities.damage_protected");
        this.interactProtectedEntities =
                this.loadEntityTypeList("rules.entities.interact_protected");
    }

    public void refreshRuntimeSettings() {
        this.loadRuntimeSettingsFromConfig();
    }

    public boolean hasAvailableInstances() {
        int maxInstances = this.config.getInt("runs.max_active", 0);
        return maxInstances == 0 || this.getTrackedInstanceCount() < maxInstances;
    }

    protected int getTrackedInstanceCount() {
        // Reloading a definition must not hide retained worlds owned by its previous object.
        var tracked = new HashSet<>(this.instances);
        if (this.activeInstances() != null) {
            for (DungeonInstance instance : this.activeInstances().getActiveInstances()) {
                if (instance.getDungeon() != null
                        && this.worldName.equals(instance.getDungeon().getWorldName())) {
                    tracked.add(instance);
                }
            }
        }
        return tracked.size();
    }

    public boolean canAcceptStartNow(
            int requestedPlayers,
            @Nullable String difficultyName,
            int currentGlobalInstances,
            int maxGlobalInstances) {
        if (!this.isEnabled()) {
            return false;
        }

        int normalizedPlayers = Math.max(1, requestedPlayers);
        boolean hasGlobalCapacity =
                maxGlobalInstances <= 0 || currentGlobalInstances < maxGlobalInstances;
        return this.canEverFitTeamSize(normalizedPlayers)
                && hasGlobalCapacity
                && this.hasAvailableInstances();
    }

    public boolean canEverFitTeamSize(int requestedPlayers) {
        return this.canFitTeamSize(requestedPlayers);
    }

    public boolean canFitTeamSize(int requestedPlayers) {
        return Math.max(1, requestedPlayers) <= this.maxTeamSize;
    }

    public void sendTeamTooLargeMessage(Player player, int requestedPlayers) {
        LangUtils.sendMessage(
                player,
                "commands.play.team.team-too-large",
                LangUtils.placeholder("count", String.valueOf(Math.max(1, requestedPlayers))),
                LangUtils.placeholder("max", String.valueOf(this.maxTeamSize)));
    }

    protected void cleanDeadInstances() {
        new InstanceWorldCleanup(this.worldName, this.logger()).clean();
    }

    public boolean instantiate(Player player) {
        return this.instantiate(player, "DEFAULT", 1);
    }

    public boolean instantiate(Player player, String difficultyName) {
        return this.instantiate(player, difficultyName, 1);
    }

    public boolean instantiate(Player player, String difficultyName, int requestedPlayers) {
        return this.instantiate(player, difficultyName, requestedPlayers, null);
    }

    public boolean instantiate(
            Player player,
            String difficultyName,
            int requestedPlayers,
            @Nullable UUID startedTeamLeaderId) {
        if (!this.isEnabled()) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.disabled",
                    LangUtils.placeholder("dungeon", this.getWorldName()));
            return false;
        }

        int normalizedPlayers = Math.max(1, requestedPlayers);
        if (!this.canFitTeamSize(normalizedPlayers)) {
            this.sendTeamTooLargeMessage(player, normalizedPlayers);
            return false;
        }

        return this.prepareInstance(player, difficultyName, normalizedPlayers, startedTeamLeaderId);
    }

    public boolean prepareInstance(Player player, String difficultyName) {
        return this.prepareInstance(player, difficultyName, 1);
    }

    public boolean prepareInstance(Player player, String difficultyName, int requestedPlayers) {
        return this.prepareInstance(player, difficultyName, requestedPlayers, null);
    }

    public abstract boolean prepareInstance(
            Player player,
            String difficultyName,
            int requestedPlayers,
            @Nullable UUID startedTeamLeaderId);

    /** Shared cancellation/publication lifecycle for static and generated isolated runs. */
    protected final boolean prepareIsolatedInstance(
            Player player,
            String difficultyName,
            @Nullable UUID startedTeamLeaderId,
            Consumer<PlayableInstance> prepareAdmission,
            long admissionDelay) {
        DungeonPlayerSession session = this.playerSessions().get(player);
        if (session == null) return false;
        return new InstancePreparation(
                        this,
                        player,
                        session,
                        this.difficultyLevels.get(difficultyName),
                        startedTeamLeaderId,
                        this.preparationTimeout(),
                        prepareAdmission,
                        admissionDelay)
                .start();
    }

    /**
     * A wall-clock deadline covers scheduler delay, creation, initialization and final admission.
     */
    protected Duration preparationTimeout() {
        return Duration.ofSeconds(5L);
    }

    public void edit(UUID playerId) {
        this.editAsync(playerId);
    }

    public CompletableFuture<Boolean> editAsync(UUID playerId) {
        if (!this.runtime.database().ready()) return CompletableFuture.completedFuture(false);
        if (this.runtime.staffOperations() != null) {
            if (!this.runtime.staffOperations().maintenance(this.worldName)
                    || this.instances.stream().anyMatch(i -> !i.isEditInstance())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null)
                    LangUtils.sendRaw(
                            player,
                            "Enable maintenance and let play worlds drain before" + " editing.");
                return CompletableFuture.completedFuture(false);
            }
        }
        var preparation =
                new EditorPreparation(
                        this,
                        playerId,
                        PluginConfigView.getEditOpenTimeoutSeconds(this.plugin().getConfig()));
        if (Bukkit.isPrimaryThread()) preparation.start();
        else Bukkit.getScheduler().runTask(this.plugin(), preparation::start);
        return preparation.result();
    }

    public final PlayableInstance createPlaySession(CountDownLatch latch) {
        return this.createPlaySession(latch, null);
    }

    public abstract PlayableInstance createPlaySession(
            CountDownLatch latch, @Nullable UUID startedTeamLeaderId);

    public abstract EditableInstance createEditSession(CountDownLatch latch);

    public void removeInstance(DungeonInstance instance) {
        this.instances.remove(instance);
        this.activeInstances().unregisterActiveInstance(instance);
    }

    protected void discardInstance(@Nullable DungeonInstance instance) {
        if (instance == null) {
            return;
        }

        instance.dispose();
    }

    public void timeout(DungeonInstance instance, DungeonPlayerSession playerSession) {
        Player player = playerSession.getPlayer();
        this.cancelInstance(instance, playerSession);
        LangUtils.sendMessage(
                player,
                "instance.lifecycle.load-timeout",
                LangUtils.placeholder("dungeon", this.getDisplayNameComponent()));
    }

    public void cancelInstance(DungeonInstance instance, DungeonPlayerSession playerSession) {
        this.discardInstance(instance);
        playerSession.setAwaitingDungeon(false);
    }

    public abstract void addFunction(Location location, DungeonFunction function);

    public abstract void removeFunction(Location location);

    public abstract void saveFunctions();

    public abstract Map<Location, DungeonFunction> getFunctions();

    public void addAccessCooldown(Player player) {
        this.addAccessCooldown(player, this.getNextUnlockTime());
    }

    public void addAccessCooldown(Player player, Date resetTime) {
        this.playerDataStore.setAccessCooldown(player.getUniqueId(), resetTime);
    }

    public boolean clearAccessCooldown(Player player) {
        return player != null && this.clearAccessCooldown(player.getUniqueId());
    }

    public boolean clearAccessCooldown(UUID playerId) {
        return this.playerDataStore.clearAccessCooldown(playerId);
    }

    public boolean shouldApplyAccessCooldown(@Nullable DungeonInstance instance, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null
                && CommandUtils.hasPermissionSilent(player, "dungeons.bypass.cooldown")) {
            return false;
        }

        UUID startedTeamLeaderId = instance == null ? null : instance.getStartedTeamLeaderId();
        return TeamRequirementPolicy.shouldApplyAccessCooldown(
                this.onlyLeaderNeedsCooldown, startedTeamLeaderId, playerId);
    }

    public boolean hasAccessCooldown(Player player) {
        return this.playerDataStore.hasAccessCooldown(player.getUniqueId());
    }

    public Date getAccessCooldown(Player player) {
        return this.playerDataStore.getAccessCooldown(player.getUniqueId());
    }

    public DungeonPlayerDataStore playerDataStore() {
        return this.playerDataStore;
    }

    public void loadCooldowns(UUID playerID) {
        this.playerDataStore.loadCooldowns(playerID);
    }

    public void saveCooldowns(Player player) {
        this.playerDataStore.stageCooldowns(player);
    }

    public void savePlayerData(Player player) {
        this.playerDataStore.save(player);
    }

    @Nullable
    public boolean hasPlayerCompletedDungeon(Player player) {
        return this.playerDataStore.isCompleted(player.getUniqueId());
    }

    public void setPlayerCompletedDungeon(Player player) {
        this.setPlayerCompletedDungeon(player, true);
    }

    public void setPlayerCompletedDungeon(Player player, boolean complete) {
        this.playerDataStore.setCompleted(player.getUniqueId(), complete);
    }

    public void banItem(ItemStack item) {
        ItemStack vanillaItem = new ItemStack(item.getType());
        if (item.isSimilar(vanillaItem)) {
            this.bannedItems.add(item.getType().toString());
            this.config.set("rules.items.banned_materials", this.bannedItems);
        } else {
            this.customBannedItems.add(item);
            this.config.set("rules.items.banned_items", this.customBannedItems);
        }

        try {
            this.config.save(new File(this.folder, "config.yml"));
        } catch (IOException exception) {
            this.logger()
                    .error(
                            "Failed to save config while banning an item in dungeon '{}'.",
                            this.worldName,
                            exception);
        }
    }

    public boolean unbanItem(ItemStack item) {
        ItemStack vanillaItem = new ItemStack(item.getType());
        if (!this.bannedItems.contains(item.getType().toString())
                && !this.customBannedItems.contains(item)) {
            return false;
        }
        if (item.isSimilar(vanillaItem)) {
            this.bannedItems.remove(item.getType().toString());
            this.config.set("rules.items.banned_materials", this.bannedItems);
        } else {
            this.customBannedItems.remove(item);
            this.config.set("rules.items.banned_items", this.customBannedItems);
        }

        try {
            this.config.save(new File(this.folder, "config.yml"));
        } catch (IOException exception) {
            this.logger()
                    .error(
                            "Failed to save config while unbanning an item in dungeon '{}'.",
                            this.worldName,
                            exception);
        }

        return true;
    }

    public AccessKeyDefinition addAccessKey(ItemStack item, @Nullable Player addedBy) {
        AccessKeyDefinition accessKey =
                new AccessKeyDefinition(
                        item,
                        new Date(System.currentTimeMillis()),
                        addedBy == null ? null : addedBy.getName(),
                        addedBy == null ? null : addedBy.getUniqueId());
        this.accessKeys.add(accessKey);
        this.rebuildValidKeys();
        this.saveAccessKeys();
        return accessKey;
    }

    public int removeAllAccessKeys() {
        int removedCount = this.accessKeys.size();
        for (AccessKeyDefinition accessKey : this.accessKeys) {
            this.accessKeyService.revokeIssuedAccessKeysByDefinition(accessKey.getKeyId());
        }
        this.accessKeys.clear();
        this.rebuildValidKeys();
        this.saveAccessKeys();
        return removedCount;
    }

    public int removeAccessKey(ItemStack item) {
        List<String> removedKeyIds = new ArrayList<>();
        this.accessKeys.removeIf(
                accessKey -> {
                    if (!accessKey.matches(item)) {
                        return false;
                    }

                    removedKeyIds.add(accessKey.getKeyId());
                    return true;
                });
        int removedCount = removedKeyIds.size();
        if (removedCount > 0) {
            for (String removedKeyId : removedKeyIds) {
                this.accessKeyService.revokeIssuedAccessKeysByDefinition(removedKeyId);
            }
            this.rebuildValidKeys();
            this.saveAccessKeys();
        }
        return removedCount;
    }

    public boolean removeAccessKey(int keyId) {
        int index = keyId - 1;
        if (index < 0 || index >= this.accessKeys.size()) {
            return false;
        }

        AccessKeyDefinition removedKey = this.accessKeys.remove(index);
        this.accessKeyService.revokeIssuedAccessKeysByDefinition(removedKey.getKeyId());
        this.rebuildValidKeys();
        this.saveAccessKeys();
        return true;
    }

    public synchronized @Nullable ItemStack issueAccessKey(int keyId) {
        AccessKeyDefinition accessKey = this.getAccessKey(keyId);
        return accessKey == null ? null : this.issueAccessKey(accessKey);
    }

    public ItemStack isValidKey(ItemStack item) {
        return this.accessKeyService.isValidKey(item);
    }

    public synchronized @Nullable ItemStack issueAccessKey(
            @Nullable AccessKeyDefinition accessKey) {
        return this.accessKeyService.issueAccessKey(accessKey);
    }

    public synchronized boolean reserveIssuedAccessKey(@Nullable ItemStack item) {
        return this.accessKeyService.reserveIssuedAccessKey(item);
    }

    public synchronized boolean releaseReservedAccessKey(@Nullable ItemStack item) {
        return this.accessKeyService.releaseReservedAccessKey(item);
    }

    public synchronized boolean isReservedAccessKey(@Nullable ItemStack item) {
        return this.accessKeyService.isReservedAccessKey(item);
    }

    public synchronized boolean consumeAccessKey(@Nullable ItemStack item) {
        return this.accessKeyService.consumeAccessKey(item);
    }

    public synchronized @Nullable ItemStack rotateAccessKey(@Nullable ItemStack item) {
        return this.accessKeyService.rotateAccessKey(item);
    }

    public boolean refundReservedAccessKey(@Nullable DungeonPlayerSession playerSession) {
        if (playerSession == null || !playerSession.hasReservedAccessKey(this.worldName)) {
            return true;
        }

        ItemStack reservedKey = playerSession.getReservedAccessKey(this.worldName);
        if (reservedKey == null || reservedKey.getType().isAir()) return false;
        var settlement = this.accessKeyService.prepareSettlement(reservedKey, true, false);
        return playerSession.settleReservedAccessKey(this.worldName, settlement, true);
    }

    public boolean completeReservedAccessKey(@Nullable DungeonPlayerSession playerSession) {
        if (playerSession == null || !playerSession.hasReservedAccessKey(this.worldName)) {
            return true;
        }

        ItemStack reservedKey = playerSession.getReservedAccessKey(this.worldName);
        if (reservedKey == null || reservedKey.getType() == Material.AIR) {
            Player reservedKeyOwner = playerSession.getPlayer();
            this.logger()
                    .warn(
                            "Reserved access key for dungeon '{}' could not be recovered for player"
                                    + " '{}'.",
                            this.worldName,
                            reservedKeyOwner == null ? "unknown" : reservedKeyOwner.getName());
            return false;
        }

        var settlement =
                this.accessKeyService.prepareSettlement(
                        reservedKey,
                        false,
                        this.config.getBoolean("access.keys.consume_on_entry", true));
        return playerSession.settleReservedAccessKey(this.worldName, settlement, false);
    }

    private void saveAccessKeys() {
        this.config.set("access.keys.items", this.accessKeys);
        if (this.config.get("access.keys.consume_on_entry") == null) {
            this.config.set("access.keys.consume_on_entry", true);
        }

        this.saveConfig();
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
            this.config.set("dungeon.chunk_generator", generator);
            this.saveConfig();
        }
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
        this.config.set("locations.lobby.enabled", this.lobbyEnabled);
        Location lobbyLocation = lobbySpawn.clone();
        lobbyLocation.setWorld(null);
        LocationUtils.writeLocation("locations.lobby.spawn", this.config, lobbyLocation);
        this.saveConfig();
    }

    public void setStartSpawn(Location startSpawn) {
        this.startSpawn = startSpawn;
        Location startLocation = startSpawn.clone();
        startLocation.setWorld(null);
        LocationUtils.writeLocation("locations.start", this.config, startLocation);
        this.saveConfig();
    }

    public void setExit(@Nullable Location exitLoc) {
        this.exitLoc = exitLoc;
        if (exitLoc == null) {
            this.config.set("locations.exit", null);
        } else {
            Location target = exitLoc.clone();
            target.setWorld(null);
            LocationUtils.writeExternalLocation("locations.exit", this.config, exitLoc);
        }
        this.saveConfig();
    }

    public Date getNextUnlockTime() {
        if (!this.config.getBoolean("access.cooldown.enabled", false)) {
            return new Date();
        }
        CooldownPeriod period =
                CooldownPeriod.valueOf(DungeonConfigView.getAccessCooldownPeriodName(this.config));
        int cooldownTime = DungeonConfigView.getAccessCooldownValue(this.config);
        if (period == CooldownPeriod.TIMER) {
            return period.fromNow(cooldownTime);
        }
        return CooldownSchedule.nextReset(
                period, cooldownTime, DungeonConfigView.getAccessCooldownResetDay(this.config));
    }

    public void setSaveConfig(String path, Object value) {
        this.config.set(path, value);
        this.saveConfig();
        this.refreshRuntimeSettings();
    }

    public void saveConfig() {
        try {
            String snapshot = this.config.saveToString();
            ConfigurationFile.saveSnapshot(
                    this.plugin(), new File(this.folder, "config.yml"), snapshot);
            this.durableConfiguration = snapshot;
        } catch (RuntimeException failure) {
            if (this.durableConfiguration != null) {
                try {
                    this.config.loadFromString(this.durableConfiguration);
                    this.refreshRuntimeSettings();
                } catch (InvalidConfigurationException rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        }
    }

    @Nullable
    public StaticDungeon asStatic() {
        return this.as(StaticDungeon.class);
    }

    @Nullable
    public BranchingDungeon asBranching() {
        return this.as(BranchingDungeon.class);
    }

    @Nullable
    public <T extends DungeonDefinition> T as(Class<T> clazz) {
        return clazz.isInstance(this) ? clazz.cast(this) : null;
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

    /** Administrator-authored formatting is explicit; ordinary placeholder strings stay literal. */
    public Component getDisplayNameComponent() {
        return LangUtils.trusted(this.displayName);
    }

    public boolean isEnabled() {
        return this.enabled
                && this.repositoryInitialized
                && this.runtime.database().ready()
                && (this.runtime.staffOperations() == null
                        || !this.runtime.staffOperations().maintenance(this.worldName));
    }

    public int getMaxTeamSize() {
        return this.maxTeamSize;
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

    public List<String> getJoinCommands() {
        return List.copyOf(this.joinCommands);
    }

    public List<AccessKeyDefinition> getAccessKeys() {
        return List.copyOf(this.accessKeys);
    }

    public @Nullable AccessKeyDefinition getAccessKey(int keyId) {
        int index = keyId - 1;
        if (index < 0 || index >= this.accessKeys.size()) {
            return null;
        }

        return this.accessKeys.get(index);
    }

    public List<ItemStack> getValidKeys() {
        return List.copyOf(this.validKeys);
    }

    public boolean isOnlyLeaderNeedsKey() {
        return this.onlyLeaderNeedsKey;
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

    public boolean isOnlyLeaderNeedsCooldown() {
        return this.onlyLeaderNeedsCooldown;
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

    public List<DungeonInstance> getInstances() {
        return this.instances;
    }

    public EditableInstance getEditSession() {
        return this.editSession;
    }

    public void setEditSession(EditableInstance editSession) {
        this.editSession = editSession;
    }

    public boolean isSaving() {
        return this.saving;
    }

    public void setSaving(boolean saving) {
        this.saving = saving;
    }
}

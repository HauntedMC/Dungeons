package nl.hauntedmc.dungeons.model.dungeon;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import nl.hauntedmc.dungeons.content.reward.CooldownPeriod;
import nl.hauntedmc.dungeons.content.reward.LootCooldown;
import nl.hauntedmc.dungeons.content.reward.PlayerLootData;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.PluginEnvironment;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.team.TeamRequirementPolicy;
import nl.hauntedmc.dungeons.util.config.ConfigSyncUtils;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
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

/**
 * Base model for a dungeon definition loaded from disk.
 *
 * <p>The dungeon object owns configuration-backed gameplay rules, persistence-backed player data,
 * and the collection of live instances spawned from the map.</p>
 */
public abstract class DungeonDefinition {
    private static final int DEFAULT_MAX_TEAM_SIZE = 4;
    private final DungeonsRuntime runtime;
    protected final String worldName;
    protected final File folder;
    protected final FileConfiguration config;
    protected FileConfiguration lootConfig;
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
    protected Map<UUID, Date> accessCooldownsByPlayer;
    protected boolean cooldownOnFinish;
    protected boolean cooldownOnLeave;
    protected boolean cooldownOnLoseLives;
    protected boolean cooldownOnStart;
    protected Map<UUID, FileConfiguration> playerData;
    protected boolean cooldownsPerReward;
    protected List<PlayerLootData> lootCooldowns;
    protected Map<UUID, PlayerLootData> lootCooldownsByPlayer;
    protected List<DungeonInstance> instances;
    protected EditableInstance editSession;
    protected volatile boolean saving;
    protected volatile boolean markedForDelete;

    /**
     * Loads a dungeon definition from disk or from a preloaded configuration snapshot.
     */
    public DungeonDefinition(
            @NotNull DungeonsRuntime runtime,
            @NotNull File folder,
            @Nullable YamlConfiguration loadedConfig)
            throws DungeonLoadException {
        this.runtime = runtime;
        this.worldName = folder.getName();
        this.folder = folder;
        if (loadedConfig != null) {
            this.config = loadedConfig;
        } else {
            this.config = new YamlConfiguration();

            try {
                File configFile = new File(folder, "config.yml");
                if (!configFile.exists()) {
                    this.logger().info("Creating fresh config file for '{}'.", folder.getName());
                    FileUtils.copyFile(
                                                        new File(this.runtime.dungeonFiles(), "config_default.yml"), configFile);
                }

                this.config.load(configFile);
            } catch (IOException exception) {
                                throw new DungeonLoadException(
                        "Access of config.yml file failed!",
                        false,
                        "There may be another process accessing the file, or we may not have permission.");
            } catch (InvalidConfigurationException | IllegalArgumentException | YAMLException exception) {
                                throw new DungeonLoadException("Dungeon config has invalid YAML! See error below...", true);
            }
        }

        this.syncConfigWithDefaults();
        this.loadRuntimeSettingsFromConfig();
        this.accessCooldownsByPlayer = new HashMap<>();
        this.lootConfig = new YamlConfiguration();
        this.lootCooldowns = new ArrayList<>();
        this.lootCooldownsByPlayer = new HashMap<>();
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
                    } catch (InvalidConfigurationException | IOException exception) {
                        this.logger()
                                .error(
                                        "Failed to load player data '{}' for dungeon '{}'.",
                                        playerFile.getAbsolutePath(),
                                        this.worldName,
                                        exception);
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

    /**
     * Synchronizes the dungeon config with the shipped default template and writes the result back to
     * disk when keys changed.
     */
    private void syncConfigWithDefaults() throws DungeonLoadException {
        FileConfiguration defaultConfig = this.runtime.defaultDungeonConfig();
        if (defaultConfig.getKeys(true).isEmpty()) {
            this.logger()
                    .warn(
                            "Skipping config sync for dungeon '{}' because the default dungeon config is empty.",
                            this.worldName);
            return;
        }

        if (!ConfigSyncUtils.syncMissingAndObsolete(defaultConfig, this.config)) {
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

    /**
     * Returns the runtime that owns this dungeon definition.
     */
    public final DungeonsRuntime getRuntime() {
        return this.runtime;
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

        protected final org.slf4j.Logger logger() {
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
    @SuppressWarnings("unchecked")
    protected void loadRuntimeSettingsFromConfig() {
        this.displayName =
                ColorUtils.fullColor(this.config.getString("dungeon.display_name", "&c" + this.worldName));
        int configuredMaxTeamSize = this.config.getInt("team.max_size", DEFAULT_MAX_TEAM_SIZE);
        if (configuredMaxTeamSize < 1) {
            this.logger()
                    .warn(
                            "Dungeon '{}' has invalid team.max_size value '{}'. Falling back to {}.",
                            this.worldName,
                            configuredMaxTeamSize,
                            DEFAULT_MAX_TEAM_SIZE);
            configuredMaxTeamSize = DEFAULT_MAX_TEAM_SIZE;
        }

        this.maxTeamSize = configuredMaxTeamSize;
        this.lobbyEnabled = this.config.getBoolean("locations.lobby.enabled", true);

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
                    LocationUtils.readLocation(this.config.getConfigurationSection("locations.start"));
        }

        ConfigurationSection exitLocSect = this.config.getConfigurationSection("locations.exit");
        this.exitLoc = exitLocSect == null ? null : LocationUtils.readLocation(exitLocSect);
        if (this.exitLoc != null && this.exitLoc.getWorld() == null && !this.exitLoc.isWorldLoaded()) {
            this.exitLoc = null;
            this.logger()
                    .warn(
                            "Exit location for dungeon '{}' references an unloaded world. Clearing exit location.",
                            this.worldName);
        }

        this.alwaysUseExit = this.config.getBoolean("locations.exit_on_leave", false);
        this.useDifficultyLevels = this.config.getBoolean("difficulty.enabled", false);
        this.showDifficultyMenu = this.config.getBoolean("difficulty.use_menu", false);
        this.difficultyLevels = new LinkedHashMap<>();
        if (this.useDifficultyLevels) {
            ConfigurationSection difficulties = this.config.getConfigurationSection("difficulty.presets");
            if (difficulties != null) {
                for (String path : difficulties.getKeys(false)) {
                    ConfigurationSection difficultySection = difficulties.getConfigurationSection(path);
                    if (difficultySection != null) {
                        this.difficultyLevels.put(path, new DungeonDifficulty(difficultySection));
                    }
                }
            }
        }

        this.bannedItems = new ArrayList<>(this.config.getStringList("rules.items.banned_materials"));
        this.customBannedItems = (List<ItemStack>) this.config.get("rules.items.banned_items");
        if (this.customBannedItems == null) {
            this.customBannedItems = new ArrayList<>();
        }

        this.validKeys = (List<ItemStack>) this.config.get("access.keys.items");
        if (this.validKeys == null) {
            this.validKeys = new ArrayList<>();
        }

        this.onlyLeaderNeedsKey = this.config.getBoolean("access.keys.leader_only", false);

        this.placeWhitelist = this.loadMaterialList("rules.building.place_whitelist");
        this.breakWhitelist = this.loadMaterialList("rules.building.break_whitelist");
        this.placeBlacklist = this.loadMaterialList("rules.building.place_blacklist");
        this.breakBlacklist = this.loadMaterialList("rules.building.break_blacklist");
        this.breakPlacedBlocks = this.config.getBoolean("rules.building.break_placed_blocks", false);
        this.damageProtectedEntities = this.loadEntityTypeList("rules.entities.damage_protected");
        this.interactProtectedEntities = this.loadEntityTypeList("rules.entities.interact_protected");
        this.accessCooldownEnabled = this.config.getBoolean("access.cooldown.enabled", false);
        this.onlyLeaderNeedsCooldown = this.config.getBoolean("access.cooldown.leader_only", false);
        this.cooldownOnFinish = this.config.getBoolean("access.cooldown.on_finish", true);
        this.cooldownOnLeave = this.config.getBoolean("access.cooldown.on_leave", false);
        this.cooldownOnLoseLives = this.config.getBoolean("access.cooldown.on_lives_depleted", false);
        this.cooldownOnStart = this.config.getBoolean("access.cooldown.on_start", false);
        this.cooldownsPerReward =
                this.config.getBoolean("rewards.loot_cooldown.track_per_reward", true);
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

        public void refreshRuntimeSettings() {
        this.loadRuntimeSettingsFromConfig();
    }

        public boolean hasAvailableInstances() {
        int maxInstances = this.config.getInt("runs.max_active", 0);
        return maxInstances == 0 || this.getTrackedInstanceCount() < maxInstances;
    }

        protected int getTrackedInstanceCount() {
        return this.instances.size();
    }

        public boolean canAcceptStartNow(
            int requestedPlayers,
            @Nullable String difficultyName,
            int currentGlobalInstances,
            int maxGlobalInstances) {
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
        File[] worlds = Bukkit.getWorldContainer().listFiles();
        if (worlds != null) {
            for (File world : worlds) {
                if (world.isDirectory()
                        && this.isOwnedInstanceWorldFolder(world)
                        && Bukkit.getWorld(world.getName()) == null) {
                    try {
                        FileUtils.deleteDirectory(world);
                    } catch (IOException exception) {
                        this.logger()
                                .error(
                                        "Failed to delete stale instance folder '{}'.",
                                        world.getAbsolutePath(),
                                        exception);
                    }
                }
            }
        }
    }

        private boolean isOwnedInstanceWorldFolder(File worldFolder) {
        String worldName = worldFolder.getName();
        String prefix = this.folder.getName() + "_";
        if (!worldName.startsWith(prefix)) {
            return false;
        }

        String suffix = worldName.substring(prefix.length());
        if (suffix.isEmpty()) {
            return false;
        }

        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return false;
            }
        }

        return true;
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

        public void edit(UUID playerId) {
        if (this.editSession != null) {
            Bukkit.getScheduler()
                    .runTask(
                            this.plugin(),
                            () -> {
                                DungeonPlayerSession playerSession = this.playerSessions().get(playerId);
                                if (this.editSession != null && playerSession != null) {
                                    this.editSession.addPlayer(playerSession);
                                }
                            });
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<EditableInstance> sessionRef =
                new java.util.concurrent.atomic.AtomicReference<>();

        Bukkit.getScheduler()
                .runTask(
                        this.plugin(),
                        () -> {
                            if (this.editSession == null) {
                                EditableInstance created = this.createEditSession(latch);
                                if (created != null) {
                                    this.editSession = created;
                                    this.instances.add(created);
                                    this.activeInstances().registerActiveInstance(created);
                                } else {
                                    latch.countDown();
                                }
                            }

                            sessionRef.set(this.editSession);
                        });

        try {
            boolean loaded =
                    latch.await(
                            PluginConfigView.getEditOpenTimeoutSeconds(this.plugin().getConfig()),
                            TimeUnit.SECONDS);
            EditableInstance session = sessionRef.get();
            if (!loaded || session == null) {
                Bukkit.getScheduler()
                        .runTask(
                                this.plugin(),
                                () -> {
                                    if (this.editSession != null) {
                                        this.discardInstance(this.editSession);
                                        this.editSession = null;
                                    }
                                });
                this.scheduleEditMessage(playerId, "instance.editor.open-timeout");
                return;
            }

            if (session.hasLoadFailed() || !session.isReady()) {
                Bukkit.getScheduler()
                        .runTask(
                                this.plugin(),
                                () -> {
                                    if (this.editSession == session) {
                                        this.discardInstance(session);
                                        this.editSession = null;
                                    }
                                });
                this.scheduleEditMessage(playerId, "instance.editor.open-failed");
                return;
            }

            Bukkit.getScheduler()
                    .runTask(
                            this.plugin(),
                            () -> {
                                DungeonPlayerSession playerSession = this.playerSessions().get(playerId);
                                if (playerSession != null) {
                                    session.addPlayer(playerSession);
                                }
                            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Bukkit.getScheduler()
                    .runTask(
                            this.plugin(),
                            () -> {
                                if (this.editSession != null) {
                                    this.discardInstance(this.editSession);
                                    this.editSession = null;
                                }
                            });
            this.logger()
                    .error(
                            "Interrupted while waiting for edit session of dungeon '{}' to initialize.",
                            this.worldName,
                            exception);
        }
    }

        private void scheduleEditMessage(UUID playerId, String messageKey) {
        Bukkit.getScheduler()
                .runTask(
                        this.plugin(),
                        () -> {
                            Player player = Bukkit.getPlayer(playerId);
                            if (player != null && player.isOnline()) {
                                LangUtils.sendMessage(
                                        player, messageKey, LangUtils.placeholder("dungeon", this.getDisplayName()));
                            }
                        });
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

        this.removeInstance(instance);
        if (instance.getInstanceWorld() != null) {
            instance.dispose();
        } else {
            instance.cleanupPendingWorldFolder();
        }
    }

        public void timeout(DungeonInstance instance, DungeonPlayerSession playerSession) {
        Player player = playerSession.getPlayer();
        this.cancelInstance(instance, playerSession);
        nl.hauntedmc.dungeons.util.lang.LangUtils.sendMessage(
                player,
                "instance.lifecycle.load-timeout",
                LangUtils.placeholder("dungeon", this.getDisplayName()));
    }

        public void cancelInstance(DungeonInstance instance, DungeonPlayerSession playerSession) {
        this.removeInstance(instance);
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
        this.accessCooldownsByPlayer.put(player.getUniqueId(), resetTime);
    }

        public boolean clearAccessCooldown(Player player) {
        return player != null && this.clearAccessCooldown(player.getUniqueId());
    }

        public boolean clearAccessCooldown(UUID playerId) {
        return this.accessCooldownsByPlayer.remove(playerId) != null;
    }

        public boolean shouldApplyAccessCooldown(@Nullable DungeonInstance instance, UUID playerId) {
        UUID startedTeamLeaderId = instance == null ? null : instance.getStartedTeamLeaderId();
        return TeamRequirementPolicy.shouldApplyAccessCooldown(
                this.onlyLeaderNeedsCooldown, startedTeamLeaderId, playerId);
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
        return !this.hasAccessCooldown(player)
                ? null
                : this.accessCooldownsByPlayer.get(player.getUniqueId());
    }

        public void addLootCooldown(Player player, RewardFunction reward) {
        this.addLootCooldown(player, reward, this.getNextLootTime());
    }

        public void addLootCooldown(Player player, RewardFunction reward, Date resetTime) {
        PlayerLootData playerCooldown = this.lootCooldownsByPlayer.get(player.getUniqueId());
        if (playerCooldown == null) {
            playerCooldown = new PlayerLootData(player);
            this.lootCooldowns.add(playerCooldown);
            this.lootCooldownsByPlayer.put(player.getUniqueId(), playerCooldown);
        }

        playerCooldown.addLootCooldown(reward, resetTime);
    }

        public boolean hasLootCooldown(Player player, RewardFunction reward) {
        PlayerLootData playerCooldown = this.lootCooldownsByPlayer.get(player.getUniqueId());
        if (playerCooldown == null) {
            playerCooldown = new PlayerLootData(player);
            this.lootCooldowns.add(playerCooldown);
            this.lootCooldownsByPlayer.put(player.getUniqueId(), playerCooldown);
        }

        return playerCooldown.hasLootOnCooldown(reward);
    }

        public Date getLootUnlockTime(Player player, RewardFunction function) {
        PlayerLootData lootData = this.getPlayerLootData(player);
        LootCooldown cd = lootData.getLootCooldown(function);
        return cd == null ? null : cd.getResetTime();
    }

        public PlayerLootData getPlayerLootData(Player player) {
        return this.lootCooldownsByPlayer.get(player.getUniqueId());
    }

        public void loadCooldowns(UUID playerID) {
        FileConfiguration data = this.playerData.get(playerID);
        if (data.get("RewardsInfo") != null) {
            PlayerLootData playerData = (PlayerLootData) data.get("RewardsInfo");
            if (playerData != null) {
                this.lootCooldowns.add(playerData);
                this.lootCooldownsByPlayer.put(playerData.getPlayerId(), playerData);
            }
        }

        if (data.get("AccessCooldown", null) != null) {
            this.accessCooldownsByPlayer.put(playerID, new Date(data.getLong("AccessCooldown")));
        }
    }

        public void saveCooldowns(Player player) {
        UUID playerID = player.getUniqueId();
        PlayerLootData playerData = this.lootCooldownsByPlayer.get(player.getUniqueId());
        if (playerData == null) {
            playerData = new PlayerLootData(player);
            this.lootCooldowns.add(playerData);
            this.lootCooldownsByPlayer.put(player.getUniqueId(), playerData);
        }

        FileConfiguration data = this.playerData.getOrDefault(playerID, new YamlConfiguration());
        this.playerData.putIfAbsent(playerID, data);
        data.set("RewardsInfo", playerData);
        Date cooldown = this.accessCooldownsByPlayer.get(playerID);
        data.set("AccessCooldown", cooldown == null ? null : cooldown.getTime());
    }

        public void savePlayerData(Player player) {
        File playersFolder = new File(this.folder, "players");
        UUID playerID = player.getUniqueId();
        FileConfiguration data = this.playerData.getOrDefault(playerID, new YamlConfiguration());
        this.playerData.putIfAbsent(playerID, data);

        try {
            File targetFile = new File(playersFolder, playerID + ".yml");
            data.save(targetFile);
        } catch (IOException exception) {
            this.logger()
                    .error(
                            "Failed to save player data for '{}' in dungeon '{}'.",
                            playerID,
                            this.worldName,
                            exception);
        }
    }

    @Nullable public FileConfiguration getPlayerData(UUID playerID) {
        return this.playerData.get(playerID);
    }

        public boolean hasPlayerCompletedDungeon(Player player) {
        UUID playerID = player.getUniqueId();
        FileConfiguration data = this.playerData.get(playerID);
        return data != null && data.getBoolean("Finished", false);
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

        public void banItem(ItemStack item) {
        ItemStack vanillaItem = new ItemStack(item.getType());
        if (item.isSimilar(vanillaItem)) {
            this.bannedItems.add(item.getType().toString());
        } else {
            this.customBannedItems.add(item);
        }

        this.config.set("rules.items.banned_materials", this.bannedItems);
        this.config.set("rules.items.banned_items", this.customBannedItems);

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
        } else {
            if (item.isSimilar(vanillaItem)) {
                this.bannedItems.remove(item.getType().toString());
            } else {
                this.customBannedItems.remove(item);
            }

            this.config.set("rules.items.banned_materials", this.bannedItems);
            this.config.set("rules.items.banned_items", this.customBannedItems);

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
    }

        public void addAccessKey(ItemStack item) {
        this.validKeys.add(item.clone());
        this.config.set("access.keys.items", this.validKeys);
        if (this.config.get("access.keys.consume_on_entry") == null) {
            this.config.set("access.keys.consume_on_entry", true);
        }

        try {
            this.config.save(new File(this.folder, "config.yml"));
        } catch (IOException exception) {
            this.logger()
                    .error(
                            "Failed to save config while adding an access key in dungeon '{}'.",
                            this.worldName,
                            exception);
        }
    }

        public void removeAllAccessKeys() {
        this.validKeys.clear();
        this.config.set("access.keys.items", this.validKeys);
        if (this.config.get("access.keys.consume_on_entry") == null) {
            this.config.set("access.keys.consume_on_entry", true);
        }
        try {
            this.config.save(new File(this.folder, "config.yml"));
        } catch (IOException exception) {
            this.logger()
                    .error(
                            "Failed to save config while clearing access keys in dungeon '{}'.",
                            this.worldName,
                            exception);
        }
    }

        public boolean removeAccessKey(ItemStack item) {
        boolean keyFound = false;

        for (ItemStack key : new ArrayList<>(this.validKeys)) {
            if (item.isSimilar(key)) {
                this.validKeys.remove(key);
                keyFound = true;
            }
        }

        if (keyFound) {
            this.config.set("access.keys.items", this.validKeys);
            if (this.config.get("access.keys.consume_on_entry") == null) {
                this.config.set("access.keys.consume_on_entry", true);
            }

            try {
                this.config.save(new File(this.folder, "config.yml"));
            } catch (IOException exception) {
                this.logger()
                        .error(
                                "Failed to save config while removing an access key in dungeon '{}'.",
                                this.worldName,
                                exception);
            }
        }

        return keyFound;
    }

        public ItemStack isValidKey(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            for (ItemStack key : this.validKeys) {
                if (item.isSimilar(key)) {
                    return key;
                }
            }
        }
        return null;
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
            LocationUtils.writeLocation("locations.exit", this.config, target);
        }
        this.saveConfig();
    }

        public Date getNextUnlockTime() {
        if (!this.config.getBoolean("access.cooldown.enabled", false)) {
                        return new Date();
        } else {
            CooldownPeriod period =
                    CooldownPeriod.valueOf(this.config.getString("access.cooldown.period", "DAILY"));
            int cooldownTime = this.config.getInt("access.cooldown.value", 1);
            if (period == CooldownPeriod.TIMER) {
                return period.fromNow(cooldownTime);
            } else {
                Calendar cal = Calendar.getInstance();
                if (cooldownTime > cal.get(Calendar.HOUR_OF_DAY)) {
                    cal.set(Calendar.DATE, cal.get(Calendar.DATE) - 1);
                }

                cal.set(Calendar.HOUR_OF_DAY, cooldownTime);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (period == CooldownPeriod.WEEKLY) {
                    int dayOfWeek = this.config.getInt("access.cooldown.reset_day", 1);
                    if (dayOfWeek > cal.get(Calendar.DAY_OF_WEEK)) {
                        cal.set(Calendar.WEEK_OF_MONTH, cal.get(Calendar.WEEK_OF_MONTH) - 1);
                    }

                    cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
                }

                if (period == CooldownPeriod.MONTHLY) {
                    int dayOfMonth = this.config.getInt("access.cooldown.reset_day", 1);
                    if (dayOfMonth > cal.get(Calendar.DAY_OF_WEEK)) {
                        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) - 1);
                    }

                    cal.set(Calendar.DATE, dayOfMonth);
                }

                return period.fromDate(cal.getTime());
            }
        }
    }

        public Date getNextLootTime() {
        if (!this.config.getBoolean("rewards.loot_cooldown.enabled", false)) {
                        return new Date();
        } else {
            CooldownPeriod period =
                    CooldownPeriod.valueOf(this.config.getString("rewards.loot_cooldown.period", "DAILY"));
            int cooldownTime = this.config.getInt("rewards.loot_cooldown.value", 1);
            if (period == CooldownPeriod.TIMER) {
                return period.fromNow(cooldownTime);
            } else {
                Calendar cal = Calendar.getInstance();
                if (cooldownTime > cal.get(Calendar.HOUR_OF_DAY)) {
                    cal.set(Calendar.DATE, cal.get(Calendar.DATE) - 1);
                }

                cal.set(Calendar.HOUR_OF_DAY, cooldownTime);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (period == CooldownPeriod.WEEKLY) {
                    int dayOfWeek = this.config.getInt("rewards.loot_cooldown.reset_day", 1);
                    if (dayOfWeek > cal.get(Calendar.DAY_OF_WEEK)) {
                        cal.set(Calendar.WEEK_OF_MONTH, cal.get(Calendar.WEEK_OF_MONTH) - 1);
                    }

                    cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
                }

                if (period == CooldownPeriod.MONTHLY) {
                    int dayOfMonth = this.config.getInt("rewards.loot_cooldown.reset_day", 1);
                    if (dayOfMonth > cal.get(Calendar.DAY_OF_WEEK)) {
                        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) - 1);
                    }

                    cal.set(Calendar.DATE, dayOfMonth);
                }

                return period.fromDate(cal.getTime());
            }
        }
    }

        public void setSaveConfig(String path, Object value) {
        this.config.set(path, value);
        this.saveConfig();
        this.refreshRuntimeSettings();
    }

        public void saveConfig() {
        try {
            this.config.save(new File(this.folder, "config.yml"));
        } catch (IOException exception) {
            this.logger().error("Failed to save config for dungeon '{}'.", this.worldName, exception);
        }
    }

    @Nullable public StaticDungeon asStatic() {
        return this.as(StaticDungeon.class);
    }

    @Nullable public BranchingDungeon asBranching() {
        return this.as(BranchingDungeon.class);
    }

    @Nullable public <T extends DungeonDefinition> T as(Class<T> clazz) {
        return (T) (clazz.isInstance(this) ? this : null);
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

        public List<ItemStack> getValidKeys() {
        return this.validKeys;
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

        public boolean isCooldownsPerReward() {
        return this.cooldownsPerReward;
    }

        public List<PlayerLootData> getLootCooldowns() {
        return this.lootCooldowns;
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

        public boolean isMarkedForDelete() {
        return this.markedForDelete;
    }

        public void setMarkedForDelete(boolean markedForDelete) {
        this.markedForDelete = markedForDelete;
    }
}

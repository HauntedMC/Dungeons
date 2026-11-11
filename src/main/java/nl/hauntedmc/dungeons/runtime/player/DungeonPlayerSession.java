package nl.hauntedmc.dungeons.runtime.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.event.HotbarSetEvent;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.persistence.StateDocument;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonAccessKeyService;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable runtime state attached to a single player.
 *
 * <p>The session tracks inventory snapshots, active instance membership, editor state, queued
 * dungeon access, and the temporary hotbar menus used by the plugin UI.
 */
public final class DungeonPlayerSession {
    public enum EditorClipboardType {
        NONE,
        FUNCTION,
        CONNECTOR
    }

    private final DungeonsPlugin plugin;
    private Player player;
    private boolean expired;
    private DungeonInstance observing;

    public DungeonInstance getObservingInstance() {
        return this.observing;
    }

    public boolean isObserving() {
        return this.observing != null;
    }

    public void beginObservation(DungeonInstance target) {
        if (this.instance != null || this.observing != null || this.awaitingDungeon)
            throw new IllegalArgumentException("Leave your current run or queue before observing");
        if (!this.prepareRecovery()) throw new IllegalStateException("Recovery checkpoint failed");
        this.observing = target;
    }

    public boolean stopObservation() {
        this.observing = null;
        return !this.player.isOnline() || this.recoverPendingState();
    }

    private DungeonInstance instance;
    private boolean awaitingDungeon;
    private final StateDocument playerDocument;
    private final ConfigurationFile playerData;
    private List<String> completedDungeons = new ArrayList<>();
    private GameMode savedGameMode;
    private Location savedPosition;
    private float savedExp;
    private int savedLevel;
    private int savedTotalExp;
    private int savedHeldSlot;
    private Location dungeonRespawn;
    private final PlayerRecoveryJournal recoveryJournal;
    private boolean isDisconnecting;
    private boolean dead;
    private boolean editMode;
    private ItemStack[] savedInventory;
    private ItemStack[] savedArmor;
    private double savedHealth;
    private int savedFood;
    private ItemStack[] savedEditInventory;
    private ItemStack[] savedEditArmor;
    private PlayerHotbarSnapshot savedHotbar;
    private long hotbarRevision;
    private boolean hotbarRecoveryOwned;
    private DungeonFunction activeFunction;
    private DungeonTrigger activeTrigger;
    private Location targetLocation;
    private final List<PlayerHotbarSnapshot> hotbarHistory;
    private PlayerHotbarSnapshot currentHotbar;
    private boolean chatListening = false;
    private DungeonFunction copiedFunction;
    private boolean isCopying;
    private boolean isCutting;
    private EditorClipboardType clipboardType = EditorClipboardType.NONE;
    private Location pos1;
    private Location pos2;
    private boolean awaitingRoomName;
    private BranchingRoomDefinition activeRoom;
    private Connector activeConnector;
    private ConnectorDoor activeDoor;
    private boolean confirmRoomAction;
    private Connector copiedConnector;
    private boolean addingWhitelistEntry;
    private boolean editingWhitelistEntry;
    private boolean removingWhitelistEntry;
    private boolean reloadQueued;
    private String reservedAccessKeyDungeon;
    private ItemStack reservedAccessKey;
    private final List<ItemStack> pendingAccessKeyRefunds = new ArrayList<>();

    /** Creates a session for an online player and loads their global persisted data. */
    public DungeonPlayerSession(DungeonsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.hotbarHistory = new ArrayList<>();
        this.savedGameMode = Bukkit.getDefaultGameMode();
        this.playerData = new ConfigurationFile(plugin);
        this.playerDocument =
                RuntimeContext.runtime()
                        .stateStore()
                        .document(
                                new StateStore.Key(
                                        "player-profile",
                                        player.getUniqueId().toString(),
                                        player.getUniqueId()));
        this.recoveryJournal = new PlayerRecoveryJournal(player.getUniqueId());
        try {
            this.playerDocument.loadInto(this.playerData);
            this.completedDungeons = this.playerData.getStringList("CompletedDungeons");
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException(
                    "Cannot load database player data " + player.getUniqueId(), failure);
        }
    }

    public boolean hasUnresolvedRecovery() {
        return this.recoveryJournal.hasPendingTransaction();
    }

    public boolean addStaffRefunds(UUID operation, ItemStack... items) {
        this.requireActiveSession();
        return this.recoveryJournal.addStaffRefunds(operation, items);
    }

    public String recoveryStatus() {
        return "recovery="
                + this.recoveryJournal.hasRecovery()
                + ", pending transaction="
                + this.recoveryJournal.hasPendingTransaction()
                + ", key escrow="
                + this.recoveryJournal.hasKeyReservation();
    }

    public boolean isExpired() {
        return this.expired;
    }

    private void requireActiveSession() {
        if (this.expired) throw new IllegalStateException("Player session has expired");
    }

    /** Called by the registry only after run, queue and reservation ownership has ended. */
    boolean expire(boolean shutdown) {
        if (this.expired) return true;
        if ((!shutdown && this.player.isOnline())
                || this.instance != null
                || this.observing != null
                || this.awaitingDungeon
                || this.isDisconnecting
                || this.hasReservedAccessKey()) return false;
        this.persistPendingAccessKeyRefunds();
        this.recoveryJournal.checkpoint();
        this.playerData.set("CompletedDungeons", new ArrayList<>(this.completedDungeons));
        this.playerDocument.save(this.playerData.saveToString());
        // Recovery remains in the journal; none are acknowledged or delivered here.
        this.expired = true;
        this.clearRecoveredState();
        this.reloadQueued = false;
        this.completedDungeons.clear();
        this.pendingAccessKeyRefunds.clear();
        for (String key : new ArrayList<>(this.playerData.getKeys(false)))
            this.playerData.set(key, null);
        this.recoveryJournal.release();
        this.player = null;
        return true;
    }

    /** Toggles edit mode and swaps the player between their normal and editor inventories. */
    public void setEditMode(boolean editMode) {
        this.requireActiveSession();
        if (this.editMode == editMode) return;
        if (editMode && !this.recoveryJournal.hasRecovery() && !this.prepareRecovery()) {
            throw new IllegalStateException("Cannot safely enter editor mode");
        }
        this.hotbarRevision++;
        if (editMode) {
            this.saveInventory();
            this.savedHotbar = null;
            this.currentHotbar = null;
            this.hotbarHistory.clear();
        }
        this.editMode = editMode;
        if (editMode) {
            this.player.getInventory().clear();
            this.player.setGameMode(GameMode.CREATIVE);
            this.loadEditInventory();
        } else {
            this.currentHotbar = null;
            this.hotbarHistory.clear();
            this.player.getInventory().clear();
            this.restoreInventory();
            if (this.instance == null) this.completeDeparture(true, true);
        }
    }

    /**
     * Captures the player's current survival inventory and vital stats before an instance or editor
     * flow overwrites them.
     */
    public void saveInventory() {
        this.hotbarRecoveryOwned = false;
        if (!this.recoveryJournal.hasRecovery()) {
            this.recoveryJournal.begin(this.player);
            this.savedInventory = null;
        }
        if (this.savedInventory != null) return;
        PlayerRecoverySnapshot original = this.recoveryJournal.originalSnapshot(this.player);
        this.savedGameMode = original.gameMode();
        this.savedExp = original.exp();
        this.savedLevel = original.level();
        this.savedTotalExp = original.totalExp();
        this.savedHeldSlot = original.heldSlot();
        this.savedPosition = original.location().clone();
        this.savedInventory = copyItems(original.contents());
        this.savedArmor = copyItems(original.armor());
        this.savedHealth = original.health();
        this.savedFood = original.food();
    }

    private static ItemStack[] copyItems(ItemStack[] items) {
        return items == null
                ? null
                : Arrays.stream(items)
                        .map(item -> item == null ? null : item.clone())
                        .toArray(ItemStack[]::new);
    }

    /** Restores the previously captured inventory snapshot. */
    public void restoreInventory() {
        if (this.savedInventory == null || this.savedArmor == null || this.player.isDead()) return;
        this.player.getInventory().setContents(copyItems(this.savedInventory));
        this.player.getInventory().setArmorContents(copyItems(this.savedArmor));
        this.player.getInventory().setHeldItemSlot(this.savedHeldSlot);
        this.player.setHealth(Math.min(this.savedHealth, EntityUtils.getMaxHealth(this.player)));
        this.player.setFoodLevel(this.savedFood);
    }

    /**
     * Restores the player's vanilla experience values, deferring one tick when the plugin is still
     * enabled so Bukkit has finished its own respawn updates first.
     */
    public void restoreExp() {
        if (this.expired) return;
        if (this.plugin.isEnabled()) {
            Player target = this.player;
            DungeonInstance expected = this.instance;
            float experience = this.savedExp;
            int level = this.savedLevel;
            Bukkit.getScheduler()
                    .runTaskLater(
                            this.plugin,
                            () -> {
                                if (this.player != target || this.instance != expected) return;
                                this.player.setExp(experience);
                                this.player.setLevel(level);
                            },
                            1L);
        } else {
            this.player.setExp(this.savedExp);
            this.player.setLevel(this.savedLevel);
        }
    }

    /** Captures the current editor inventory layout. */
    public void saveEditInventory() {
        this.ensureEditModeTools();
        this.savedEditInventory = copyItems(this.player.getInventory().getContents());
        this.savedEditArmor = copyItems(this.player.getInventory().getArmorContents());
    }

    /**
     * Restores the editor inventory and re-sends the status line that explains the current tool
     * bindings.
     */
    public void loadEditInventory() {
        DungeonInstance editor = this.instance;
        long revision = this.hotbarRevision;
        Player target = this.player;
        Runnable task =
                () -> {
                    if (revision != this.hotbarRevision || target != this.player) return;
                    if (editor != null && (!this.editMode || this.instance != editor)) {
                        return;
                    }
                    if (this.savedEditInventory != null) {
                        this.player.getInventory().setContents(copyItems(this.savedEditInventory));
                    }

                    if (this.savedEditArmor != null) {
                        this.player.getInventory().setArmorContents(copyItems(this.savedEditArmor));
                    }

                    this.ensureEditModeTools();
                    LangUtils.sendMessage(
                            this.player,
                            "editor.session.status-line",
                            LangUtils.placeholder(
                                    "function_slot",
                                    String.valueOf(
                                            PluginConfigView.getFunctionToolDisplaySlot(
                                                    this.plugin.getConfig()))),
                            LangUtils.placeholder(
                                    "room_slot",
                                    String.valueOf(
                                            PluginConfigView.getRoomToolDisplaySlot(
                                                    this.plugin.getConfig()))));
                    LangUtils.sendMessage(this.player, "editor.session.tip-edit-existing");
                    LangUtils.sendMessage(this.player, "editor.session.tip-config-list");
                    this.sendEditorStatusActionBar();
                };

        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(this.plugin, task, 2L);
        } else {
            task.run();
        }
    }

    /**
     * Ensures the editor tool items exist whenever the player is in edit mode and no menu has
     * temporarily replaced the hotbar.
     */
    public void ensureEditModeTools() {
        if (!this.editMode || this.currentHotbar != null) {
            return;
        }

        int functionSlot = PluginConfigView.getFunctionToolSlot(this.plugin.getConfig());
        int roomSlot = PluginConfigView.getRoomToolSlot(this.plugin.getConfig());
        PlayerInventory inv = this.player.getInventory();
        inv.setItem(functionSlot, ItemUtils.getFunctionTool());
        inv.setItem(roomSlot, ItemUtils.getRoomTool());
    }

    /** Restores editor tools after a menu or other temporary hotbar override. */
    public void restoreEditorTools() {
        if (!this.editMode) {
            return;
        }

        if (this.currentHotbar != null) {
            this.restoreCapturedHotbar();
        }

        this.ensureEditModeTools();
        this.player.updateInventory();
        this.sendEditorStatusActionBar();
    }

    /** Persists the complete pre-entry state before membership or entry commands can change it. */
    public boolean prepareRecovery() {
        this.requireActiveSession();
        try {
            this.recoveryJournal.begin(this.player);
            this.savedInventory = null;
            this.savedArmor = null;
            this.saveInventory();
            return true;
        } catch (RuntimeException failure) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Cannot prepare player recovery for '{}'; entry rejected.",
                            this.player.getUniqueId(),
                            failure);
            return false;
        }
    }

    /** Replays unfinished operations after reconnect or plugin enable. */
    public boolean recoverPendingState() {
        this.requireActiveSession();
        if (this.instance != null || this.observing != null) return true;
        if (this.player.isDead()) return false;
        try {
            boolean restoring = this.recoveryJournal.hasRecovery();
            this.hotbarRevision++;
            this.recoveryJournal.recover(this.player);
            this.reservedAccessKey = null;
            this.reservedAccessKeyDungeon = null;
            if (restoring) this.clearRecoveredState();
            this.savedPosition = null;
            this.flushPendingAccessKeyRefund();
            return true;
        } catch (RuntimeException failure) {
            this.blockUnsafePlayerState(failure);
            return false;
        }
    }

    /** Commits configured normal-exit state before acknowledging the recovery record. */
    public void completeDeparture(boolean restoreItems, boolean restoreExperience) {
        this.hotbarRevision++;
        if (!this.player.isOnline() || this.player.isDead()) return;
        try {
            if (this.savedPosition != null
                    && !this.player.teleport(
                            this.savedPosition, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                throw new IllegalStateException("Return teleport rejected");
            }
            if (restoreItems) this.restoreInventory();
            if (restoreExperience) {
                this.player.setTotalExperience(this.savedTotalExp);
                this.player.setLevel(this.savedLevel);
                this.player.setExp(this.savedExp);
            }
            this.recoveryJournal.acknowledgeDeparture(this.player);
            this.clearRecoveredState();
            this.flushPendingAccessKeyRefund();
        } catch (RuntimeException failure) {
            this.blockUnsafePlayerState(failure);
        }
    }

    /** Discards temporary controls only after durable recovery or normal departure completes. */
    private void clearRecoveredState() {
        this.hotbarRevision++;
        this.editMode = false;
        this.currentHotbar = null;
        this.savedHotbar = null;
        this.hotbarHistory.clear();
        this.hotbarRecoveryOwned = false;
        this.savedInventory = null;
        this.savedArmor = null;
        this.savedEditInventory = null;
        this.savedEditArmor = null;
        this.savedPosition = null;
        this.dungeonRespawn = null;
        this.dead = false;
        this.chatListening = false;
        this.activeFunction = null;
        this.activeTrigger = null;
        this.targetLocation = null;
        this.copiedFunction = null;
        this.copiedConnector = null;
        this.isCopying = false;
        this.isCutting = false;
        this.clipboardType = EditorClipboardType.NONE;
        this.pos1 = null;
        this.pos2 = null;
        this.awaitingRoomName = false;
        this.activeRoom = null;
        this.activeConnector = null;
        this.activeDoor = null;
        this.confirmRoomAction = false;
        this.addingWhitelistEntry = false;
        this.editingWhitelistEntry = false;
        this.removingWhitelistEntry = false;
    }

    private void blockUnsafePlayerState(RuntimeException failure) {
        this.plugin
                .getSLF4JLogger()
                .error(
                        "Player state recovery/delivery failed for '{}'; record retained.",
                        this.player.getUniqueId(),
                        failure);
        // Do not let gameplay change an inventory whose transaction is awaiting acknowledgement.
        this.player.kick(
                LangUtils.notice(
                        "Your player data could not be safely saved. Please reconnect or contact an"
                                + " administrator."));
    }

    /** Captures the player's current hotbar so a temporary menu can be restored later. */
    public void captureHotbar() {
        this.requireActiveSession();
        if (this.instance == null) {
            if (this.recoveryJournal.hasRecovery() && this.savedInventory == null) {
                if (!this.recoverPendingState())
                    throw new IllegalStateException("Player recovery is incomplete");
            }
            if (!this.recoveryJournal.hasRecovery()) {
                if (!this.prepareRecovery())
                    throw new IllegalStateException("Cannot safely replace hotbar");
                this.hotbarRecoveryOwned = true;
            }
        }
        if (this.savedHotbar == null) this.savedHotbar = new PlayerHotbarSnapshot(this.player);
    }

    /** Restores the most recently captured hotbar snapshot and clears transient menu state. */
    public void restoreCapturedHotbar() {
        if (this.expired) return;
        this.hotbarRevision++;
        HotbarSetEvent event = new HotbarSetEvent(this.savedHotbar, this);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            if (this.savedHotbar != null) {
                this.savedHotbar.applyTo(this.player.getInventory(), true);
                this.savedHotbar = null;
                if (this.instance == null && !this.editMode && this.hotbarRecoveryOwned) {
                    try {
                        this.recoveryJournal.acknowledgeDeparture(this.player);
                        this.clearRecoveredState();
                    } catch (RuntimeException failure) {
                        this.blockUnsafePlayerState(failure);
                    }
                }
                this.player.playSound(
                        this.player.getLocation(), "minecraft:item.armor.equip_gold", 1.0F, 1.2F);
            }

            this.currentHotbar = null;
            this.hotbarHistory.clear();

            if (this.editMode) {
                this.ensureEditModeTools();
                this.sendEditorStatusActionBar();
            }
        }
    }

    /** Captures the current hotbar and then displays the provided replacement snapshot. */
    public void captureAndShowHotbar(PlayerHotbarSnapshot hotbar) {
        this.captureHotbar();
        this.showHotbar(hotbar, true);
    }

    public void showHotbar(PlayerHotbarSnapshot hotbar) {
        this.showHotbar(hotbar, false);
    }

    /** Displays a temporary hotbar snapshot and optionally restores the held slot selection. */
    public void showHotbar(final PlayerHotbarSnapshot hotbar, final boolean restoreHeldSlot) {
        if (this.savedHotbar == null) this.captureHotbar();
        long revision = this.hotbarRevision;
        Player target = this.player;
        DungeonInstance expected = this.instance;
        Bukkit.getScheduler()
                .runTaskLater(
                        this.plugin,
                        () -> {
                            if (revision != this.hotbarRevision
                                    || target != this.player
                                    || expected != this.instance) return;
                            HotbarSetEvent event = new HotbarSetEvent(hotbar, this);
                            Bukkit.getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                if (this.currentHotbar != null && hotbar != this.currentHotbar) {
                                    this.hotbarHistory.add(this.currentHotbar);
                                }

                                this.currentHotbar = hotbar;
                                hotbar.applyTo(this.player.getInventory(), restoreHeldSlot);
                                this.player.playSound(
                                        this.player.getLocation(),
                                        "minecraft:item.armor.equip_gold",
                                        1.0F,
                                        1.2F);
                                this.pos1 = null;
                                this.pos2 = null;
                                this.sendMenuActionBar();
                            }
                        },
                        1L);
    }

    public PlayerHotbarSnapshot getPreviousHotbar() {
        return this.hotbarHistory.isEmpty() ? null : this.hotbarHistory.getLast();
    }

    public void restorePreviousHotbar() {
        this.restorePreviousHotbar(false);
    }

    /**
     * Restores the previous temporary hotbar snapshot or the original captured hotbar if no earlier
     * menu remains on the stack.
     */
    public void restorePreviousHotbar(final boolean resetHand) {
        long revision = this.hotbarRevision;
        Player target = this.player;
        Bukkit.getScheduler()
                .runTaskLater(
                        this.plugin,
                        () -> {
                            if (revision != this.hotbarRevision || target != this.player) return;
                            PlayerHotbarSnapshot previousHotbar = this.getPreviousHotbar();
                            if (previousHotbar == null) {
                                this.restoreCapturedHotbar();
                            } else {
                                HotbarSetEvent event = new HotbarSetEvent(previousHotbar, this);
                                Bukkit.getPluginManager().callEvent(event);
                                if (!event.isCancelled()) {
                                    this.hotbarHistory.removeLast();
                                    this.currentHotbar = previousHotbar;
                                    previousHotbar.applyTo(this.player.getInventory(), resetHand);
                                    this.player.playSound(
                                            this.player.getLocation(),
                                            "minecraft:item.armor.equip_gold",
                                            1.0F,
                                            1.2F);
                                    this.sendMenuActionBar();
                                }
                            }
                        },
                        1L);
    }

    public void sendToCheckpoint() {
        EntityUtils.forceTeleport(this.player, this.dungeonRespawn);
    }

    public void setSavedPosition(Location loc) {
        this.requireActiveSession();
        this.savedPosition = loc;
    }

    private void updateProfile(Consumer<YamlConfiguration> change) {
        this.requireActiveSession();
        var candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(this.playerData.saveToString());
            change.accept(candidate);
            this.playerDocument.save(candidate.saveToString());
            this.playerData.loadFromString(candidate.saveToString());
            this.completedDungeons =
                    new ArrayList<>(this.playerData.getStringList("CompletedDungeons"));
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot stage player profile", failure);
        }
    }

    public void setDungeonFinished(StaticDungeon dungeon) {
        this.updateProfile(
                candidate -> {
                    var completed = new ArrayList<>(candidate.getStringList("CompletedDungeons"));
                    if (!completed.contains(dungeon.getWorldName()))
                        completed.add(dungeon.getWorldName());
                    candidate.set("CompletedDungeons", completed);
                });
    }

    public void setDungeonUnfinished(StaticDungeon dungeon) {
        this.updateProfile(
                candidate -> {
                    var completed = new ArrayList<>(candidate.getStringList("CompletedDungeons"));
                    completed.remove(dungeon.getWorldName());
                    candidate.set("CompletedDungeons", completed);
                });
    }

    public void setDungeonSavePoint(Location loc, String dungeon) {
        Location saved = loc.clone();
        saved.setWorld(null);
        this.updateProfile(candidate -> candidate.set("SavePoints." + dungeon, saved));
    }

    @Nullable
    public Location getDungeonSavePoint(String dungeon) {
        Location saved = this.playerData.getLocation("SavePoints." + dungeon);
        return saved == null ? null : saved.clone();
    }

    public void clearDungeonSavePoint(String dungeon) {
        this.updateProfile(candidate -> candidate.set("SavePoints." + dungeon, null));
    }

    public void queueReload() {
        this.requireActiveSession();
        this.reloadQueued = true;
        Bukkit.getScheduler()
                .runTaskLater(
                        this.plugin,
                        () -> {
                            if (this.reloadQueued) {
                                this.reloadQueued = false;
                            }
                        },
                        200L);
    }

    public void unqueueReload() {
        this.reloadQueued = false;
    }

    private void sendMenuActionBar() {
        if (this.player == null || !this.player.isOnline()) {
            return;
        }

        LangUtils.sendActionBar(this.player, "editor.session.actionbar-menu");
    }

    private void sendEditorStatusActionBar() {
        if (this.player == null || !this.player.isOnline() || !this.editMode) {
            return;
        }

        LangUtils.sendActionBar(
                this.player,
                "editor.session.actionbar-status",
                LangUtils.placeholder(
                        "function_slot",
                        String.valueOf(
                                PluginConfigView.getFunctionToolDisplaySlot(
                                        this.plugin.getConfig()))),
                LangUtils.placeholder(
                        "room_slot",
                        String.valueOf(
                                PluginConfigView.getRoomToolDisplaySlot(this.plugin.getConfig()))));
    }

    public Player getPlayer() {
        this.requireActiveSession();
        return this.player;
    }

    public void setPlayer(Player player) {
        this.requireActiveSession();
        if (this.player != player) this.hotbarRevision++;
        this.player = player;
        if (!this.recoveryJournal.hasPendingTransaction()) this.flushPendingAccessKeyRefund();
    }

    public DungeonInstance getInstance() {
        return this.instance;
    }

    public void setInstance(DungeonInstance instance) {
        this.requireActiveSession();
        this.instance = instance;
    }

    public boolean isAwaitingDungeon() {
        return this.awaitingDungeon;
    }

    public void setAwaitingDungeon(boolean awaitingDungeon) {
        this.awaitingDungeon = awaitingDungeon;
    }

    public boolean hasReservedAccessKey() {
        return this.reservedAccessKey != null;
    }

    public boolean hasReservedAccessKey(String dungeonName) {
        return this.reservedAccessKey != null
                && dungeonName != null
                && dungeonName.equals(this.reservedAccessKeyDungeon);
    }

    @Nullable
    public String getReservedAccessKeyDungeon() {
        return this.reservedAccessKeyDungeon;
    }

    public boolean reserveAccessKey(String dungeonName, ItemStack reservedKey) {
        this.requireActiveSession();
        if (!this.player.isOnline()
                || this.player.isDead()
                || dungeonName == null
                || dungeonName.isBlank()
                || reservedKey == null
                || reservedKey.getType().isAir()) {
            return false;
        }

        if (this.reservedAccessKey != null
                || this.recoveryJournal.hasKeyReservation()
                || this.recoveryJournal.hasPendingTransaction()) {
            return false;
        }

        this.reservedAccessKeyDungeon = dungeonName;
        this.reservedAccessKey = reservedKey.clone();
        try {
            this.recoveryJournal.reserveKey(this.player, dungeonName, this.reservedAccessKey);
            return true;
        } catch (RuntimeException failure) {
            if (!this.recoveryJournal.hasKeyReservation()) {
                this.reservedAccessKey = null;
                this.reservedAccessKeyDungeon = null;
            }
            this.blockUnsafePlayerState(failure);
            throw failure;
        }
    }

    public @Nullable ItemStack getReservedAccessKey(String dungeonName) {
        if (!this.hasReservedAccessKey(dungeonName)) {
            return null;
        }

        return this.reservedAccessKey == null ? null : this.reservedAccessKey.clone();
    }

    public boolean settleReservedAccessKey(
            String dungeonName, DungeonAccessKeyService.Settlement ledger, boolean refund) {
        this.requireActiveSession();
        if (!this.hasReservedAccessKey(dungeonName) || !ledger.valid()) return false;
        var journal = this.recoveryJournal.prepareKeySettlement(refund, ledger.replacement());
        StateDocument.commitTogether(ledger.write(), journal.write());
        ledger.publish().run();
        journal.publish().run();
        this.reservedAccessKeyDungeon = null;
        this.reservedAccessKey = null;
        this.flushPendingAccessKeyRefund();
        return true;
    }

    public boolean completeReservedAccessKey(
            String dungeonName, @Nullable ItemStack replacementKey) {
        this.requireActiveSession();
        if (!this.hasReservedAccessKey(dungeonName)) {
            return false;
        }

        boolean settled = this.recoveryJournal.settleKey(false, replacementKey);
        this.reservedAccessKeyDungeon = null;
        this.reservedAccessKey = null;
        if (!settled && replacementKey != null && !replacementKey.getType().isAir()) {
            this.queueAccessKeyRefund(replacementKey.clone());
        } else if (settled) this.flushPendingAccessKeyRefund();
        return true;
    }

    public boolean refundReservedAccessKey(String dungeonName) {
        if (!this.hasReservedAccessKey(dungeonName)) {
            return false;
        }

        return this.refundReservedAccessKey();
    }

    public boolean refundReservedAccessKey() {
        this.requireActiveSession();
        if (!this.hasReservedAccessKey()) {
            return false;
        }

        ItemStack refundedKey = this.reservedAccessKey;
        boolean settled = this.recoveryJournal.settleKey(true, null);
        this.reservedAccessKeyDungeon = null;
        this.reservedAccessKey = null;
        if (settled) this.flushPendingAccessKeyRefund();
        else this.queueAccessKeyRefund(refundedKey);
        return true;
    }

    public void flushPendingAccessKeyRefund() {
        this.requireActiveSession();
        this.persistPendingAccessKeyRefunds();
        if (this.player == null
                || !this.player.isOnline()
                || this.instance != null
                || this.observing != null
                || this.recoveryJournal.hasPendingTransaction()) return;
        try {
            this.recoveryJournal.deliverRefunds(this.player);
        } catch (RuntimeException failure) {
            this.blockUnsafePlayerState(failure);
        }
    }

    private void persistPendingAccessKeyRefunds() {
        if (this.pendingAccessKeyRefunds.isEmpty()) return;
        this.recoveryJournal.addRefunds(this.pendingAccessKeyRefunds.toArray(ItemStack[]::new));
        this.pendingAccessKeyRefunds.clear();
    }

    private void queueAccessKeyRefund(@Nullable ItemStack refundedKey) {
        if (refundedKey == null || refundedKey.getType().isAir()) return;
        this.pendingAccessKeyRefunds.add(refundedKey.clone());
        // Keep the in-memory copy if persistence fails. Expiry retries rather than dropping it.
        this.flushPendingAccessKeyRefund();
    }

    public GameMode getSavedGameMode() {
        return this.savedGameMode;
    }

    public void setSavedGameMode(GameMode savedGameMode) {
        this.savedGameMode = savedGameMode;
    }

    public Location getSavedPosition() {
        return this.savedPosition;
    }

    public void setSavedExp(float savedExp) {
        this.savedExp = savedExp;
    }

    public void setSavedLevel(int savedLevel) {
        this.savedLevel = savedLevel;
    }

    public Location getDungeonRespawn() {
        return this.dungeonRespawn == null ? null : this.dungeonRespawn.clone();
    }

    public void setDungeonRespawn(Location dungeonRespawn) {
        this.dungeonRespawn = dungeonRespawn == null ? null : dungeonRespawn.clone();
    }

    public boolean isDisconnecting() {
        return this.isDisconnecting;
    }

    public void setDisconnecting(boolean isDisconnecting) {
        this.isDisconnecting = isDisconnecting;
    }

    public boolean isDead() {
        return this.dead;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public boolean isEditMode() {
        return this.editMode;
    }

    public ItemStack[] getSavedEditInventory() {
        return this.savedEditInventory;
    }

    public PlayerHotbarSnapshot getSavedHotbar() {
        return this.savedHotbar;
    }

    public DungeonFunction getActiveFunction() {
        return this.activeFunction;
    }

    public void setActiveFunction(DungeonFunction activeFunction) {
        this.activeFunction = activeFunction;
    }

    public DungeonTrigger getActiveTrigger() {
        return this.activeTrigger;
    }

    public void setActiveTrigger(DungeonTrigger activeTrigger) {
        this.activeTrigger = activeTrigger;
    }

    public Location getTargetLocation() {
        return this.targetLocation;
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
    }

    public PlayerHotbarSnapshot getCurrentHotbar() {
        return this.currentHotbar;
    }

    public boolean isChatListening() {
        return this.chatListening;
    }

    public void setChatListening(boolean chatListening) {
        this.chatListening = chatListening;
    }

    public DungeonFunction getCopiedFunction() {
        return this.copiedFunction;
    }

    public void setCopiedFunction(DungeonFunction copiedFunction) {
        this.copiedFunction = copiedFunction;
    }

    public void beginFunctionCopy(DungeonFunction function) {
        this.copiedFunction = function;
        this.copiedConnector = null;
        this.clipboardType = EditorClipboardType.FUNCTION;
        this.isCopying = true;
        this.isCutting = false;
    }

    public void beginFunctionCut(DungeonFunction function) {
        this.copiedFunction = function;
        this.copiedConnector = null;
        this.clipboardType = EditorClipboardType.FUNCTION;
        this.isCopying = false;
        this.isCutting = true;
    }

    public boolean isFunctionCopying() {
        return this.clipboardType == EditorClipboardType.FUNCTION && this.isCopying;
    }

    public boolean isFunctionCutting() {
        return this.clipboardType == EditorClipboardType.FUNCTION && this.isCutting;
    }

    public void clearFunctionClipboard() {
        this.copiedFunction = null;
        if (this.clipboardType == EditorClipboardType.FUNCTION) {
            this.clipboardType = EditorClipboardType.NONE;
            this.isCopying = false;
            this.isCutting = false;
        }
    }

    public boolean isCopying() {
        return this.isCopying;
    }

    public void setCopying(boolean isCopying) {
        this.isCopying = isCopying;
        if (!this.isCopying && !this.isCutting) {
            this.clipboardType = EditorClipboardType.NONE;
        }
    }

    public boolean isCutting() {
        return this.isCutting;
    }

    public void setCutting(boolean isCutting) {
        this.isCutting = isCutting;
        if (!this.isCopying && !this.isCutting) {
            this.clipboardType = EditorClipboardType.NONE;
        }
    }

    public EditorClipboardType getClipboardType() {
        return this.clipboardType;
    }

    public void setClipboardType(EditorClipboardType clipboardType) {
        this.clipboardType = clipboardType == null ? EditorClipboardType.NONE : clipboardType;
    }

    public Location getPos1() {
        return this.pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return this.pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public boolean isAwaitingRoomName() {
        return this.awaitingRoomName;
    }

    public void setAwaitingRoomName(boolean awaitingRoomName) {
        this.awaitingRoomName = awaitingRoomName;
    }

    public BranchingRoomDefinition getActiveRoom() {
        return this.activeRoom;
    }

    public void setActiveRoom(BranchingRoomDefinition activeRoom) {
        this.activeRoom = activeRoom;
    }

    public Connector getActiveConnector() {
        return this.activeConnector;
    }

    public void setActiveConnector(Connector activeConnector) {
        this.activeConnector = activeConnector;
    }

    public ConnectorDoor getActiveDoor() {
        return this.activeDoor;
    }

    public void setActiveDoor(ConnectorDoor activeDoor) {
        this.activeDoor = activeDoor;
    }

    public boolean isConfirmRoomAction() {
        return this.confirmRoomAction;
    }

    public void setConfirmRoomAction(boolean confirmRoomAction) {
        this.confirmRoomAction = confirmRoomAction;
    }

    public Connector getCopiedConnector() {
        return this.copiedConnector;
    }

    public void setCopiedConnector(Connector copiedConnector) {
        this.copiedConnector = copiedConnector;
    }

    public void beginConnectorCopy(Connector connector) {
        this.copiedConnector = connector;
        this.copiedFunction = null;
        this.clipboardType = EditorClipboardType.CONNECTOR;
        this.isCopying = true;
        this.isCutting = false;
    }

    public void beginConnectorCut(Connector connector) {
        this.copiedConnector = connector;
        this.copiedFunction = null;
        this.clipboardType = EditorClipboardType.CONNECTOR;
        this.isCopying = false;
        this.isCutting = true;
    }

    public boolean isConnectorCopying() {
        return this.clipboardType == EditorClipboardType.CONNECTOR && this.isCopying;
    }

    public boolean isConnectorCutting() {
        return this.clipboardType == EditorClipboardType.CONNECTOR && this.isCutting;
    }

    public void clearConnectorClipboard() {
        this.copiedConnector = null;
        if (this.clipboardType == EditorClipboardType.CONNECTOR) {
            this.clipboardType = EditorClipboardType.NONE;
            this.isCopying = false;
            this.isCutting = false;
        }
    }

    public boolean isAddingWhitelistEntry() {
        return this.addingWhitelistEntry;
    }

    public void setAddingWhitelistEntry(boolean addingWhitelistEntry) {
        this.addingWhitelistEntry = addingWhitelistEntry;
    }

    public boolean isEditingWhitelistEntry() {
        return this.editingWhitelistEntry;
    }

    public void setEditingWhitelistEntry(boolean editingWhitelistEntry) {
        this.editingWhitelistEntry = editingWhitelistEntry;
    }

    public boolean isRemovingWhitelistEntry() {
        return this.removingWhitelistEntry;
    }

    public void setRemovingWhitelistEntry(boolean removingWhitelistEntry) {
        this.removingWhitelistEntry = removingWhitelistEntry;
    }

    public boolean isReloadQueued() {
        return this.reloadQueued;
    }
}

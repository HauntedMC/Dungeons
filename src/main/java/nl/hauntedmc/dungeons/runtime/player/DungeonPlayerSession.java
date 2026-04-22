package nl.hauntedmc.dungeons.runtime.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.event.HotbarSetEvent;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable runtime state attached to a single player.
 *
 * <p>The session tracks inventory snapshots, active instance membership, editor state, queued
 * dungeon access, and the temporary hotbar menus used by the plugin UI.</p>
 */
public final class DungeonPlayerSession {
    private final DungeonsPlugin plugin;
    private Player player;
    private DungeonInstance instance;
    private boolean awaitingDungeon;
    @Nullable private final File playerDataFile;
    private final ConfigurationFile playerData;
    private List<String> completedDungeons = new ArrayList<>();
    private GameMode savedGameMode;
    private Location savedPosition;
    private float savedExp;
    private int savedLevel;
    private Location dungeonRespawn;
    private final List<ItemStack> rewardItems;
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
    private DungeonFunction activeFunction;
    private DungeonTrigger activeTrigger;
    private Location targetLocation;
    private final List<PlayerHotbarSnapshot> hotbarHistory;
    private PlayerHotbarSnapshot currentHotbar;
    private boolean chatListening = false;
    private DungeonFunction copiedFunction;
    private boolean isCopying;
    private boolean isCutting;
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
    private ItemStack pendingAccessKeyRefund;

    /**
     * Creates a session for an online player and loads their global persisted data.
     */
    public DungeonPlayerSession(DungeonsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.hotbarHistory = new ArrayList<>();
        this.savedGameMode = Bukkit.getDefaultGameMode();
        this.rewardItems = new ArrayList<>();
        this.playerData = new ConfigurationFile(plugin);
        this.playerDataFile =
                                new File(plugin.getDataFolder(), "players/" + player.getUniqueId() + ".yml");

        try {
            File parent = this.playerDataFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                this.plugin
                        .getSLF4JLogger()
                        .error("Failed to create player data directory '{}'.", parent.getAbsolutePath());
            }

            if (!this.playerDataFile.exists()) {
                if (!this.playerDataFile.createNewFile()) {
                    this.plugin
                            .getSLF4JLogger()
                            .error(
                                    "Failed to create player data file '{}'.", this.playerDataFile.getAbsolutePath());
                }
            }

            this.playerData.load(this.playerDataFile);
            this.completedDungeons =
                    (List<String>) this.playerData.getList("CompletedDungeons", new ArrayList<>());
        } catch (IOException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to initialize player data for '{}' at '{}'.",
                    player.getName(),
                    this.playerDataFile.getAbsolutePath(),
                    exception);
        }
    }

    /**
     * Toggles edit mode and swaps the player between their normal and editor inventories.
     */
    public void setEditMode(boolean editMode) {
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
        }
    }

    /**
     * Captures the player's current survival inventory and vital stats before an instance or editor
     * flow overwrites them.
     */
    public void saveInventory() {
        this.savedInventory = this.player.getInventory().getContents();
        this.savedArmor = this.player.getInventory().getArmorContents();
        this.savedHealth = this.player.getHealth();
        this.savedFood = this.player.getFoodLevel();
    }

    /**
     * Restores the previously captured inventory snapshot.
     */
    public void restoreInventory() {
        if (this.plugin.isEnabled()) {
            this.player.getInventory().setContents(this.savedInventory);
            this.player.getInventory().setArmorContents(this.savedArmor);
            this.player.setHealth(Math.min(this.savedHealth, EntityUtils.getMaxHealth(this.player)));
            this.player.setFoodLevel(this.savedFood);
        } else {
            this.player.getInventory().setContents(this.savedInventory);
            this.player.getInventory().setArmorContents(this.savedArmor);
        }
    }

    /**
     * Restores the player's vanilla experience values, deferring one tick when the plugin is still
     * enabled so Bukkit has finished its own respawn updates first.
     */
    public void restoreExp() {
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler()
                    .runTaskLater(
                            this.plugin,
                            () -> {
                                this.player.setExp(this.savedExp);
                                this.player.setLevel(this.savedLevel);
                            },
                            1L);
        } else {
            this.player.setExp(this.savedExp);
            this.player.setLevel(this.savedLevel);
        }
    }

    /**
     * Captures the current editor inventory layout.
     */
    public void saveEditInventory() {
        this.ensureEditModeTools();
        this.savedEditInventory = this.player.getInventory().getContents();
        this.savedEditArmor = this.player.getInventory().getArmorContents();
    }

    /**
     * Restores the editor inventory and re-sends the status line that explains the current tool
     * bindings.
     */
    public void loadEditInventory() {
        Runnable task =
                () -> {
                    if (this.savedEditInventory != null) {
                        this.player.getInventory().setContents(this.savedEditInventory);
                    }

                    if (this.savedEditArmor != null) {
                        this.player.getInventory().setArmorContents(this.savedEditArmor);
                    }

                    this.ensureEditModeTools();
                    LangUtils.sendMessage(
                            this.player,
                            "editor.session.status-line",
                            LangUtils.placeholder(
                                    "function_slot",
                                    String.valueOf(
                                            PluginConfigView.getFunctionToolDisplaySlot(this.plugin.getConfig()))),
                            LangUtils.placeholder(
                                    "room_slot",
                                    String.valueOf(
                                            PluginConfigView.getRoomToolDisplaySlot(this.plugin.getConfig()))));
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

    /**
     * Restores editor tools after a menu or other temporary hotbar override.
     */
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

        public List<ItemStack> getRewardItems() {
        List<ItemStack> rewards = new ArrayList<>(this.rewardItems);
        if (this.instance != null && this.instance instanceof PlayableInstance) {
            List<ItemStack> instanceRewards =
                    ((PlayableInstance) this.instance).getRewardInventories().get(this.player.getUniqueId());
            if (instanceRewards != null && !instanceRewards.isEmpty()) {
                rewards.addAll(instanceRewards);
            }
        }

        return rewards;
    }

        public void addReward(ItemStack... items) {
        this.rewardItems.addAll(Arrays.asList(items));
    }

        public void removeReward(ItemStack item) {
        this.rewardItems.remove(item);
    }

    /**
     * Captures the player's current hotbar so a temporary menu can be restored later.
     */
    public void captureHotbar() {
        this.savedHotbar = new PlayerHotbarSnapshot(this.player);
    }

    /**
     * Restores the most recently captured hotbar snapshot and clears transient menu state.
     */
    public void restoreCapturedHotbar() {
        HotbarSetEvent event = new HotbarSetEvent(this.savedHotbar, this);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            if (this.savedHotbar != null) {
                this.savedHotbar.applyTo(this.player.getInventory(), true);
                this.savedHotbar = null;
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

    /**
     * Captures the current hotbar and then displays the provided replacement snapshot.
     */
    public void captureAndShowHotbar(PlayerHotbarSnapshot hotbar) {
        this.captureHotbar();
        this.showHotbar(hotbar, true);
    }

        public void showHotbar(PlayerHotbarSnapshot hotbar) {
        this.showHotbar(hotbar, false);
    }

    /**
     * Displays a temporary hotbar snapshot and optionally restores the held slot selection.
     */
    public void showHotbar(final PlayerHotbarSnapshot hotbar, final boolean restoreHeldSlot) {
        (new BukkitRunnable() {
                                        public void run() {
                        HotbarSetEvent event = new HotbarSetEvent(hotbar, DungeonPlayerSession.this);
                        Bukkit.getPluginManager().callEvent(event);
                        if (!event.isCancelled()) {
                            if (DungeonPlayerSession.this.currentHotbar != null
                                    && hotbar != DungeonPlayerSession.this.currentHotbar) {
                                DungeonPlayerSession.this.hotbarHistory.add(
                                        DungeonPlayerSession.this.currentHotbar);
                            }

                            DungeonPlayerSession.this.currentHotbar = hotbar;
                            hotbar.applyTo(DungeonPlayerSession.this.player.getInventory(), restoreHeldSlot);
                            DungeonPlayerSession.this.player.playSound(
                                    DungeonPlayerSession.this.player.getLocation(),
                                    "minecraft:item.armor.equip_gold",
                                    1.0F,
                                    1.2F);
                            DungeonPlayerSession.this.pos1 = null;
                            DungeonPlayerSession.this.pos2 = null;
                            DungeonPlayerSession.this.sendMenuActionBar();
                        }
                    }
                })
                .runTaskLater(this.plugin, 1L);
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
        (new BukkitRunnable() {
                                        public void run() {
                        PlayerHotbarSnapshot previousHotbar = DungeonPlayerSession.this.getPreviousHotbar();
                        if (previousHotbar == null) {
                            DungeonPlayerSession.this.restoreCapturedHotbar();
                        } else {
                            HotbarSetEvent event = new HotbarSetEvent(previousHotbar, DungeonPlayerSession.this);
                            Bukkit.getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                DungeonPlayerSession.this.hotbarHistory.removeLast();
                                DungeonPlayerSession.this.currentHotbar = previousHotbar;
                                previousHotbar.applyTo(DungeonPlayerSession.this.player.getInventory(), resetHand);
                                DungeonPlayerSession.this.player.playSound(
                                        DungeonPlayerSession.this.player.getLocation(),
                                        "minecraft:item.armor.equip_gold",
                                        1.0F,
                                        1.2F);
                                DungeonPlayerSession.this.sendMenuActionBar();
                            }
                        }
                    }
                })
                .runTaskLater(this.plugin, 1L);
    }

        public void sendToCheckpoint() {
        EntityUtils.forceTeleport(this.player, this.dungeonRespawn);
    }

        public void setSavedPosition(Location loc) {
        this.savedPosition = loc;
        this.saveExitLocation();
    }

        public void saveExitLocation() {
        if (this.instance != null) {
            this.playerData.set("DungeonReturnLocation", this.savedPosition);
            this.playerData.save(this.playerDataFile);
        }
    }

    @Nullable public Location getExitLocation() {
        return this.playerData.getLocation("DungeonReturnLocation");
    }

        public void clearExitLocation() {
        this.playerData.set("DungeonReturnLocation", null);
        this.playerData.save(this.playerDataFile);
    }

        public void setDungeonFinished(StaticDungeon dungeon) {
        if (!this.completedDungeons.contains(dungeon.getWorldName())) {
            this.completedDungeons.add(dungeon.getWorldName());
        }

        this.playerData.set("CompletedDungeons", this.completedDungeons);
        this.playerData.save(this.playerDataFile);
    }

        public void setDungeonUnfinished(StaticDungeon dungeon) {
        this.completedDungeons.remove(dungeon.getWorldName());
        this.playerData.set("CompletedDungeons", this.completedDungeons);
        this.playerData.save(this.playerDataFile);
    }

        public void setDungeonSavePoint(Location loc, String dungeon) {
        loc = loc.clone();
        loc.setWorld(null);
        this.playerData.set("SavePoints." + dungeon, loc);
        this.playerData.save(this.playerDataFile);
    }

    @Nullable public Location getDungeonSavePoint(String dungeon) {
        return this.playerData.getLocation("SavePoints." + dungeon);
    }

        public void clearDungeonSavePoint(String dungeon) {
        this.playerData.set("SavePoints." + dungeon, null);
        this.playerData.save(this.playerDataFile);
    }

        public void queueReload() {
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
                        String.valueOf(PluginConfigView.getFunctionToolDisplaySlot(this.plugin.getConfig()))),
                LangUtils.placeholder(
                        "room_slot",
                        String.valueOf(PluginConfigView.getRoomToolDisplaySlot(this.plugin.getConfig()))));
    }

        public Player getPlayer() {
        return this.player;
    }

        public void setPlayer(Player player) {
        this.player = player;
        this.flushPendingAccessKeyRefund();
    }

        public DungeonInstance getInstance() {
        return this.instance;
    }

        public void setInstance(DungeonInstance instance) {
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

    @Nullable public String getReservedAccessKeyDungeon() {
        return this.reservedAccessKeyDungeon;
    }

        public boolean reserveAccessKey(String dungeonName, ItemStack reservedKey) {
        if (reservedKey == null || reservedKey.getType().isAir()) {
            return false;
        }

        if (this.reservedAccessKey != null) {
            return false;
        }

        this.reservedAccessKeyDungeon = dungeonName;
        this.reservedAccessKey = reservedKey.clone();
        return true;
    }

        public boolean commitReservedAccessKey(String dungeonName) {
        if (!this.hasReservedAccessKey(dungeonName)) {
            return false;
        }

        this.reservedAccessKeyDungeon = null;
        this.reservedAccessKey = null;
        return true;
    }

        public boolean refundReservedAccessKey(String dungeonName) {
        if (!this.hasReservedAccessKey(dungeonName)) {
            return false;
        }

        return this.refundReservedAccessKey();
    }

        public boolean refundReservedAccessKey() {
        if (!this.hasReservedAccessKey()) {
            return false;
        }

        ItemStack refundedKey = this.reservedAccessKey;
        this.reservedAccessKeyDungeon = null;
        this.reservedAccessKey = null;
        this.queueAccessKeyRefund(refundedKey);
        return true;
    }

        public void flushPendingAccessKeyRefund() {
        if (this.pendingAccessKeyRefund == null
                || this.pendingAccessKeyRefund.getType().isAir()
                || this.player == null
                || !this.player.isOnline()) {
            return;
        }

        ItemUtils.giveOrDrop(this.player, this.pendingAccessKeyRefund);
        this.pendingAccessKeyRefund = null;
    }

        private void queueAccessKeyRefund(@Nullable ItemStack refundedKey) {
        if (refundedKey == null || refundedKey.getType().isAir()) {
            return;
        }

        if (this.player != null && this.player.isOnline()) {
            ItemUtils.giveOrDrop(this.player, refundedKey);
            return;
        }

        this.pendingAccessKeyRefund = refundedKey.clone();
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

        public boolean isCopying() {
        return this.isCopying;
    }

        public void setCopying(boolean isCopying) {
        this.isCopying = isCopying;
    }

        public boolean isCutting() {
        return this.isCutting;
    }

        public void setCutting(boolean isCutting) {
        this.isCutting = isCutting;
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

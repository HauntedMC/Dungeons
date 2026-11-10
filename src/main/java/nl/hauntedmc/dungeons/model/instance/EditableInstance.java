package nl.hauntedmc.dungeons.model.instance;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.trigger.InteractTrigger;
import nl.hauntedmc.dungeons.listener.instance.EditListener;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
import nl.hauntedmc.dungeons.util.world.WorldUtils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Editable dungeon instance used by map editors.
 *
 * <p>This instance type loads all functions in edit mode, exposes visual markers, and persists
 * edits back into the source dungeon folder.</p>
 */
public class EditableInstance extends DungeonInstance {
    private final EditorSaveCoordinator saves;

    protected BukkitRunnable autosaveTicker;
    protected BukkitRunnable functionParticles;
    protected BukkitRunnable roomPreviewTicker;

    /**
     * Creates an editable instance and optional autosave ticker.
     */
    public EditableInstance(DungeonDefinition dungeon, CountDownLatch latch) {
        super(dungeon, latch);
        this.listener = new EditListener(this);
        this.saves =
                new EditorSaveCoordinator(
                        this.plugin(),
                        dungeon.getWorldName(),
                        new EditorSaveCoordinator.Target() {
                            private World snapshotWorld;

                            @Override
                            public boolean canSave(boolean closing) {
                                return (closing || !EditableInstance.this.disposing)
                                        && EditableInstance.this.instanceWorld != null;
                            }

                            @Override
                            public boolean isSaving() {
                                return dungeon.isSaving();
                            }

                            @Override
                            public void markSaving(boolean saving) {
                                dungeon.setSaving(saving);
                            }

                            @Override
                            public void captureSnapshot() {
                                World world = EditableInstance.this.instanceWorld;
                                EditableInstance.this.captureWorldSnapshot(world);
                                this.snapshotWorld = world;
                            }

                            @Override
                            public CompletableFuture<Boolean> persistSnapshot() {
                                World world = this.snapshotWorld;
                                this.snapshotWorld = null;
                                return EditableInstance.this.commitWorldAsync(world);
                            }

                            @Override
                            public void flushRecoverySnapshot() {
                                if (EditableInstance.this.instanceWorld != null)
                                    EditableInstance.this.instanceWorld.save(true);
                            }
                        });

        long autosaveTime =
                PluginConfigView.getEditAutosaveIntervalSeconds(this.plugin().getConfig()) * 20L;
        if (autosaveTime > 0L) {
            this.autosaveTicker =
                    new BukkitRunnable() {
                        public void run() {
                            EditableInstance.this.autosave();
                        }
                    };
            this.autosaveTicker.runTaskTimer(this.plugin(), autosaveTime, autosaveTime);
        }
    }

    /**
     * Loads editable functions and optional visual marker particles.
     */
    @Override
    public void onLoadGame() {
        final StaticDungeon dungeon = (StaticDungeon) this.dungeon;
        List<DungeonFunction> functions = new ArrayList<>(dungeon.getFunctions().values());
        dungeon.getFunctions().clear();

        for (DungeonFunction function : functions) {
            function.setInstance(this);
            function.getLocation().setWorld(this.instanceWorld);
            this.trackEditorFunction(function);
            this.addFunctionLabel(function);
            dungeon.addFunction(function.getLocation(), function);
            function.initializeMenu();
            DungeonTrigger trigger = function.getTrigger();
            if (trigger != null) {
                trigger.initializeMenu();
                trigger.initializeConditionsMenu();
            }
        }

        if (!PluginConfigView.isFunctionMarkerPreviewEnabled(this.plugin().getConfig())) {
            return;
        }

        double markerRadius =
                PluginConfigView.getStaticFunctionMarkerVisibleRadiusBlocks(
                        this.plugin().getConfig());
        final double markerRadiusSquared = markerRadius * markerRadius;
        long previewInterval =
                PluginConfigView.getEditorPreviewUpdateIntervalTicks(this.plugin().getConfig());
        this.functionParticles =
                new BukkitRunnable() {
                    public void run() {
                        for (DungeonFunction dungeonFunction : dungeon.getFunctions().values()) {
                            Location loc = dungeonFunction.getLocation().clone();
                            loc.setX(loc.getX() + 0.5);
                            loc.setY(loc.getY() + 0.7);
                            loc.setZ(loc.getZ() + 0.5);
                            loc.setWorld(EditableInstance.this.instanceWorld);
                            DustOptions dustOptions =
                                    new DustOptions(
                                            ColorUtils.hexToColor(dungeonFunction.getColour()),
                                            1.0F);

                            for (DungeonPlayerSession playerSession :
                                    EditableInstance.this.players) {
                                Player player = playerSession.getPlayer();
                                // Marker particles are only sent to nearby editors to avoid
                                // unnecessary packet load in large maps.
                                if (loc.distanceSquared(player.getLocation())
                                        <= markerRadiusSquared) {
                                    player.spawnParticle(
                                            Particle.DUST, loc, 12, 0.25, 0.25, 0.25, dustOptions);
                                    player.spawnParticle(
                                            Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
                                }
                            }
                        }
                    }
                };
        this.functionParticles.runTaskTimer(this.plugin(), 0L, previewInterval);
    }

    /**
     * Stops editor tickers and releases the dungeon's active edit-session reference.
     */
    @Override
    public void onDispose() {
        if (this.autosaveTicker != null && !this.autosaveTicker.isCancelled()) {
            this.autosaveTicker.cancel();
            this.autosaveTicker = null;
        }

        if (this.functionParticles != null && !this.functionParticles.isCancelled()) {
            this.functionParticles.cancel();
        }
        this.functionParticles = null;
        if (this.roomPreviewTicker != null && !this.roomPreviewTicker.isCancelled()) {
            this.roomPreviewTicker.cancel();
        }
        this.roomPreviewTicker = null;
    }

    @Override
    protected void onDisposalComplete() {
        if (this.dungeon.getEditSession() == this) this.dungeon.setEditSession(null);
    }

    /**
     * Triggers an asynchronous save and sends action-bar status feedback to editors.
     */
    public void autosave() {
        if (this.disposing
                || this.instanceWorld == null
                || this.players.isEmpty()
                || this.dungeon.isSaving()) {
            return;
        }

        for (DungeonPlayerSession playerSession : this.players) {
            LangUtils.sendActionBar(playerSession.getPlayer(), "editor.session.autosaving");
        }

        this.saveWorldAsync()
                .whenComplete(
                        (success, throwable) -> {
                            if (!this.plugin().isEnabled()) {
                                return;
                            }

                            Bukkit.getScheduler()
                                    .runTask(
                                            this.plugin(),
                                            () -> {
                                                for (DungeonPlayerSession playerSession :
                                                        new ArrayList<>(this.players)) {
                                                    Player player = playerSession.getPlayer();
                                                    if (!player.isOnline()) {
                                                        continue;
                                                    }

                                                    if (throwable == null
                                                            && Boolean.TRUE.equals(success)) {
                                                        LangUtils.sendActionBar(player, "editor.session.autosaved");
                                                    } else {
                                                        LangUtils.sendActionBar(
                                                                player,
                                                                "editor.session.autosave-failed");
                                                    }
                                                }
                                            });
                        });
    }

    /**
     * Starts a save operation without waiting for completion.
     */
    @Override
    public void saveWorld() {
        this.saveWorldAsync();
    }

    /**
     * Starts a save operation and counts down the provided latch on completion.
     */
    @Override
    public void saveWorld(CountDownLatch latch) {
        this.saveWorldAsync(latch);
    }

    /**
     * Saves the editor world and metadata asynchronously.
     */
    public CompletableFuture<Boolean> saveWorldAsync() {
        return this.saveWorldAsync(null);
    }

    /**
     * Saves the editor world and metadata asynchronously.
     */
    public CompletableFuture<Boolean> saveWorldAsync(@Nullable CountDownLatch latch) {
        return this.saves.save(latch);
    }

    @Override
    protected CompletableFuture<Boolean> prepareDisposal() {
        return this.saves.prepareDisposal();
    }

    public EditorSaveCoordinator.State getSaveState() {
        return this.saves.state();
    }

    /** Captures only Bukkit-owned world state; filesystem work belongs to the commit phase. */
    private void captureWorldSnapshot(World world) {
        try {
            if (this.hologramManager != null) this.hologramManager.captureAndClearHolograms();
            TextUtils.runTimed(
                    "Saving of " + this.dungeon.getWorldName(),
                    () -> {
                        world.setAutoSave(false);
                        world.save(true);
                        this.beforeCommitWorldSave(world);
                    });
        } finally {
            if (this.hologramManager != null) this.hologramManager.restoreCapturedHolograms();
        }
    }

    /**
     * Hook called after the world save flush and before folder commit begins.
     */
    protected void beforeCommitWorldSave(World world) {}

    /** Captures metadata on the server thread, then commits world files on a worker. */
    protected CompletableFuture<Boolean> commitWorldAsync(World world) {
        File source = world.getWorldFolder();
        File target = this.dungeon.getFolder();
        // Both hooks capture mutable editor/world state now, before any asynchronous continuation.
        CompletableFuture<Boolean> metadata = this.saveEditorMetadataAsync();
        if (metadata.isDone() && !Boolean.TRUE.equals(metadata.join())) return metadata;
        CompletableFuture<Boolean> additional = this.onCommitWorldAsync(world);
        CompletableFuture<Boolean> files =
                ConfigurationFile.filesystemAsync(
                        this.plugin(),
                        target,
                        () -> {
                            this.saveWorldFiles(source, target);
                            return true;
                        });
        return CompletableFuture.allOf(metadata, additional, files)
                .thenApply(
                        ignored ->
                                Boolean.TRUE.equals(metadata.join())
                                        && Boolean.TRUE.equals(additional.join())
                                        && Boolean.TRUE.equals(files.join()));
    }

    /** Filesystem phase. Runs on a worker and must not access Bukkit or mutable editor state. */
    protected void saveWorldFiles(File source, File target) throws IOException {
        WorldUtils.saveTemplate(source, target);
    }

    /**
     * Persists editor-managed metadata files such as function definitions.
     */
    protected CompletableFuture<Boolean> saveEditorMetadataAsync() {
        YamlConfiguration functionsYaml = new YamlConfiguration();
        File saveFile = new File(this.dungeon.getFolder(), "functions.yml");
        functionsYaml.set("Functions", new ArrayList<>(this.dungeon.getFunctions().values()));
        return ConfigurationFile.saveAsync(
                this.plugin(), functionsYaml, saveFile);
    }

    /** Captures additional persistence on the server thread before filesystem copying begins. */
    protected CompletableFuture<Boolean> onCommitWorldAsync(World world) {
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Adds a player to edit mode and teleports them to the editor start location.
     */
    @Override
    public void addPlayer(DungeonPlayerSession playerSession) {
        if (this.disposing || this.loadFailed) return;
        if (this.players.contains(playerSession)
                || playerSession.getInstance() != null && playerSession.getInstance() != this) {
            return;
        }
        Player player = playerSession.getPlayer();
        super.addPlayer(playerSession);
        if (playerSession.getInstance() != this) return;
        playerSession.setEditMode(true);
        EntityUtils.forceTeleport(player, this.startLocation);
    }

    /** Restores editor state only after the departure has been accepted. */
    @Override
    protected void onPlayerRemovalAccepted(DungeonPlayerSession playerSession) {
        if (this.plugin().isEnabled()) {
            playerSession.restoreCapturedHotbar();
        }

        playerSession.saveEditInventory();
        playerSession.setEditMode(false);
        playerSession.setChatListening(false);
        playerSession.setActiveFunction(null);
        playerSession.setActiveTrigger(null);
        playerSession.setTargetLocation(null);
        playerSession.setCopiedFunction(null);
        playerSession.setCutting(false);
        playerSession.setCopying(false);
        playerSession.setClipboardType(DungeonPlayerSession.EditorClipboardType.NONE);
        playerSession.setPos1(null);
        playerSession.setPos2(null);
        playerSession.setAwaitingRoomName(false);
        playerSession.setActiveRoom(null);
        playerSession.setActiveConnector(null);
        playerSession.setActiveDoor(null);
        playerSession.setConfirmRoomAction(false);
        playerSession.setCopiedConnector(null);
        playerSession.setAddingWhitelistEntry(false);
        playerSession.setEditingWhitelistEntry(false);
        playerSession.setRemovingWhitelistEntry(false);
    }

    /**
     * Adds or updates the floating label for a function.
     */
    public void addFunctionLabel(DungeonFunction function) {
        if (this.hologramManager != null) {
            this.setTextDisplayLabel(function);
        }
    }

    /**
     * Re-renders the floating label for a function.
     */
    public void updateLabel(DungeonFunction function) {
        if (this.hologramManager != null) {
            this.setTextDisplayLabel(function);
        }
    }

    /**
     * Removes the floating label associated with a function.
     */
    public void removeFunctionLabelByFunction(DungeonFunction function) {
        if (this.hologramManager != null) {
            this.removeTextDisplayLabel(function);
        }
    }

    /**
     * Tracks a function in the editor-instance lookup map used by previews and tool actions.
     */
    public void trackEditorFunction(DungeonFunction function) {
        if (function == null || function.getLocation() == null || this.instanceWorld == null) {
            return;
        }

        Location trackedLocation = function.getLocation().clone();
        if (trackedLocation.getWorld() == null
                || trackedLocation.getWorld() != this.instanceWorld) {
            trackedLocation.setWorld(this.instanceWorld);
        }

        this.functions.put(trackedLocation, function);
    }

    /**
     * Removes a function from the editor-instance lookup map.
     */
    public void untrackEditorFunction(DungeonFunction function) {
        if (function == null) {
            return;
        }

        this.functions.entrySet().removeIf(entry -> entry.getValue() == function);
    }

    /**
     * Creates or updates the text display label for a function marker.
     */
    protected void setTextDisplayLabel(DungeonFunction function) {
        if (this.hologramManager != null) {
            Location fLoc = function.getLocation().clone();
            if (fLoc.getWorld() == null) {
                fLoc.setWorld(this.instanceWorld);
            }

            fLoc.setX(fLoc.getX() + 0.5);
            fLoc.setY(fLoc.getY() + 1.2);
            fLoc.setZ(fLoc.getZ() + 0.5);
            String text =
                    ColorUtils.fullColor(
                            "<" + function.getColour() + ">" + function.getDisplayName());
            if (function.getTrigger() != null) {
                text = text + "\n<#5E55AD>" + function.getTrigger().getDisplayName();
            }

            this.hologramManager.updateHologram(fLoc, 10.0F, text, true);
        }
    }

    /**
     * Removes a function text display label from the editor world.
     */
    public void removeTextDisplayLabel(DungeonFunction function) {
        if (this.hologramManager != null) {
            Location fLoc = function.getLocation().clone();
            if (fLoc.getWorld() == null) {
                fLoc.setWorld(this.instanceWorld);
            }

            fLoc.setX(fLoc.getX() + 0.5);
            fLoc.setY(fLoc.getY() + 1.2);
            fLoc.setZ(fLoc.getZ() + 0.5);
            this.hologramManager.removeHologram(fLoc);
        }
    }

    /**
     * Removes stale sign blocks for functions that no longer use interaction triggers.
     */
    public int cleanSigns() {
        int count = 0;

        for (DungeonFunction function : this.functions.values()) {
            if (!(function.getTrigger() instanceof InteractTrigger)) {
                Location functionLocation = function.getLocation();
                if (functionLocation == null) {
                    continue;
                }

                Location blockLocation = functionLocation.clone();
                if (blockLocation.getWorld() == null
                        || blockLocation.getWorld() != this.instanceWorld) {
                    blockLocation.setWorld(this.instanceWorld);
                }

                Block sign = this.instanceWorld.getBlockAt(blockLocation);
                BlockState state = sign.getState();
                if (state instanceof Sign) {
                    sign.setType(Material.AIR);
                    count++;
                }
            }
        }

        return count;
    }
}

package nl.hauntedmc.dungeons.model.instance;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

/**
 * Editable dungeon instance used by map editors.
 *
 * <p>This instance type loads all functions in edit mode, exposes visual markers, and persists
 * edits back into the source dungeon folder.</p>
 */
public class EditableInstance extends DungeonInstance {
    protected BukkitRunnable autosaveTicker;
    protected BukkitRunnable functionParticles;
    protected BukkitRunnable roomPreviewTicker;

    /**
     * Creates an editable instance and optional autosave ticker.
     */
    public EditableInstance(DungeonDefinition dungeon, CountDownLatch latch) {
        super(dungeon, latch);
        this.listener = new EditListener(this);
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
                PluginConfigView.getStaticFunctionMarkerVisibleRadiusBlocks(this.plugin().getConfig());
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
                                                                        new DustOptions(ColorUtils.hexToColor(dungeonFunction.getColour()), 1.0F);

                            for (DungeonPlayerSession playerSession : EditableInstance.this.players) {
                                Player player = playerSession.getPlayer();
                                // Marker particles are only sent to nearby editors to avoid
                                // unnecessary packet load in large maps.
                                if (loc.distanceSquared(player.getLocation()) <= markerRadiusSquared) {
                                    player.spawnParticle(Particle.DUST, loc, 12, 0.25, 0.25, 0.25, dustOptions);
                                    player.spawnParticle(Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
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
        this.dungeon.setEditSession(null);
    }

    /**
     * Triggers an asynchronous save and sends action-bar status feedback to editors.
     */
    public void autosave() {
        for (DungeonPlayerSession playerSession : this.players) {
            playerSession
                    .getPlayer()
                    .sendActionBar(
                            ComponentUtils.component(LangUtils.getMessage("editor.session.autosaving")));
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
                                                for (DungeonPlayerSession playerSession : new ArrayList<>(this.players)) {
                                                    Player player = playerSession.getPlayer();
                                                    if (!player.isOnline()) {
                                                        continue;
                                                    }

                                                    if (throwable == null && Boolean.TRUE.equals(success)) {
                                                        player.sendActionBar(
                                                                ComponentUtils.component(
                                                                        LangUtils.getMessage("editor.session.autosaved")));
                                                    } else {
                                                        LangUtils.sendActionBar(player, "editor.session.autosave-failed");
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
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable saveTask =
                () -> {
                    if (this.dungeon.isSaving()) {
                        if (latch != null) {
                            latch.countDown();
                        }
                        result.complete(false);
                        return;
                    }

                    this.dungeon.setSaving(true);

                    try {
                        if (this.hologramManager != null) {
                            this.hologramManager.captureAndClearHolograms();
                        }

                        TextUtils.runTimed(
                                "Saving of " + this.dungeon.getWorldName(),
                                () -> {
                                    // Paper writes chunk data asynchronously by default; wait for the writer so the
                                    // folder copy is consistent.
                                    this.instanceWorld.save(true);
                                    this.beforeCommitWorldSave();
                                });
                    } catch (Throwable throwable) {
                        if (this.hologramManager != null) {
                            this.hologramManager.restoreCapturedHolograms();
                        }

                        this.finishSave(latch, result, false);
                        this.logger()
                                .error(
                                        "Failed to save edited world '{}' for dungeon '{}'.",
                                        this.instanceWorld.getName(),
                                        this.dungeon.getWorldName(),
                                        throwable);
                        return;
                    }

                    if (this.hologramManager != null) {
                        this.hologramManager.restoreCapturedHolograms();
                    }

                    this.commitWorldAsync()
                            .whenComplete(
                                    (success, throwable) -> {
                                        if (throwable != null) {
                                            this.logger()
                                                    .error(
                                                            "Failed to finish save for dungeon '{}'.",
                                                            this.dungeon.getWorldName(),
                                                            throwable);
                                            this.finishSave(latch, result, false);
                                            return;
                                        }

                                        this.finishSave(latch, result, Boolean.TRUE.equals(success));
                                    });
                };

        if (this.plugin().isEnabled() && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(this.plugin(), saveTask);
        } else {
            saveTask.run();
        }

        return result;
    }

    /**
     * Hook called after the world save flush and before folder commit begins.
     */
    protected void beforeCommitWorldSave() {}

    /**
     * Commits the live world folder back into the source dungeon directory.
     */
    protected CompletableFuture<Boolean> commitWorldAsync() {
        try {
            FileFilter filter = file -> !file.getName().equals("rooms");
            FileUtils.copyDirectory(
                    this.instanceWorld.getWorldFolder(), this.dungeon.getFolder(), filter);
                        new File(this.dungeon.getFolder(), "uid.dat").delete();
        } catch (IOException exception) {
            this.logger()
                    .error(
                            "Failed to commit edited world '{}' back to dungeon folder '{}'.",
                            this.instanceWorld.getName(),
                            this.dungeon.getFolder().getAbsolutePath(),
                            exception);
            return CompletableFuture.completedFuture(false);
        }

        return this.saveEditorMetadataAsync()
                .thenCompose(
                        metadataSaved -> {
                            if (!Boolean.TRUE.equals(metadataSaved)) {
                                return CompletableFuture.completedFuture(false);
                            }

                            return this.onCommitWorldAsync();
                        });
    }

    /**
     * Persists editor-managed metadata files such as function definitions.
     */
    protected CompletableFuture<Boolean> saveEditorMetadataAsync() {
        YamlConfiguration functionsYaml = new YamlConfiguration();
        File saveFile = new File(this.dungeon.getFolder(), "functions.yml");
        List<DungeonFunction> functions = new ArrayList<>(this.dungeon.getFunctions().values());
        functionsYaml.set("Functions", functions);

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable saveTask =
                () -> {
                    try {
                        functionsYaml.save(saveFile);
                        result.complete(true);
                    } catch (IOException exception) {
                        this.logger()
                                .error(
                                        "Failed to save functions for dungeon '{}' to '{}'.",
                                        this.dungeon.getWorldName(),
                                        saveFile.getAbsolutePath(),
                                        exception);
                        result.complete(false);
                    }
                };

        if (this.plugin().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin(), saveTask);
        } else {
            saveTask.run();
        }

        return result;
    }

    /**
     * Extension hook for subclasses that need post-commit persistence.
     */
    protected CompletableFuture<Boolean> onCommitWorldAsync() {
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Finalizes save-state bookkeeping and completes waiting callbacks.
     */
    private void finishSave(
            @Nullable CountDownLatch latch, CompletableFuture<Boolean> result, boolean success) {
        this.dungeon.setSaving(false);
        if (latch != null) {
            latch.countDown();
        }
        result.complete(success);
    }

    /**
     * Adds a player to edit mode and teleports them to the editor start location.
     */
    @Override
    public void addPlayer(DungeonPlayerSession playerSession) {
        Player player = playerSession.getPlayer();
        playerSession.saveInventory();
        super.addPlayer(playerSession);
        playerSession.setEditMode(true);
        EntityUtils.forceTeleport(player, this.startLocation);
    }

    /**
     * Removes a player from edit mode and clears editor-only session state.
     */
    @Override
    public void removePlayer(DungeonPlayerSession playerSession, boolean force) {
        Player player = playerSession.getPlayer();
        if (!this.players.contains(playerSession)) {
            if (playerSession.getInstance() == this
                    && player.isOnline()
                    && playerSession.getSavedPosition() != null) {
                playerSession.setInstance(null);
                EntityUtils.forceTeleport(player, playerSession.getSavedPosition());
                playerSession.setSavedPosition(null);
            }
        } else {
            if (this.plugin().isEnabled()) {
                playerSession.restoreCapturedHotbar();
            }

            playerSession.saveEditInventory();
            playerSession.setEditMode(false);
            playerSession.setChatListening(false);
            playerSession.setActiveFunction(null);
            playerSession.setActiveTrigger(null);
            playerSession.setCopiedFunction(null);
            playerSession.setCutting(false);
            playerSession.setCopying(false);
            playerSession.setPos1(null);
            playerSession.setPos2(null);
            playerSession.setAwaitingRoomName(false);
            playerSession.setActiveRoom(null);
            playerSession.setActiveConnector(null);
            playerSession.setActiveDoor(null);
            playerSession.setConfirmRoomAction(false);
            playerSession.setCopiedConnector(null);
            super.removePlayer(playerSession, force);
        }
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
                    ColorUtils.fullColor("<" + function.getColour() + ">" + function.getDisplayName());
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
                Block sign = this.instanceWorld.getBlockAt(function.getLocation());
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

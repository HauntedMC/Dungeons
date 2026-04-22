package nl.hauntedmc.dungeons.content.instance.edit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.entity.ParticleUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Branching editor instance that visualizes rooms, connectors, and room-scoped functions.
 *
 * <p>Unlike the generic editor instance, this type has to manage room schematics, room labels, and
 * preview particles that are specific to branching dungeon authoring.
 */
public class BranchingEditableInstance extends EditableInstance {
    private final BranchingDungeon dungeon;

    /**
     * Creates a new BranchingEditableInstance instance.
     */
    public BranchingEditableInstance(BranchingDungeon dungeon, CountDownLatch latch) {
        super(dungeon, latch);
        this.dungeon = dungeon;
    }

    /**
     * Performs on load game.
     */
    @Override
    public void onLoadGame() {
        File roomFolder = new File(this.instanceWorld.getWorldFolder(), "rooms");
        try {
            FileUtils.deleteDirectory(roomFolder);
        } catch (IOException exception) {
            RuntimeContext.logger()
                    .error("Failed to delete room folder '{}'.", roomFolder.getAbsolutePath(), exception);
        }
        this.loadFunctions();
        long previewInterval =
                PluginConfigView.getEditorPreviewUpdateIntervalTicks(this.plugin().getConfig());
        if (PluginConfigView.isFunctionMarkerPreviewEnabled(this.plugin().getConfig())) {
            double markerRadius =
                    PluginConfigView.getBranchingFunctionMarkerVisibleRadiusBlocks(
                            this.plugin().getConfig());
            final double markerRadiusSquared = markerRadius * markerRadius;
            this.functionParticles =
                                        new BukkitRunnable() {
                        /**
                         * Performs run.
                         */
                        public void run() {
                            // Snapshot the player list so preview iteration stays stable even if
                            // the editor membership changes mid-tick.
                            for (Entry<Location, DungeonFunction> pair :
                                    BranchingEditableInstance.this.functions.entrySet()) {
                                DungeonFunction function = pair.getValue();
                                Location loc = pair.getKey().clone();
                                loc.setX(loc.getX() + 0.5);
                                loc.setY(loc.getY() + 0.7);
                                loc.setZ(loc.getZ() + 0.5);
                                DustOptions dustOptions =
                                                                                new DustOptions(ColorUtils.hexToColor(function.getColour()), 1.0F);

                                for (DungeonPlayerSession playerSession :
                                        new ArrayList<>(BranchingEditableInstance.this.players)) {
                                    Player player = playerSession.getPlayer();
                                    if (loc.distanceSquared(player.getLocation()) <= markerRadiusSquared) {
                                        player.spawnParticle(Particle.DUST, loc, 12, 0.25, 0.25, 0.25, dustOptions);
                                        player.spawnParticle(Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
                                    }
                                }
                            }
                        }
                    };
            this.functionParticles.runTaskTimer(RuntimeContext.plugin(), 0L, previewInterval);
        }

        final boolean showRoomBounds =
                PluginConfigView.isBranchingRoomPreviewEnabled(this.plugin().getConfig());
        final double roomPreviewRadius =
                PluginConfigView.getBranchingRoomPreviewVisibleRadiusBlocks(this.plugin().getConfig());
        this.roomPreviewTicker =
                                new BukkitRunnable() {
                    /**
                     * Performs run.
                     */
                    public void run() {
                        for (DungeonPlayerSession playerSession :
                                new ArrayList<>(BranchingEditableInstance.this.players)) {
                            Player player = playerSession.getPlayer();
                            if (playerSession.getPos1() != null && playerSession.getPos2() != null) {
                                ParticleUtils.displayBoundingBox(
                                        player,
                                        LocationUtils.captureOffsetBoundingBox(
                                                playerSession.getPos1(), playerSession.getPos2()));
                            }

                            BranchingRoomDefinition activeRoom = playerSession.getActiveRoom();
                            boolean holdingTool = ItemUtils.isRoomTool(player.getInventory().getItemInMainHand());

                            for (BranchingRoomDefinition room :
                                    BranchingEditableInstance.this.dungeon.getUniqueRooms().values()) {
                                // Only show nearby room bounds unless the player is actively
                                // editing that room, which keeps the editor readable in dense maps.
                                if (showRoomBounds && room == activeRoom) {
                                    BranchingEditableInstance.this.displayRoomParticles(player, room);
                                } else if (showRoomBounds
                                        && holdingTool
                                        && player
                                                .getBoundingBox()
                                                .overlaps(room.getBounds().clone().expand(roomPreviewRadius))) {
                                    BranchingEditableInstance.this.displayRoomParticles(player, room);
                                } else {
                                    BranchingEditableInstance.this.clearRoomDisplay(player, room);
                                }
                            }
                        }
                    }
                };
        this.roomPreviewTicker.runTaskTimer(RuntimeContext.plugin(), 0L, previewInterval);
    }

    /**
     * Performs before commit world save.
     */
    @Override
    protected void beforeCommitWorldSave() {
        for (String rule : this.instanceWorld.getGameRules()) {
            this.dungeon
                    .getRuleConfig()
                    .set("Gamerule." + rule, this.instanceWorld.getGameRuleValue(rule));
        }

        this.dungeon.getRuleConfig().set("Difficulty", this.instanceWorld.getDifficulty().name());
    }

    /**
     * Saves editor metadata async.
     */
    @Override
    protected CompletableFuture<Boolean> saveEditorMetadataAsync() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        futures.add(this.saveGamerulesAsync());

        for (BranchingRoomDefinition room : this.dungeon.getUniqueRooms().values()) {
            futures.add(room.saveFunctionsAsync());
        }

        return this.combineFutures(futures);
    }

    /**
     * Performs on commit world async.
     */
    @Override
    protected CompletableFuture<Boolean> onCommitWorldAsync() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (BranchingRoomDefinition room : this.dungeon.getUniqueRooms().values()) {
            futures.add(room.captureSchematic(this.instanceWorld, true));
        }

        return this.combineFutures(futures);
    }

    /**
     * Saves gamerules async.
     */
    private CompletableFuture<Boolean> saveGamerulesAsync() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        File saveFile = new File(this.dungeon.getFolder(), "gamerules.yml");
        Runnable task =
                () -> {
                    try {
                        this.dungeon.getRuleConfig().save(saveFile);
                        result.complete(true);
                    } catch (IOException exception) {
                        RuntimeContext.logger()
                                .error(
                                        "Failed to save gamerules for dungeon '{}'.",
                                        this.dungeon.getWorldName(),
                                        exception);
                        result.complete(false);
                    }
                };

        if (RuntimeContext.plugin().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(RuntimeContext.plugin(), task);
        } else {
            task.run();
        }

        return result;
    }

    /**
     * Performs combine futures.
     */
    private CompletableFuture<Boolean> combineFutures(List<CompletableFuture<Boolean>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<?>[] all = futures.toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(all)
                .thenApply(
                        ignored -> {
                            for (CompletableFuture<Boolean> future : futures) {
                                if (!Boolean.TRUE.equals(future.join())) {
                                    return false;
                                }
                            }

                            return true;
                        });
    }

    /**
     * Loads functions.
     */
    public void loadFunctions() {
        for (BranchingRoomDefinition room : this.dungeon.getUniqueRooms().values()) {
            for (DungeonFunction function : room.getFunctionsMapRelative().values()) {
                if (function == null) {
                    RuntimeContext.plugin()
                            .getSLF4JLogger()
                            .warn(
                                    "Skipping null editor function in room '{}' of dungeon '{}'.",
                                    room.getNamespace(),
                                    this.dungeon.getWorldName());
                    continue;
                }

                try {
                    this.addFunction(function);
                    function.setInstance(this);
                    function.initializeMenu();
                    DungeonTrigger trigger = function.getTrigger();
                    if (trigger != null) {
                        // Room functions carry their own trigger trees, so both menus must be
                        // re-bound to this temporary editor instance before use.
                        trigger.initializeMenu();
                        trigger.initializeConditionsMenu();
                        trigger.setInstance(this);
                    }
                } catch (Throwable throwable) {
                    Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
                    Location loc = function.getLocation();
                    RuntimeContext.plugin()
                            .getSLF4JLogger()
                            .warn(
                                    "Skipping broken editor function '{}' in room '{}' of dungeon '{}' at {}. Reason: {}",
                                    function.getClass().getSimpleName(),
                                    room.getNamespace(),
                                    this.dungeon.getWorldName(),
                                    loc == null
                                            ? "<no-location>"
                                            : (loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()),
                                    root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(),
                                    root);
                }
            }

            this.setRoomLabel(room);
        }
    }

    /**
     * Adds function.
     */
    public void addFunction(DungeonFunction function) {
        Location loc = function.getLocation().clone();
        loc.setWorld(this.instanceWorld);
        this.functions.put(loc, function);
        this.addFunctionLabel(function);
    }

    /**
     * Sets the room label.
     */
    public void setRoomLabel(BranchingRoomDefinition room) {
        if (this.hologramManager != null) {
            Vector vec = room.getBounds().getCenter();
            Location loc =
                                        new Location(this.instanceWorld, vec.getX(), vec.getY(), vec.getZ())
                            .add(0.5, Math.abs(room.getBounds().getHeight() / 2.0) + 1.5, 0.5);
            this.hologramManager.updateHologram(
                    loc,
                    50.0F,
                    "&b&l"
                            + room.getNamespace()
                            + "\n&bCount: "
                            + room.getOccurrencesString()
                            + " | Weight: "
                            + room.getWeight()
                            + " | Depth: "
                            + room.getDepthString(),
                    true);
            if (room.getSpawn() != null) {
                Location spawn = room.getSpawn().clone().add(0.0, 1.2, 0.0);
                spawn.setWorld(this.instanceWorld);
                this.hologramManager.updateHologram(spawn, 10.0F, "&eRoom Spawn", true);
            }
        }
    }

    /**
     * Removes room label.
     */
    public void removeRoomLabel(BranchingRoomDefinition room) {
        if (this.hologramManager != null) {
            Vector vec = room.getBounds().getCenter();
            Location loc =
                                        new Location(this.instanceWorld, vec.getX(), vec.getY(), vec.getZ())
                            .add(0.5, Math.abs(room.getBounds().getHeight() / 2.0) + 1.5, 0.5);
            this.hologramManager.removeHologram(loc);
            if (room.getSpawn() != null) {
                Location spawn = room.getSpawn().clone().add(0.0, 1.2, 0.0);
                spawn.setWorld(this.instanceWorld);
                this.hologramManager.removeHologram(spawn);
            }
        }
    }

    /**
     * Performs display room particles.
     */
    public void displayRoomParticles(Player player, BranchingRoomDefinition room) {
        BoundingBox box = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        if (!(box.getWidthX() > 48.0)
                && !(box.getHeight() > 48.0)
                && !(box.getWidthZ() > 48.0)
                && !(box.getMinY() - 1.0 < this.instanceWorld.getMinHeight())) {
            ParticleUtils.displayStructureBox(player, box);
        } else {
            ParticleUtils.displayBoundingBox(player, box);
        }

        if (room.getSpawn() != null) {
            Location spawn = room.getSpawn().clone();
            spawn.add(0.0, 0.5, 0.0);
            spawn.setWorld(this.instanceWorld);
            DustOptions dustOptions = new DustOptions(Color.YELLOW, 1.0F);
            player.spawnParticle(Particle.DUST, spawn, 12, 0.1, 1.0, 0.1, dustOptions);
        }

        for (Connector connector : room.getConnectors()) {
            Location loc = connector.getLocation().asLocation();
            BoundingBox blockBox =
                                        new BoundingBox(
                            loc.getX(),
                            loc.getY(),
                            loc.getZ(),
                            loc.getX() + 1.0,
                            loc.getY() + 1.0,
                            loc.getZ() + 1.0);
            ParticleUtils.displayBoundingBox(player, Particle.DUST, blockBox);
            loc.setX(loc.getX() + 0.5);
            loc.setY(loc.getY() + 0.7);
            loc.setZ(loc.getZ() + 0.5);
            player.spawnParticle(Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
        }

        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        ConnectorDoor door = playerSession.getActiveDoor();
        if (door != null) {
            door.displayParticles(player);
        }
    }

    /**
     * Clears room display.
     */
    public void clearRoomDisplay(Player player, BranchingRoomDefinition room) {
        BoundingBox box = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        ParticleUtils.clearStructureBox(player, box);
    }

    /**
     * Clears room display.
     */
    public void clearRoomDisplay(BranchingRoomDefinition room) {
        BoundingBox box = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        ParticleUtils.clearStructureBox(this.instanceWorld, box);
    }

    /**
     * Adds player.
     */
    @Override
    public void addPlayer(DungeonPlayerSession playerSession) {
        super.addPlayer(playerSession);
        Player player = playerSession.getPlayer();
        if (playerSession.getSavedEditInventory() == null) {
            ItemUtils.giveOrDrop(player, ItemUtils.getRoomTool());
        }
    }

    /**
     * Returns the dungeon.
     */
    public BranchingDungeon getDungeon() {
        return this.dungeon;
    }
}

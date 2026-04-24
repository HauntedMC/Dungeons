package nl.hauntedmc.dungeons.content.dungeon;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import nl.hauntedmc.dungeons.content.instance.play.OpenInstance;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static-style dungeon that keeps reusable instances warm for faster joins.
 *
 * <p>Open dungeons can publish already-loaded instances, reserve slots for teams, and keep a
 * preloaded instance ready when configured to do so.
 */
public class OpenDungeon extends StaticDungeon {
    private final List<OpenInstance> readyInstances = new CopyOnWriteArrayList<>();
    private final List<OpenInstance> loadingInstances = new CopyOnWriteArrayList<>();
    private final AtomicBoolean preloadScheduled = new AtomicBoolean(false);

    /**
     * Creates a new OpenDungeon instance.
     */
    public OpenDungeon(
            @NotNull DungeonsRuntime runtime,
            @NotNull File folder,
            @Nullable YamlConfiguration loadedConfig)
            throws DungeonLoadException {
        super(runtime, folder, loadedConfig);
        if (this.config.getBoolean("open.preload_world", false)) {
            Bukkit.getScheduler().runTaskLater(this.plugin(), () -> this.schedulePreLoadIfNeeded(0L), 1L);
        }
    }

    /**
     * Performs refresh runtime settings.
     */
    @Override
    public void refreshRuntimeSettings() {
        super.refreshRuntimeSettings();
        if (this.config.getBoolean("open.preload_world", false)) {
            this.schedulePreLoadIfNeeded(0L);
        }
    }

    /**
     * Creates play session.
     */
    @Override
    public PlayableInstance createPlaySession(
            CountDownLatch latch, @Nullable UUID startedTeamLeaderId) {
        OpenInstance instance = new OpenInstance(this, latch);
        instance.setStartedTeamLeaderId(startedTeamLeaderId);
        this.registerLoadingInstance(instance);
        instance.initialize();
        return instance;
    }

    /**
     * Performs register loading instance.
     */
    private void registerLoadingInstance(OpenInstance instance) {
        this.instances.add(instance);
        this.activeInstances().registerActiveInstance(instance);
        this.loadingInstances.add(instance);
    }

    /**
     * Performs pre load.
     */
    public void preLoad() {
        if (!this.shouldMaintainWarmInstance()
                || this.hasWarmInstance()
                || !this.canCreateAdditionalOpenInstance()) {
            return;
        }

        OpenInstance preloaded = this.createPlaySession(null).as(OpenInstance.class);
        if (preloaded == null) {
            return;
        }

        this.watchPreloadedInstance(preloaded);
    }

    /**
     * Performs schedule pre load if needed.
     */
    public void schedulePreLoadIfNeeded() {
        this.schedulePreLoadIfNeeded(20L);
    }

    /**
     * Performs schedule pre load if needed.
     */
    private void schedulePreLoadIfNeeded(long delayTicks) {
        if (!this.config.getBoolean("open.preload_world", false) || !this.plugin().isEnabled()) {
            return;
        }

        if (!this.preloadScheduled.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler()
                .runTaskLater(
                        this.plugin(),
                        () -> {
                            this.preloadScheduled.set(false);
                            this.preLoad();
                        },
                        Math.max(0L, delayTicks));
    }

    /**
     * Performs watch preloaded instance.
     */
    private void watchPreloadedInstance(OpenInstance instance) {
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        this.plugin(),
                        () -> {
                            try {
                                boolean completed = instance.awaitInitialization(30L, TimeUnit.SECONDS);
                                if (completed && instance.hasLoadFailed()) {
                                    Bukkit.getScheduler()
                                            .runTask(this.plugin(), () -> this.cancelUnusedInstance(instance));
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                this.logger()
                                        .error(
                                                "Interrupted while watching preloaded open instance '{}' for dungeon '{}'.",
                                                instance,
                                                this.getWorldName(),
                                                exception);
                            }
                        });
    }

    /**
     * Returns whether it can accept start now.
     */
    @Override
    public boolean canAcceptStartNow(
            int requestedPlayers,
            @Nullable String difficultyName,
            int currentGlobalInstances,
            int maxGlobalInstances) {
        if (!this.isEnabled()) {
            return false;
        }

        int normalizedPlayers = Math.max(1, requestedPlayers);
        if (!this.canEverFitTeamSize(normalizedPlayers)) {
            return false;
        }

        DungeonDifficulty requestedDifficulty = this.difficultyLevels.get(difficultyName);
        if (this.findReservableInstance(this.readyInstances, normalizedPlayers, requestedDifficulty)
                != null) {
            return true;
        }

        if (this.findReservableInstance(this.loadingInstances, normalizedPlayers, requestedDifficulty)
                != null) {
            return true;
        }

        boolean hasGlobalCapacity =
                maxGlobalInstances <= 0 || currentGlobalInstances < maxGlobalInstances;
        return hasGlobalCapacity && this.hasAvailableInstances();
    }

    /**
     * Returns whether it can ever fit team size.
     */
    @Override
    public boolean canEverFitTeamSize(int requestedPlayers) {
        int normalizedPlayers = Math.max(1, requestedPlayers);
        if (!super.canEverFitTeamSize(normalizedPlayers)) {
            return false;
        }

        int maxPlayers = this.config.getInt("open.max_players", 0);
        return maxPlayers <= 0 || normalizedPlayers <= maxPlayers;
    }

    /**
     * Performs prepare instance.
     */
    @Override
    public boolean prepareInstance(
            Player player,
            String difficultyName,
            int requestedPlayers,
            @Nullable UUID startedTeamLeaderId) {
        DungeonPlayerSession playerSession = this.playerSessions().get(player);
        if (playerSession == null) {
            return false;
        }

        int normalizedPlayers = Math.max(1, requestedPlayers);
        if (!this.canEverFitTeamSize(normalizedPlayers)) {
            playerSession.setAwaitingDungeon(false);
            LangUtils.sendMessage(
                    player,
                    "commands.play.team.open-too-large",
                    LangUtils.placeholder("count", String.valueOf(normalizedPlayers)),
                    LangUtils.placeholder(
                            "max", String.valueOf(this.config.getInt("open.max_players", 0))));
            return false;
        }

        LangUtils.sendMessage(
                player,
                "instance.lifecycle.loading",
                LangUtils.placeholder("dungeon", this.getDisplayName()));
        DungeonDifficulty difficulty = this.difficultyLevels.get(difficultyName);
        UUID reservationId = player.getUniqueId();

        OpenInstance readyInstance =
                this.reserveInstance(this.readyInstances, reservationId, normalizedPlayers, difficulty);
        if (readyInstance != null) {
            return this.completeReservedJoin(player, playerSession, readyInstance, reservationId, true);
        }

        OpenInstance loadingInstance =
                this.reserveInstance(this.loadingInstances, reservationId, normalizedPlayers, difficulty);
        if (loadingInstance != null) {
            this.waitForReservedInstance(player, playerSession, loadingInstance, reservationId, true);
            return true;
        }

        if (!this.canCreateAdditionalOpenInstance()) {
            playerSession.setAwaitingDungeon(false);
            LangUtils.sendMessage(player, "commands.play.instances-full");
            return false;
        }

        OpenInstance created =
                this.createReservedInstance(
                        difficulty, reservationId, normalizedPlayers, startedTeamLeaderId);
        if (created == null) {
            playerSession.setAwaitingDungeon(false);
            LangUtils.sendMessage(
                    player,
                    "instance.lifecycle.load-failed",
                    LangUtils.placeholder("dungeon", this.getDisplayName()));
            return false;
        }

        this.waitForReservedInstance(
                player, playerSession, created, reservationId, this.shouldMaintainWarmInstance());
        return true;
    }

    @Nullable private OpenInstance createReservedInstance(
            @Nullable DungeonDifficulty difficulty,
            UUID reservationId,
            int requestedPlayers,
            @Nullable UUID startedTeamLeaderId) {
        PlayableInstance raw = this.createPlaySession(null, startedTeamLeaderId);
        OpenInstance instance = raw == null ? null : raw.as(OpenInstance.class);
        if (instance == null) {
            return null;
        }

        instance.setDifficulty(difficulty);
        if (!instance.reserveTeamSlots(reservationId, requestedPlayers)) {
            this.cancelUnusedInstance(instance);
            return null;
        }

        return instance;
    }

    /**
     * Performs wait for reserved instance.
     */
    private void waitForReservedInstance(
            Player player,
            DungeonPlayerSession playerSession,
            OpenInstance instance,
            UUID reservationId,
            boolean keepWarmIfEmpty) {
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        this.plugin(),
                        () ->
                                TextUtils.runTimed(
                                        "Loading ongoing dungeon " + this.getWorldName(),
                                        () -> {
                                            try {
                                                boolean loaded = instance.awaitInitialization(5L, TimeUnit.SECONDS);
                                                Bukkit.getScheduler()
                                                        .runTask(
                                                                this.plugin(),
                                                                () -> {
                                                                    // Reservation cleanup must run on the main
                                                                    // thread because it can dispose worlds and
                                                                    // mutate player/session state.
                                                                    if (!loaded) {
                                                                        this.releaseReservation(
                                                                                instance, reservationId, keepWarmIfEmpty);
                                                                        playerSession.refundReservedAccessKey(this.getWorldName());
                                                                        playerSession.setAwaitingDungeon(false);
                                                                        LangUtils.sendMessage(
                                                                                player,
                                                                                "instance.lifecycle.load-timeout",
                                                                                LangUtils.placeholder("dungeon", this.getDisplayName()));
                                                                        return;
                                                                    }

                                                                    if (instance.hasLoadFailed() || !instance.isReady()) {
                                                                        this.releaseReservation(
                                                                                instance, reservationId, keepWarmIfEmpty);
                                                                        playerSession.refundReservedAccessKey(this.getWorldName());
                                                                        playerSession.setAwaitingDungeon(false);
                                                                        LangUtils.sendMessage(
                                                                                player,
                                                                                "instance.lifecycle.load-failed",
                                                                                LangUtils.placeholder("dungeon", this.getDisplayName()));
                                                                        return;
                                                                    }

                                                                    this.completeReservedJoin(
                                                                            player,
                                                                            playerSession,
                                                                            instance,
                                                                            reservationId,
                                                                            keepWarmIfEmpty);
                                                                });
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                                Bukkit.getScheduler()
                                                        .runTask(
                                                                this.plugin(),
                                                                () -> {
                                                                    this.releaseReservation(instance, reservationId, keepWarmIfEmpty);
                                                                    playerSession.refundReservedAccessKey(this.getWorldName());
                                                                    playerSession.setAwaitingDungeon(false);
                                                                    LangUtils.sendMessage(
                                                                            player,
                                                                            "instance.lifecycle.load-failed",
                                                                            LangUtils.placeholder("dungeon", this.getDisplayName()));
                                                                });
                                                this.logger()
                                                        .error(
                                                                "Interrupted while waiting for open instance '{}' to initialize.",
                                                                this.getWorldName(),
                                                                exception);
                                            }
                                        }));
    }

    /**
     * Performs complete reserved join.
     */
    private boolean completeReservedJoin(
            Player player,
            DungeonPlayerSession playerSession,
            OpenInstance instance,
            UUID reservationId,
            boolean keepWarmIfEmpty) {
        if (!player.isOnline()) {
            playerSession.setAwaitingDungeon(false);
            playerSession.refundReservedAccessKey(this.getWorldName());
            this.releaseReservation(instance, reservationId, keepWarmIfEmpty);
            this.logger()
                    .warn(
                            "Aborted loading open instance '{}' because player '{}' disconnected before join.",
                            this.getWorldName(),
                            player.getName());
            return false;
        }

        if (!instance.addReservedPlayer(playerSession, reservationId)) {
            playerSession.setAwaitingDungeon(false);
            playerSession.refundReservedAccessKey(this.getWorldName());
            this.releaseReservation(instance, reservationId, keepWarmIfEmpty);
            LangUtils.sendMessage(
                    player,
                    "instance.lifecycle.load-failed",
                    LangUtils.placeholder("dungeon", this.getDisplayName()));
            return false;
        }

        playerSession.commitReservedAccessKey(this.getWorldName());
        playerSession.setAwaitingDungeon(false);
        LangUtils.sendMessage(
                player,
                "instance.lifecycle.entered",
                LangUtils.placeholder("dungeon", this.getDisplayName()));
        return true;
    }

    /**
     * Performs release reservation.
     */
    private void releaseReservation(
            OpenInstance instance, UUID reservationId, boolean keepWarmIfEmpty) {
        instance.releaseReservedSlots(reservationId);
        if (instance.hasLoadFailed()) {
            this.cancelUnusedInstance(instance);
            return;
        }

        // Unused reserved instances are discarded unless this dungeon is intentionally keeping a
        // warm instance alive for future joins.
        if (!keepWarmIfEmpty
                && instance.getPlayers().isEmpty()
                && !instance.hasOutstandingReservations()) {
            this.cancelUnusedInstance(instance);
        }
    }

    /**
     * Performs cancel unused instance.
     */
    private void cancelUnusedInstance(OpenInstance instance) {
        instance.cancelReadyRegistration();
        this.removeInstance(instance);
        if (instance.getInstanceWorld() != null) {
            instance.dispose();
        } else {
            instance.cleanupPendingWorldFolder();
        }
    }

    @Nullable private OpenInstance reserveInstance(
            List<OpenInstance> pool,
            UUID reservationId,
            int requestedPlayers,
            @Nullable DungeonDifficulty difficulty) {
        for (OpenInstance instance : pool) {
            if (!this.matchesRequestedDifficulty(instance, difficulty)) {
                continue;
            }

            if (!instance.reserveTeamSlots(reservationId, requestedPlayers)) {
                continue;
            }

            this.applyRequestedDifficultyIfUnset(instance, difficulty);
            return instance;
        }

        return null;
    }

    @Nullable private OpenInstance findReservableInstance(
            List<OpenInstance> pool,
            int requestedPlayers,
            @Nullable DungeonDifficulty requestedDifficulty) {
        for (OpenInstance instance : pool) {
            if (instance.hasCapacityFor(requestedPlayers)
                    && this.matchesRequestedDifficulty(instance, requestedDifficulty)) {
                return instance;
            }
        }

        return null;
    }

    /**
     * Applies requested difficulty if unset.
     */
    private void applyRequestedDifficultyIfUnset(
            OpenInstance instance, @Nullable DungeonDifficulty difficulty) {
        if (instance.getPlayers().isEmpty() && instance.getDifficulty() == null) {
            instance.setDifficulty(difficulty);
        }
    }

    /**
     * Performs matches requested difficulty.
     */
    private boolean matchesRequestedDifficulty(
            OpenInstance instance, @Nullable DungeonDifficulty requestedDifficulty) {
        DungeonDifficulty currentDifficulty = instance.getDifficulty();
        if (currentDifficulty == null) {
            if (requestedDifficulty == null) {
                return true;
            }

            return instance.getPlayers().isEmpty() && !instance.hasOutstandingReservations();
        }

        return requestedDifficulty != null
                && currentDifficulty.getNamespace().equals(requestedDifficulty.getNamespace());
    }

    /**
     * Performs publish ready instance.
     */
    public boolean publishReadyInstance(OpenInstance instance) {
        this.loadingInstances.remove(instance);
        if (!this.isCurrentDungeonRegistration() || instance.isReadyRegistrationCancelled()) {
            return false;
        }

        if (!this.readyInstances.contains(instance)) {
            this.readyInstances.add(instance);
        }
        return true;
    }

    /**
     * Returns whether current dungeon registration.
     */
    private boolean isCurrentDungeonRegistration() {
        return this.runtime().dungeonCatalog().get(this.getWorldName()) == this;
    }

    /**
     * Performs should maintain warm instance.
     */
    private boolean shouldMaintainWarmInstance() {
        return this.config.getBoolean("open.preload_world", false)
                && this.plugin().isEnabled()
                && !this.isSaving()
                && !this.isMarkedForDelete()
                && this.isCurrentDungeonRegistration();
    }

    /**
     * Returns whether it has warm instance.
     */
    private boolean hasWarmInstance() {
        return !this.readyInstances.isEmpty() || !this.loadingInstances.isEmpty();
    }

    /**
     * Returns whether it can create additional open instance.
     */
    private boolean canCreateAdditionalOpenInstance() {
        int maxGlobalInstances = this.activeInstances().getMaxGlobalInstances();
        boolean hasGlobalCapacity =
                maxGlobalInstances <= 0
                        || this.activeInstances().getRegisteredInstanceCount() < maxGlobalInstances;
        return hasGlobalCapacity && this.hasAvailableInstances();
    }

    /**
     * Removes instance.
     */
    @Override
    public void removeInstance(DungeonInstance instance) {
        if (instance instanceof OpenInstance open) {
            this.readyInstances.remove(open);
            this.loadingInstances.remove(open);
        }
        super.removeInstance(instance);
    }

    /**
     * Performs on open instance disposed.
     */
    public void onOpenInstanceDisposed(OpenInstance instance) {
        this.readyInstances.remove(instance);
        this.loadingInstances.remove(instance);
        if (this.shouldMaintainWarmInstance()) {
            this.schedulePreLoadIfNeeded();
        }
    }

    /**
     * Returns the active instances.
     */
    public List<OpenInstance> getActiveInstances() {
        return this.readyInstances;
    }
}

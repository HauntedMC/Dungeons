package nl.hauntedmc.dungeons.content.instance.play;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.content.dungeon.OpenDungeon;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Bukkit;

/**
 * Playable instance for open dungeons.
 *
 * <p>Open instances track reserved team slots separately from active players so a world can
 * be prepared before the joining team fully arrives.
 */
public class OpenInstance extends StaticInstance {
    protected final OpenDungeon dungeon;
    private int totalPlayersSinceStart;
    private final Map<UUID, Integer> reservedSlotsByOwner = new HashMap<>();
    private boolean readyRegistrationCancelled;

    /**
     * Creates a new OpenInstance instance.
     */
    public OpenInstance(OpenDungeon dungeon, CountDownLatch latch) {
        super(dungeon, latch);
        this.dungeon = dungeon;
    }

    /**
     * Performs on instance ready.
     */
    @Override
    protected void onInstanceReady() {
        if (this.dungeon.publishReadyInstance(this)) {
            return;
        }

        Bukkit.getScheduler()
                .runTask(
                        RuntimeContext.plugin(),
                        () -> {
                            this.dungeon.removeInstance(this);
                            if (this.getInstanceWorld() != null) {
                                this.dispose();
                            } else {
                                this.cleanupPendingWorldFolder();
                            }
                        });
    }

    /**
     * Adds player.
     */
    @Override
    public synchronized void addPlayer(DungeonPlayerSession playerSession) {
        if (this.readyRegistrationCancelled || this.disposing) {
            playerSession.setAwaitingDungeon(false);
            return;
        }

        if (!this.getPlayers().contains(playerSession) && !this.hasCapacityFor(1)) {
            playerSession.setAwaitingDungeon(false);
            return;
        }

        this.attachPlayer(playerSession);
    }

    /**
     * Adds reserved player.
     */
    public synchronized boolean addReservedPlayer(
            DungeonPlayerSession playerSession, UUID reservationId) {
        if (this.readyRegistrationCancelled || this.disposing) {
            return false;
        }

        if (!this.consumeReservedSlot(reservationId)
                && !this.getPlayers().contains(playerSession)
                && !this.hasCapacityFor(1)) {
            return false;
        }

        this.attachPlayer(playerSession);
        return true;
    }

    /**
     * Performs attach player.
     */
    private void attachPlayer(DungeonPlayerSession playerSession) {
        int before = this.getPlayers().size();
        super.addPlayer(playerSession);
        if (this.getPlayers().size() > before) {
            this.totalPlayersSinceStart++;
        }
    }

    /**
     * Returns whether it has capacity for.
     */
    public synchronized boolean hasCapacityFor(int requestedPlayers) {
        int normalizedPlayers = Math.max(1, requestedPlayers);
        int maxPlayers = this.dungeon.getConfig().getInt("open.max_players", 0);
        if (maxPlayers <= 0) {
            return true;
        }

        return this.getPlayers().size() + this.getReservedSlotCount() + normalizedPlayers <= maxPlayers;
    }

    /**
     * Performs reserve team slots.
     */
    public synchronized boolean reserveTeamSlots(UUID reservationId, int requestedPlayers) {
        if (this.disposing
                || this.readyRegistrationCancelled
                || !this.hasCapacityFor(requestedPlayers)) {
            return false;
        }

        this.reservedSlotsByOwner.merge(reservationId, Math.max(1, requestedPlayers), Integer::sum);
        return true;
    }

    /**
     * Performs release reserved slots.
     */
    public synchronized void releaseReservedSlots(UUID reservationId) {
        this.reservedSlotsByOwner.remove(reservationId);
    }

    /**
     * Returns whether it has outstanding reservations.
     */
    public synchronized boolean hasOutstandingReservations() {
        return !this.reservedSlotsByOwner.isEmpty();
    }

    /**
     * Performs cancel ready registration.
     */
    public synchronized void cancelReadyRegistration() {
        this.readyRegistrationCancelled = true;
    }

    /**
     * Returns whether ready registration cancelled.
     */
    public synchronized boolean isReadyRegistrationCancelled() {
        return this.readyRegistrationCancelled;
    }

    /**
     * Performs on dispose.
     */
    @Override
    public synchronized void onDispose() {
        this.cancelReadyRegistration();
        this.reservedSlotsByOwner.clear();
        super.onDispose();
        this.dungeon.onOpenInstanceDisposed(this);
    }

    /**
     * Performs consume reserved slot.
     */
    private boolean consumeReservedSlot(UUID reservationId) {
        Integer reserved = this.reservedSlotsByOwner.get(reservationId);
        if (reserved == null || reserved <= 0) {
            return false;
        }

        if (reserved == 1) {
            this.reservedSlotsByOwner.remove(reservationId);
        } else {
            this.reservedSlotsByOwner.put(reservationId, reserved - 1);
        }
        return true;
    }

    /**
     * Returns the reserved slot count.
     */
    private int getReservedSlotCount() {
        int reservedSlots = 0;
        for (int count : this.reservedSlotsByOwner.values()) {
            reservedSlots += count;
        }
        return reservedSlots;
    }

    /**
     * Returns the dungeon.
     */
    public OpenDungeon getDungeon() {
        return this.dungeon;
    }

    /**
     * Returns the total players since start.
     */
    public int getTotalPlayersSinceStart() {
        return this.totalPlayersSinceStart;
    }

    /**
     * Sets the total players since start.
     */
    public void setTotalPlayersSinceStart(int totalPlayersSinceStart) {
        this.totalPlayersSinceStart = totalPlayersSinceStart;
    }
}

package nl.hauntedmc.dungeons.runtime.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import nl.hauntedmc.dungeons.content.instance.play.OpenInstance;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.runtime.team.TeamRequirementPolicy;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Immutable queue intent plus mutable queued-member list for one pending dungeon start.
 */
public class DungeonQueueEntry {
    private final DungeonPlayerSession queuedPlayer;
    private final DungeonDefinition dungeon;
    private final String difficulty;
    private final List<UUID> queuedPlayers;
    private final UUID teamId;
    private final UUID leaderId;

    /** Creates a solo queue entry. */
    public DungeonQueueEntry(
            DungeonPlayerSession queuedPlayer, DungeonDefinition dungeon, String difficulty) {
        this(queuedPlayer, dungeon, difficulty, null, null);
    }

    /** Creates a queue entry for solo or team starts. */
    public DungeonQueueEntry(
            DungeonPlayerSession queuedPlayer,
            DungeonDefinition dungeon,
            String difficulty,
            List<UUID> queuedPlayers,
            UUID teamId) {
        this.queuedPlayer = Objects.requireNonNull(queuedPlayer, "queuedPlayer");
        this.dungeon = Objects.requireNonNull(dungeon, "dungeon");
        this.difficulty = difficulty == null ? "" : difficulty;
        this.teamId = teamId;
        Player leader =
                Objects.requireNonNull(
                        queuedPlayer.getPlayer(), "queued player must have a live Bukkit player");
        this.leaderId = leader.getUniqueId();
        this.queuedPlayers = new ArrayList<>();
        this.queuedPlayers.add(this.leaderId);
        if (queuedPlayers != null) {
            for (UUID playerId : queuedPlayers) {
                if (playerId != null && !this.queuedPlayers.contains(playerId)) {
                    this.queuedPlayers.add(playerId);
                }
            }
        }
    }

    /** Returns the dungeon that should be started for this queue entry. */
    public DungeonDefinition getDungeon() {
        return this.dungeon;
    }

    /** Returns the requested difficulty id for this queue entry. */
    public String getDifficulty() {
        return this.difficulty;
    }

    /** Returns queued member ids in queue-order, leader first. */
    public List<UUID> getPlayers() {
        return this.queuedPlayers;
    }

    /** Returns the queue leader id. */
    public UUID getLeaderId() {
        return this.leaderId;
    }

    /** Returns whether the given id is the queue leader. */
    public boolean isLeader(UUID playerId) {
        return this.leaderId.equals(playerId);
    }

    /** Returns the team id backing this queue, or null for solo queues. */
    public UUID getTeamId() {
        return this.teamId;
    }

    /** Returns whether this queue was created from a team start request. */
    public boolean isTeamQueue() {
        return this.teamId != null;
    }

    /** Returns whether this member must provide an access key. */
    public boolean requiresAccessKey(UUID playerId) {
        return !this.isTeamQueue()
                || TeamRequirementPolicy.requiresAccessKey(
                        this.dungeon.isOnlyLeaderNeedsKey(), this.leaderId, playerId);
    }

    /** Returns whether this member should receive access cooldown checks. */
    public boolean requiresAccessCooldown(UUID playerId) {
        return !this.isTeamQueue()
                || TeamRequirementPolicy.requiresAccessCooldown(
                        this.dungeon.isOnlyLeaderNeedsCooldown(), this.leaderId, playerId);
    }

    /** Removes one non-leader player from this queued member list. */
    public void removePlayer(UUID playerId) {
        if (!this.isLeader(playerId)) {
            this.queuedPlayers.remove(playerId);
        }
    }

    /** Returns the queue owner session used to bootstrap instance startup. */
    public DungeonPlayerSession getQueuedPlayer() {
        return this.queuedPlayer;
    }

    /**
     * Schedules a short retry loop that joins team members once the leader's instance is ready.
     */
    public void scheduleTeamJoin(
            DungeonsPlugin plugin, DungeonTeamService teamManager, PlayerSessionRegistry playerManager) {
                new BukkitRunnable() {
            private int attempts;

            @Override
                        public void run() {
                UUID reservationId = DungeonQueueEntry.this.leaderId;
                if (DungeonQueueEntry.this.teamId == null) {
                    DungeonQueueEntry.this.refundReservedAccessKeys(playerManager);
                    DungeonQueueEntry.this.clearAwaitingPlayers(playerManager);
                    this.cancel();
                    return;
                }

                var team = teamManager.getTeamById(DungeonQueueEntry.this.teamId);
                if (team == null) {
                    DungeonQueueEntry.this.releaseOpenReservation(reservationId);
                    DungeonQueueEntry.this.refundReservedAccessKeys(playerManager);
                    DungeonQueueEntry.this.clearAwaitingPlayers(playerManager);
                    this.cancel();
                    return;
                }

                DungeonInstance instance = DungeonQueueEntry.this.queuedPlayer.getInstance();
                if (instance == null) {
                    this.attempts++;
                    if (this.attempts >= 200) {
                        teamManager.clearQueued(team);
                        DungeonQueueEntry.this.releaseOpenReservation(reservationId);
                        DungeonQueueEntry.this.refundReservedAccessKeys(playerManager);
                        DungeonQueueEntry.this.clearAwaitingPlayers(playerManager);
                        this.cancel();
                    }
                    return;
                }

                teamManager.markStarted(DungeonQueueEntry.this.teamId, instance);
                OpenInstance open = instance.as(OpenInstance.class);
                for (UUID playerId : new ArrayList<>(DungeonQueueEntry.this.queuedPlayers)) {
                    DungeonPlayerSession member = playerManager.get(playerId);
                    if (member != null) {
                        member.setAwaitingDungeon(false);
                    }

                    if (DungeonQueueEntry.this.isLeader(playerId)) {
                        continue;
                    }

                    if (member == null || member.getPlayer() == null || !member.getPlayer().isOnline()) {
                        continue;
                    }

                    if (member.getInstance() == instance) {
                        continue;
                    }

                    if (member.getInstance() == null) {
                        if (open != null) {
                            open.addReservedPlayer(member, reservationId);
                        } else {
                            instance.addPlayer(member);
                        }
                    }
                }

                if (open != null) {
                    open.releaseReservedSlots(reservationId);
                }

                DungeonQueueEntry.this.finalizeReservedAccessKeys(playerManager, instance);
                this.cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Releases any reserved open-instance slots for this queue's leader reservation id. */
    private void releaseOpenReservation(UUID reservationId) {
        DungeonInstance instance = this.queuedPlayer.getInstance();
        OpenInstance open = instance == null ? null : instance.as(OpenInstance.class);
        if (open != null) {
            open.releaseReservedSlots(reservationId);
        }
    }

    /** Clears awaiting-dungeon state for queued members not currently inside an instance. */
    private void clearAwaitingPlayers(PlayerSessionRegistry playerManager) {
        for (UUID playerId : new ArrayList<>(this.queuedPlayers)) {
            DungeonPlayerSession member = playerManager.get(playerId);
            if (member != null && member.getInstance() == null) {
                member.setAwaitingDungeon(false);
            }
        }
    }

    /** Refunds any access keys reserved while this queue was pending. */
    private void refundReservedAccessKeys(PlayerSessionRegistry playerManager) {
        for (UUID playerId : new ArrayList<>(this.queuedPlayers)) {
            DungeonPlayerSession member = playerManager.get(playerId);
            if (member != null && member.getInstance() == null) {
                member.refundReservedAccessKey(this.dungeon.getWorldName());
            }
        }
    }

    /** Commits or refunds reserved access keys based on final join outcome. */
    private void finalizeReservedAccessKeys(
            PlayerSessionRegistry playerManager, DungeonInstance instance) {
        for (UUID playerId : new ArrayList<>(this.queuedPlayers)) {
            DungeonPlayerSession member = playerManager.get(playerId);
            if (member == null) {
                continue;
            }

            if (!this.requiresAccessKey(playerId)) {
                member.refundReservedAccessKey(this.dungeon.getWorldName());
                continue;
            }

            if (member.getInstance() == instance) {
                member.commitReservedAccessKey(this.dungeon.getWorldName());
            } else if (member.getInstance() == null) {
                member.refundReservedAccessKey(this.dungeon.getWorldName());
            }
        }
    }
}

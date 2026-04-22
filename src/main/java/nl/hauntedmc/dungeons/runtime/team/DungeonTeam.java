package nl.hauntedmc.dungeons.runtime.team;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

/**
 * Mutable team aggregate used for grouped dungeon queueing and started-run tracking.
 */
public final class DungeonTeam {
    private final UUID teamId;
    private final UUID leaderId;
    private final Set<UUID> memberIds = new LinkedHashSet<>();
    private boolean queued;
    private boolean started;
    private String queuedDungeonName;
    private String queuedDifficulty;
    private String instanceWorldName;
    private BukkitTask expiryTask;

    /** Creates a new team with one leader/member. */
    public DungeonTeam(UUID leaderId) {
        this.teamId = UUID.randomUUID();
        this.leaderId = leaderId;
        this.memberIds.add(leaderId);
    }

    /** Returns the stable team id. */
    public UUID getTeamId() {
        return this.teamId;
    }

    /** Returns the team leader player id. */
    public UUID getLeaderId() {
        return this.leaderId;
    }

    /** Returns whether the given player id is the leader. */
    public boolean isLeader(UUID playerId) {
        return this.leaderId.equals(playerId);
    }

    /** Returns whether the given player id belongs to this team. */
    public boolean hasMember(UUID playerId) {
        return this.memberIds.contains(playerId);
    }

    /** Adds a member to the team. */
    public boolean addMember(UUID playerId) {
        return this.memberIds.add(playerId);
    }

    /** Removes a non-leader member from the team. */
    public boolean removeMember(UUID playerId) {
        if (this.leaderId.equals(playerId)) {
            return false;
        }

        return this.memberIds.remove(playerId);
    }

    /** Returns a snapshot of member ids in insertion order. */
    public List<UUID> getMemberIds() {
        return new ArrayList<>(this.memberIds);
    }

    /** Returns current team size. */
    public int size() {
        return this.memberIds.size();
    }

    /** Returns whether the team is currently queued for a dungeon start. */
    public boolean isQueued() {
        return this.queued;
    }

    /** Marks the team as queued for a dungeon and difficulty. */
    public void setQueued(String dungeonName, String difficulty) {
        this.queued = true;
        this.queuedDungeonName = dungeonName;
        this.queuedDifficulty = difficulty;
    }

    /** Clears queued-state metadata. */
    public void clearQueued() {
        this.queued = false;
        this.queuedDungeonName = null;
        this.queuedDifficulty = null;
    }

    /** Returns whether the team has already started a dungeon run. */
    public boolean isStarted() {
        return this.started;
    }

    /** Updates started-state metadata and instance linkage. */
    public void setStarted(boolean started) {
        this.started = started;
        if (started) {
            this.clearQueued();
        } else {
            this.instanceWorldName = null;
        }
    }

    /** Returns whether team membership is locked by queued/started state. */
    public boolean isLocked() {
        return this.queued || this.started;
    }

    /** Returns queued dungeon id, if queued. */
    public String getQueuedDungeonName() {
        return this.queuedDungeonName;
    }

    /** Returns queued difficulty id, if queued. */
    public String getQueuedDifficulty() {
        return this.queuedDifficulty;
    }

    /** Returns the active instance world name once started. */
    public String getInstanceWorldName() {
        return this.instanceWorldName;
    }

    /** Updates the active instance world name for started teams. */
    public void setInstanceWorldName(String instanceWorldName) {
        this.instanceWorldName = instanceWorldName;
    }

    /** Returns the expiry task used for auto-disband logic. */
    public BukkitTask getExpiryTask() {
        return this.expiryTask;
    }

    /** Sets the expiry task used for auto-disband logic. */
    public void setExpiryTask(BukkitTask expiryTask) {
        this.expiryTask = expiryTask;
    }

    /** Cancels and clears any scheduled expiry task. */
    public void cancelExpiryTask() {
        if (this.expiryTask != null) {
            this.expiryTask.cancel();
            this.expiryTask = null;
        }
    }
}

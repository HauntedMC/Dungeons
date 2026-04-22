package nl.hauntedmc.dungeons.runtime.team;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Shared policy helpers for leader-only team requirements.
 */
public final class TeamRequirementPolicy {
    /** Utility class. */
    private TeamRequirementPolicy() {}

    /** Returns whether this member must provide an access key. */
    public static boolean requiresAccessKey(
            boolean onlyLeaderNeedsKey, UUID leaderId, UUID memberId) {
        return !onlyLeaderNeedsKey || leaderId.equals(memberId);
    }

    /** Returns whether this member should be checked for access cooldown rules. */
    public static boolean requiresAccessCooldown(
            boolean onlyLeaderNeedsCooldown, UUID leaderId, UUID memberId) {
        return !onlyLeaderNeedsCooldown || leaderId.equals(memberId);
    }

    /** Returns whether cooldown should be applied for a started run participant. */
    public static boolean shouldApplyAccessCooldown(
            boolean onlyLeaderNeedsCooldown, @Nullable UUID startedTeamLeaderId, UUID playerId) {
        return !onlyLeaderNeedsCooldown
                || startedTeamLeaderId == null
                || startedTeamLeaderId.equals(playerId);
    }
}

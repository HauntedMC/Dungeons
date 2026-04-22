package nl.hauntedmc.dungeons.runtime.team;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamRequirementPolicyTest {

    @Test
    void requiresAccessKey_respectsLeaderOnlyRule() {
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        assertTrue(TeamRequirementPolicy.requiresAccessKey(false, leaderId, memberId));
        assertTrue(TeamRequirementPolicy.requiresAccessKey(true, leaderId, leaderId));
        assertFalse(TeamRequirementPolicy.requiresAccessKey(true, leaderId, memberId));
    }

    @Test
    void requiresAccessCooldown_respectsLeaderOnlyRule() {
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        assertTrue(TeamRequirementPolicy.requiresAccessCooldown(false, leaderId, memberId));
        assertTrue(TeamRequirementPolicy.requiresAccessCooldown(true, leaderId, leaderId));
        assertFalse(TeamRequirementPolicy.requiresAccessCooldown(true, leaderId, memberId));
    }

    @Test
    void shouldApplyAccessCooldown_handlesMissingLeaderAsSafeFallback() {
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();

        assertTrue(TeamRequirementPolicy.shouldApplyAccessCooldown(false, null, playerId));
        assertTrue(TeamRequirementPolicy.shouldApplyAccessCooldown(true, null, playerId));
        assertTrue(TeamRequirementPolicy.shouldApplyAccessCooldown(true, playerId, playerId));
        assertFalse(TeamRequirementPolicy.shouldApplyAccessCooldown(true, playerId, otherPlayerId));
    }
}

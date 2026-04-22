package nl.hauntedmc.dungeons.runtime.team;

import java.util.UUID;

/** Immutable team invitation with creation and expiry timestamps. */
public record TeamInvite(
        UUID teamId, UUID inviterId, UUID targetId, long createdAt, long expiresAt) {

    /** Returns whether the invite has expired relative to current wall-clock time. */
    public boolean isExpired() {
        return System.currentTimeMillis() > this.expiresAt;
    }
}

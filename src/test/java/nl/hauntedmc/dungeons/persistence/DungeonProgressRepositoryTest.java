package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import nl.hauntedmc.dungeons.persistence.DungeonProgressRepository.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class DungeonProgressRepositoryTest {
    @Test void changesRejectAmbiguousOperationsAndHaveDeterministicCooldownOrdering() {
        var access = new CooldownChange(new CooldownKey(CooldownType.ACCESS, ""), null);
        var reward = new CooldownChange(new CooldownKey(CooldownType.REWARD, "reward:chest-1"), Instant.EPOCH);
        var mutable = new ArrayList<>(List.of(reward, access));
        var first = new Changes(null, true, null, mutable);
        mutable.clear();
        assertEquals(new Changes(null, true, null, List.of(access, reward)), first,
                "Equivalent requests must generate the same retry digest regardless of input order");
        assertThrows(IllegalArgumentException.class, () -> new Changes(null, false, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Changes(true, false, null, List.of(access, access)));
        assertThrows(IllegalArgumentException.class,
                () -> new Changes(null, false, new Checkpoint("checkpoint", "a".repeat(64)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CooldownKey(CooldownType.ACCESS, "chest"));
        assertThrows(IllegalArgumentException.class, () -> new CooldownKey(CooldownType.REWARD, "Variable Case"));
        assertThrows(IllegalArgumentException.class, () -> new CooldownKey(CooldownType.REWARD, "a".repeat(97)));
    }

    @Test void checkpointLimitsUseDatabaseByteCapacityAndRequireAContentFingerprint() {
        String payload = "界".repeat(20_000);
        assertEquals(payload, new Checkpoint(payload, "0".repeat(64)).data());
        assertThrows(IllegalArgumentException.class, () -> new Checkpoint(payload + "界", "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new Checkpoint(null, "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new Checkpoint("data", "0".repeat(63)));
        assertThrows(IllegalArgumentException.class, () -> new Checkpoint("data", "A".repeat(64)));
    }

    @Test void cooldownDatesAreNormalizedBeforeRetryHashingAndStayWithinTheDatabaseRange() {
        var key = new CooldownKey(CooldownType.ACCESS, "");
        assertNull(new CooldownChange(key, null).expiresAt());
        assertEquals(Instant.parse("2026-11-09T12:00:00.123Z"),
                new CooldownChange(key, Instant.parse("2026-11-09T12:00:00.123456Z")).expiresAt());
        assertThrows(IllegalArgumentException.class, () -> new CooldownChange(key, Instant.EPOCH.minusMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new CooldownChange(key, Instant.parse("+10000-01-01T00:00:00Z")));
    }
}

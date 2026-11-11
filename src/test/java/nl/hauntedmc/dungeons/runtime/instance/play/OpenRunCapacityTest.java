package nl.hauntedmc.dungeons.runtime.instance.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import nl.hauntedmc.dungeons.content.dungeon.OpenDungeon;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class OpenRunCapacityTest {
    @Test
    void teamReservationsPreventOverbookingAndReleaseCapacityForTheNextTeam() {
        var settings = new YamlConfiguration();
        settings.set("open.max_players", 4);
        var dungeon = mock(OpenDungeon.class);
        var plugin = mock(nl.hauntedmc.dungeons.plugin.DungeonsPlugin.class);
        when(plugin.namespace()).thenReturn("dungeons");
        when(dungeon.getEnvironment())
                .thenReturn(
                        new nl.hauntedmc.dungeons.runtime.DungeonsRuntime(plugin).environment());
        when(dungeon.getConfig()).thenReturn(settings);
        var run = new CapacityRun(dungeon);
        UUID firstTeam = UUID.randomUUID();
        UUID nextTeam = UUID.randomUUID();
        assertTrue(run.reserveTeamSlots(firstTeam, 3));
        assertFalse(run.reserveTeamSlots(nextTeam, 2));
        assertFalse(run.hasCapacityFor(Integer.MAX_VALUE));
        assertTrue(run.hasCapacityFor(1));
        assertTrue(run.reserveTeamSlots(nextTeam, 1));
        assertFalse(run.hasCapacityFor(1));
        run.releaseReservedSlots(firstTeam);
        assertTrue(run.hasOutstandingReservations());
        assertEquals(0, run.cleanups);
        assertTrue(run.hasCapacityFor(3));
        run.releaseReservedSlots(nextTeam);
        assertFalse(run.hasOutstandingReservations());
        assertTrue(run.hasCapacityFor(4));
        assertEquals(1, run.cleanups);
    }

    static class CapacityRun extends OpenInstance {
        int cleanups;

        CapacityRun(OpenDungeon dungeon) {
            super(dungeon, null);
        }

        @Override
        public synchronized void scheduleEmptyCleanup() {
            cleanups++;
        }
    }
}

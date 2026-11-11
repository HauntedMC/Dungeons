package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.random.RandomGenerator;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DungeonSpawnPointsTest {
    private final Location first = new Location(null, 1.5, 70, -2.5, 90, 15);
    private final Location second = new Location(null, 101.5, 80, 2.5, 180, -15);

    @Test
    void legacyReadsWithoutMutationAndConvertsOnEditWithoutLosingFacing() throws Exception {
        var config = new YamlConfiguration();
        LocationUtils.writeLocation("locations.start", config, first);
        String original = config.saveToString();
        var points = DungeonSpawnPoints.read(config);
        assertEquals(original, config.saveToString());
        assertEquals(first, points.first());
        assertEquals("crypt", points.add("CRYPT", second));
        points.write(config);
        assertFalse(config.contains("locations.start"));
        var reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        var restored = DungeonSpawnPoints.read(reloaded);
        assertEquals(List.of("1", "crypt"), List.copyOf(restored.all().keySet()));
        assertEquals(second, restored.get("Crypt"));
        assertEquals(first, restored.first());
        assertNull(restored.first().getWorld());
    }

    @Test
    void optionalNamesAllocateLowestFreeNumberAndRemovalDoesNotRenumber() {
        var points = new DungeonSpawnPoints();
        assertEquals("1", points.add(null, first));
        assertEquals("crypt", points.add("Crypt", second));
        assertEquals("2", points.add(null, second));
        points.remove("1");
        assertEquals(List.of("crypt", "2"), List.copyOf(points.all().keySet()));
        assertEquals("1", points.add(null, first));
        points.setFirst(first);
        assertEquals(first, points.get("crypt"));
        assertEquals(second, points.get("2"));
        assertThrows(IllegalArgumentException.class, () -> points.add("CRYPT", second));
        assertThrows(IllegalArgumentException.class, () -> points.get("missing"));
        assertThrows(IllegalArgumentException.class, () -> points.update("missing", first));
        assertThrows(IllegalArgumentException.class, () -> points.remove("missing"));
        assertThrows(IllegalArgumentException.class, () -> points.add("bad.id", first));
    }

    @Test
    void selectionUsesEveryIndexAndReturnsIndependentLocations() {
        var points = new DungeonSpawnPoints();
        points.add("crypt", first);
        points.add("tower", second);
        var random = mock(RandomGenerator.class);
        when(random.nextInt(2)).thenReturn(0, 1);
        assertEquals(first, points.select(random));
        assertEquals(second, points.select(random));
        points.get("crypt").setX(999);
        points.first().setY(999);
        points.all().get("crypt").setZ(999);
        first.setPitch(80);
        assertEquals(new Location(null, 1.5, 70, -2.5, 90, 15), points.get("crypt"));
        verify(random, times(2)).nextInt(2);
    }

    @Test
    void explicitEmptyCollectionDoesNotResurrectLegacySpawn() throws Exception {
        var config = new YamlConfiguration();
        LocationUtils.writeLocation("locations.start", config, first);
        var points = DungeonSpawnPoints.read(config);
        points.remove("1");
        points.write(config);
        var reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        LocationUtils.writeLocation("locations.start", reloaded, second);
        var restored = DungeonSpawnPoints.read(reloaded);
        assertNull(restored.first());
        var random = mock(RandomGenerator.class);
        assertNull(restored.select(random));
        verifyNoInteractions(random);
    }

    @Test
    void malformedAndNonFiniteEntriesAreRejected() {
        for (String yaml :
                List.of(
                        "locations: {starts: []}",
                        "locations: {starts: {crypt: nope}}",
                        "locations: {starts: {crypt: {x: 1, y: 2}}}",
                        "locations: {starts: {crypt: {x: 1, y: 2, z: 3, yaw: bad}}}",
                        "locations: {starts: {crypt: {x: .NaN, y: 2, z: 3}}}",
                        "locations: {starts: {Crypt: {x: 1, y: 2, z: 3}, crypt: {x: 4, y: 5, z: 6}}}")) {
            var config = new YamlConfiguration();
            assertDoesNotThrow(() -> config.loadFromString(yaml));
            assertThrows(
                    IllegalArgumentException.class, () -> DungeonSpawnPoints.read(config), yaml);
        }
    }
}

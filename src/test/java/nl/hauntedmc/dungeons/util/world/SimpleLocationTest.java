package nl.hauntedmc.dungeons.util.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class SimpleLocationTest {

    @Test
    void fromVector_usesBlockCoordinatesForNegativeAxes() {
        SimpleLocation location = SimpleLocation.from(new Vector(-0.1, -63.2, -12.9));

        assertEquals(-1.0, location.getX());
        assertEquals(-64.0, location.getY());
        assertEquals(-13.0, location.getZ());
    }
}

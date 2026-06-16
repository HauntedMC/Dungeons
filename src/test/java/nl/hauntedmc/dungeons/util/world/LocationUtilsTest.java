package nl.hauntedmc.dungeons.util.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class LocationUtilsTest {

    @Test
    void captureBoundingBox_preservesSelectedBlockCorners() {
        BoundingBox box =
                LocationUtils.captureBoundingBox(
                        new Location(null, -5.0, -10.0, 3.0), new Location(null, -2.0, -7.0, 8.0));

        assertEquals(-5.0, box.getMinX());
        assertEquals(-10.0, box.getMinY());
        assertEquals(3.0, box.getMinZ());
        assertEquals(-2.0, box.getMaxX());
        assertEquals(-7.0, box.getMaxY());
        assertEquals(8.0, box.getMaxZ());
    }

    @Test
    void captureBlockSelectionPreviewBox_expandsUpperBoundsForRendering() {
        BoundingBox box =
                LocationUtils.captureBlockSelectionPreviewBox(
                        new Location(null, -5.0, -10.0, 3.0), new Location(null, -2.0, -7.0, 8.0));

        assertEquals(-5.0, box.getMinX());
        assertEquals(-10.0, box.getMinY());
        assertEquals(3.0, box.getMinZ());
        assertEquals(-1.0, box.getMaxX());
        assertEquals(-6.0, box.getMaxY());
        assertEquals(9.0, box.getMaxZ());
    }
}

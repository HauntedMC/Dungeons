package nl.hauntedmc.dungeons.util.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * In-world text-display hologram manager keyed by normalized location.
 */
public class HologramManager {
    private final Map<Location, TextDisplay> textHolograms = new HashMap<>();
    private Map<Location, TextDisplay> capturedHolograms = new HashMap<>();

    /** Creates or updates a hologram at location with text and view range. */
    public TextDisplay updateHologram(Location loc, float viewRange, String text, boolean isLabel) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }

        Location key = this.normalizeKey(loc);
        TextDisplay display = this.textHolograms.get(key);
        if (display == null || !display.isValid()) {
            if (display != null) {
                this.textHolograms.remove(key);
            }

            display = this.createHologram(loc, viewRange, text, isLabel);
        }

        if (display == null) {
            return null;
        } else {
            display.text(ComponentUtils.component(text.replace("\\n", "\n")));
            display.setViewRange(viewRange / 64.0F);
            return display;
        }
    }

    /** Creates a fresh hologram at location, replacing any existing managed hologram there. */
    public TextDisplay createHologram(Location loc, float viewRange, String text, boolean isLabel) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        } else {
            Location key = this.normalizeKey(loc);
            this.removeExistingHologram(key);

            TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class);
            display.setAlignment(TextAlignment.CENTER);
            display.setBillboard(Billboard.CENTER);
            display.text(ComponentUtils.component(text.replace("\\n", "\n")));
            display.setViewRange(viewRange / 64.0F);
            if (isLabel) {
                display
                        .getPersistentDataContainer()
                        .set(this.getDungeonHologramKey(), PersistentDataType.BOOLEAN, true);
            }

            this.textHolograms.put(key, display);
            return display;
        }
    }

    /** Clones an existing display's visible properties into a managed hologram at location. */
    public TextDisplay cloneHologramAt(Location loc, TextDisplay display) {
        if (loc == null || loc.getWorld() == null || display == null) {
            return null;
        } else {
            Location key = this.normalizeKey(loc);
            this.removeExistingHologram(key);

            TextDisplay newDisplay = loc.getWorld().spawn(loc, TextDisplay.class);
            newDisplay.setAlignment(TextAlignment.CENTER);
            newDisplay.setBillboard(Billboard.CENTER);
            newDisplay.text(display.text());
            newDisplay.setViewRange(display.getViewRange());

            PersistentDataContainer sourceData = display.getPersistentDataContainer();
            if (sourceData.has(this.getDungeonHologramKey(), PersistentDataType.BOOLEAN)) {
                newDisplay
                        .getPersistentDataContainer()
                        .set(this.getDungeonHologramKey(), PersistentDataType.BOOLEAN, true);
            }

            this.textHolograms.put(key, newDisplay);
            return newDisplay;
        }
    }

    /** Hides a managed hologram at location by setting view range to zero. */
    public void hideHologram(Location loc) {
        if (loc == null) {
            return;
        }

        TextDisplay display = this.textHolograms.get(this.normalizeKey(loc));
        if (display != null) {
            this.hideHologram(display);
        }
    }

    /** Hides one display by setting its view range to zero. */
    public void hideHologram(TextDisplay display) {
        if (display != null && display.isValid()) {
            display.setViewRange(0.0F);
        }
    }

    /** Shows a managed hologram at location using supplied view range. */
    public void showHologram(Location loc, float range) {
        if (loc == null) {
            return;
        }

        TextDisplay display = this.textHolograms.get(this.normalizeKey(loc));
        if (display != null) {
            this.showHologram(display, range);
        }
    }

    /** Shows one display using supplied view range. */
    public void showHologram(TextDisplay display, float range) {
        if (display != null && display.isValid()) {
            display.setViewRange(range / 64.0F);
        }
    }

    /** Removes a managed hologram at location. */
    public void removeHologram(Location loc) {
        if (loc == null) {
            return;
        }

        TextDisplay display = this.textHolograms.remove(this.normalizeKey(loc));
        if (display != null) {
            display.remove();
        }
    }

    /** Removes every tracked hologram display. */
    public void clearAllHolograms() {
        for (TextDisplay display : this.textHolograms.values()) {
            if (display != null) {
                display.remove();
            }
        }

        this.textHolograms.clear();
    }

    /** Captures and removes all holograms for temporary world-save operations. */
    public void captureAndClearHolograms() {
        this.capturedHolograms = new HashMap<>(this.textHolograms);
        this.clearAllHolograms();
    }

    /** Restores previously captured holograms after a save operation. */
    public void restoreCapturedHolograms() {
        if (this.capturedHolograms.isEmpty()) {
            return;
        }

        this.clearAllHolograms();

        for (Entry<Location, TextDisplay> pair : this.capturedHolograms.entrySet()) {
            Location loc = pair.getKey();
            TextDisplay display = pair.getValue();
            this.cloneHologramAt(loc, display);
        }

        this.capturedHolograms.clear();
    }

    /** Normalizes location key by clearing yaw/pitch to avoid key drift. */
    private Location normalizeKey(Location loc) {
        Location key = loc.clone();
        key.setYaw(0.0F);
        key.setPitch(0.0F);
        return key;
    }

    /** Removes any existing display at the key from manager and world. */
    private void removeExistingHologram(Location key) {
        TextDisplay existing = this.textHolograms.remove(key);
        if (existing != null) {
            existing.remove();
        }

        if (key.getWorld() == null) {
            return;
        }

        for (TextDisplay display : key.getWorld().getEntitiesByClass(TextDisplay.class)) {
            if (this.isTaggedDungeonHologram(display) && this.samePosition(display.getLocation(), key)) {
                display.remove();
            }
        }
    }

    /** Returns whether a display carries the dungeon hologram persistent tag. */
    private boolean isTaggedDungeonHologram(TextDisplay display) {
        return display
                .getPersistentDataContainer()
                .has(this.getDungeonHologramKey(), PersistentDataType.BOOLEAN);
    }

    /** Returns whether two locations represent the exact same world coordinates. */
    private boolean samePosition(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && Double.compare(a.getX(), b.getX()) == 0
                && Double.compare(a.getY(), b.getY()) == 0
                && Double.compare(a.getZ(), b.getZ()) == 0;
    }

    /** Returns the persistent key used to tag dungeon hologram displays. */
    private NamespacedKey getDungeonHologramKey() {
        return new NamespacedKey(RuntimeContext.plugin(), "dungeonhologram");
    }
}

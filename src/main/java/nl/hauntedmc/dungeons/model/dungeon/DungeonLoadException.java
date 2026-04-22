package nl.hauntedmc.dungeons.model.dungeon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;

/** Signals that a dungeon could not be loaded from disk into the runtime catalogue. */
public class DungeonLoadException extends Exception {
    private final List<String> info;
    private final boolean trace;

    /**
     * Creates a load exception with optional detail lines and stack-trace preference.
     */
    public DungeonLoadException(String message, boolean printStackTrace, String... info) {
        super(message);
        this.trace = printStackTrace;
        if (info == null) {
            this.info = new ArrayList<>();
        } else {
            this.info = new ArrayList<>(List.of(info));
        }
    }

    /**
     * Adds an extra human-readable detail line to this exception.
     */
    public void addMessage(String message) {
        this.info.add(message);
    }

    /**
     * Logs this load failure against one dungeon folder.
     */
    public void printError(File folder) {
        RuntimeContext.logger()
                .error("The dungeon '{}' was not loaded: {}", folder.getName(), this.getMessage());

        for (String line : this.info) {
            RuntimeContext.logger().error("  - {}", line);
        }

        if (this.trace) {
            RuntimeContext.logger()
                    .error("Dungeon initialization failed for '{}'.", folder.getName(), this);
        }
    }

    /**
     * Returns additional detail lines attached to this failure.
     */
    public List<String> getInfo() {
        return this.info;
    }
}

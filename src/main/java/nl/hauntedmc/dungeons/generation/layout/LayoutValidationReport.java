package nl.hauntedmc.dungeons.generation.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;

/**
 * Collects warnings and errors discovered while validating a layout configuration.
 */
public class LayoutValidationReport {
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    /**
     * Adds a warning message if it is non-empty and not already present.
     *
     * @param message warning text
     */
    public void addWarning(String message) {
        if (message != null && !message.isBlank() && !this.warnings.contains(message)) {
            this.warnings.add(message);
        }
    }

    /**
     * Adds an error message if it is non-empty and not already present.
     *
     * @param message error text
     */
    public void addError(String message) {
        if (message != null && !message.isBlank() && !this.errors.contains(message)) {
            this.errors.add(message);
        }
    }

    /**
     * Returns whether any warnings were recorded.
     *
     * @return {@code true} when warnings exist
     */
    public boolean hasWarnings() {
        return !this.warnings.isEmpty();
    }

    /**
     * Returns whether any errors were recorded.
     *
     * @return {@code true} when errors exist
     */
    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    /**
     * Returns an immutable view of recorded warnings.
     *
     * @return warning messages
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(this.warnings);
    }

    /**
     * Returns an immutable view of recorded errors.
     *
     * @return error messages
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(this.errors);
    }

    /**
     * Logs all collected warnings and errors for the given dungeon name.
     *
     * @param dungeonName dungeon being validated
     */
    public void log(String dungeonName) {
        for (String warning : this.warnings) {
            RuntimeContext.logger()
                    .warn("Generation validation warning for dungeon '{}': {}", dungeonName, warning);
        }
        for (String error : this.errors) {
            RuntimeContext.logger()
                    .error("Generation validation error for dungeon '{}': {}", dungeonName, error);
        }
    }
}

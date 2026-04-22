package nl.hauntedmc.dungeons.generation.layout;

/**
 * Categorized reasons recorded while layout generation rejects candidates.
 */
public enum GenerationFailureReason {
    INVALID_CONFIGURATION("Invalid configuration"),
    NO_VALID_CANDIDATE_ROOMS("No valid candidate rooms"),
    NO_COMPATIBLE_CONNECTOR("No compatible connector"),
    NO_VALID_ORIENTATION("No valid orientation"),
    COLLIDES_WITH_EXISTING_ROOM("Room collision"),
    DEPTH_OUT_OF_RANGE("Depth constraint mismatch"),
    ROOM_MAXED_OUT("Room occurrence cap reached"),
    TERMINAL_ROOM_REQUIRED("Terminal room required"),
    MISSING_REQUIRED_ROOM("Missing required room"),
    BRANCH_TARGET_TOO_SMALL("Branch target too small"),
    REQUIRED_ROOM_UNREACHABLE("Required room unreachable");

    private final String description;

    /**
     * Creates a new generation failure reason instance.
     */
    GenerationFailureReason(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description used in logs and diagnostics.
     */
    public String getDescription() {
        return this.description;
    }
}

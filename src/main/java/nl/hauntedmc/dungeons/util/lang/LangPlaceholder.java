package nl.hauntedmc.dungeons.util.lang;

/** Named placeholder token used for localized message substitution. */
public record LangPlaceholder(String name, Object value) {
    /** Validates placeholder key at creation time. */
    public LangPlaceholder {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Placeholder name cannot be null or blank.");
        }
    }
}

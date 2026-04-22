package nl.hauntedmc.dungeons.model.element;

/**
 * Groups function types by the gameplay scope they primarily act on.
 */
public enum FunctionCategory {
    DUNGEON("#fca103"),
    PLAYER("#0bfc03"),
    LOCATION("#fc03fc"),
    META("#38e5fc"),
    ROOM("#fc0303");

    private final String color;

    /**
     * Creates a new function category instance.
     */
    FunctionCategory(String hexColor) {
        this.color = hexColor;
    }

    /**
     * Returns the default UI color for this category as a hex string.
     */
    public String getColor() {
        return this.color;
    }
}

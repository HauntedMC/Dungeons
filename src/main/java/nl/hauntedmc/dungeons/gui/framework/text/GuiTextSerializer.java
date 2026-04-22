package nl.hauntedmc.dungeons.gui.framework.text;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;

/**
 * Internal serializer for converting menu strings into Adventure components.
 */
final class GuiTextSerializer {
    /** Utility class. */
    private GuiTextSerializer() {}

    /** Normalizes GUI text color formats and applies full color translation. */
    static String fullColor(String input) {
        return ColorUtils.fullColor(ColorUtils.convertHexBraces(input));
    }

    /** Converts one GUI text string into an Adventure component. */
    static Component component(String input) {
        return ComponentUtils.component(ColorUtils.convertHexBraces(input));
    }
}

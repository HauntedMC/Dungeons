package nl.hauntedmc.dungeons.gui.framework.text;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;

import java.util.List;

/**
 * Public text helpers used by the inventory GUI framework.
 */
public final class GuiTextUtils {
    /** Utility class. */
    private GuiTextUtils() {}

    /** Applies legacy `&` color code translation. */
    public static String colorize(String s) {
        return ColorUtils.colorize(s);
    }

    /** Applies full GUI color processing, including hex support. */
    public static String fullColor(String s) {
        return GuiTextSerializer.fullColor(s);
    }

    /** Converts one GUI string into an Adventure component. */
    public static Component component(String s) {
        return GuiTextSerializer.component(s);
    }

    /** Converts multiple GUI text lines into Adventure components. */
    public static List<Component> components(List<String> lines) {
        return lines.stream().map(GuiTextUtils::component).toList();
    }

    /** Serializes an Adventure component into plain legacy-compatible text. */
    public static String serialize(Component component) {
        return ComponentUtils.serialize(component);
    }
}

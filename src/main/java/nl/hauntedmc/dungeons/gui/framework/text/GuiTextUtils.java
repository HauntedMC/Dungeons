package nl.hauntedmc.dungeons.gui.framework.text;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;

/** Public text helpers used by the inventory GUI framework. */
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

    /** Chest titles sit on a light surface and need a darker accent than chat. */
    public static Component title(String text) {
        return Component.text("◆ ", NamedTextColor.DARK_AQUA)
                .append(inventoryTitle(component(text)).decorate(TextDecoration.BOLD));
    }

    private static Component inventoryTitle(Component text) {
        return text.color(NamedTextColor.DARK_GRAY)
                .children(text.children().stream().map(GuiTextUtils::inventoryTitle).toList());
    }

    public static Component itemName(String text) {
        return component(text)
                .colorIfAbsent(NamedTextColor.WHITE)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component itemLore(String text) {
        return component(text)
                .colorIfAbsent(NamedTextColor.GRAY)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Converts multiple GUI text lines into Adventure components. */
    public static List<Component> components(List<String> lines) {
        return lines.stream().map(GuiTextUtils::itemLore).toList();
    }

    /** Serializes an Adventure component into plain legacy-compatible text. */
    public static String serialize(Component component) {
        return ComponentUtils.serialize(component);
    }
}

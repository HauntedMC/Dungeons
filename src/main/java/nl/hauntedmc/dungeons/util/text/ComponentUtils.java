package nl.hauntedmc.dungeons.util.text;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Adventure component conversion helpers used across chat and UI rendering. */
public final class ComponentUtils {

    private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    private static final PlainTextComponentSerializer PLAIN_TEXT_COMPONENT_SERIALIZER =
            PlainTextComponentSerializer.plainText();

    /** Converts legacy-colored text into an adventure component. */
    public static Component component(String s) {
        return s == null
                ? Component.empty()
                : LEGACY_COMPONENT_SERIALIZER.deserialize(ColorUtils.fullColor(s));
    }

    /** Converts a list of strings into a list of adventure components. */
    public static List<Component> components(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        return lines.stream().map(ComponentUtils::component).toList();
    }

    /** Serializes an adventure component into legacy text. */
    public static String serialize(Component component) {
        return component == null ? "" : LEGACY_COMPONENT_SERIALIZER.serialize(component);
    }

    /** Serializes an adventure component into plain text. */
    public static String plainText(Component component) {
        return component == null ? "" : PLAIN_TEXT_COMPONENT_SERIALIZER.serialize(component);
    }
}

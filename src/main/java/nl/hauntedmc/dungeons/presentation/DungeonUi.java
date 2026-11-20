package nl.hauntedmc.dungeons.presentation;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Shared hierarchy for Minecraft chat panels, compact HUD rows and inventory text. */
public final class DungeonUi {
    private DungeonUi() {}

    public static Component heading(Component title) {
        return Component.text("▌ ", NamedTextColor.DARK_AQUA)
                .append(title.colorIfAbsent(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
    }

    public static Component detail(Component content) {
        return Component.text("│  ", NamedTextColor.DARK_GRAY)
                .append(content.colorIfAbsent(NamedTextColor.WHITE));
    }

    public static Component metric(Component label, Component value) {
        return label.colorIfAbsent(NamedTextColor.GRAY)
                .append(Component.space())
                .append(value.colorIfAbsent(NamedTextColor.WHITE));
    }

    public static Component join(Component... parts) {
        return join(List.of(parts));
    }

    public static Component join(List<Component> parts) {
        return Component.join(
                JoinConfiguration.separator(Component.text("  ·  ", NamedTextColor.DARK_GRAY)),
                parts.stream().filter(part -> !part.equals(Component.empty())).toList());
    }

    public static Component action(Component label, String command, NamedTextColor color) {
        return Component.text("› ", color)
                .append(label.color(color))
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    public static Component syntax(String command) {
        var row = Component.text();
        String[] tokens = command.split(" ");
        for (int index = 0; index < tokens.length; index++) {
            if (index > 0) row.append(Component.space());
            String token = tokens[index];
            row.append(
                    Component.text(
                            token,
                            index == 0
                                    ? NamedTextColor.GRAY
                                    : token.startsWith("<")
                                            ? NamedTextColor.GOLD
                                            : NamedTextColor.WHITE));
        }
        int argument = command.indexOf(" <");
        String suggestion = argument < 0 ? command : command.substring(0, argument + 1);
        return row.build()
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.suggestCommand(suggestion));
    }
}

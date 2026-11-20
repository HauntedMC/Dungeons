package nl.hauntedmc.dungeons.experience;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.featureframework.hud.HudFrame;
import org.bukkit.entity.Player;

/** Applies the map’s sidebar layout to live run and spectator views. */
final class DungeonSidebar {
    private final ExperiencePresentation policy;
    private final Player viewer;
    private final Component dungeon;
    private final Map<String, Component> rows = new HashMap<>();
    private final Map<String, Object> substitutions = new HashMap<>();

    DungeonSidebar(ExperiencePresentation policy, Player viewer, Component dungeon) {
        this.policy = policy;
        this.viewer = viewer;
        this.dungeon = dungeon;
        for (String key : ExperiencePresentation.WIDGETS) substitutions.put(key, Component.empty());
        substitutions.put("time_left", Component.empty());
        substitutions.put("dungeon", dungeon);
    }

    void runDetails(PlayableInstance instance) {
        if (instance.getDifficulty() != null) {
            metric("difficulty", instance.getDifficulty().getDisplayNameComponent());
            // Existing custom title and row templates use the preset ID.
            placeholder("difficulty", instance.getDifficulty().getNamespace());
        }
        if (instance.isStarted() || policy.text().get("widgets.elapsed").equals("shown"))
            metric("elapsed", ExperienceService.clock(instance.getTimeElapsed()));
        else placeholder("elapsed", ExperienceService.clock(0));
        if (instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0)
            metric("remaining", ExperienceService.clock(instance.getTimeLeft()));
    }

    void row(String key, Component value) {
        rows.put(key, value);
        substitutions.put(key, value);
    }

    void placeholder(String key, Object value) {
        substitutions.put(key, value);
    }

    void metric(String key, Object value) {
        String labelKey = key.equals("elapsed") ? "run-time" : key;
        Component content =
                value instanceof Component component ? component : Component.text(value.toString());
        String symbol =
                switch (key) {
                    case "lives" -> "♥ ";
                    case "elapsed", "remaining" -> "◷ ";
                    case "respawn" -> "⚑ ";
                    default -> "› ";
                };
        var label =
                LangUtils.component(
                        "experience.ui-hud-" + labelKey,
                        viewer,
                        false,
                        Map.of("value", content.colorIfAbsent(NamedTextColor.WHITE)));
        row(
                key,
                Component.text(
                                symbol,
                                key.equals("lives") ? NamedTextColor.RED : NamedTextColor.DARK_AQUA)
                        .append(label));
        if (!key.equals("party") && !key.equals("respawn")) substitutions.put(key, content);
        if (key.equals("remaining")) substitutions.put("time_left", content);
    }

    HudFrame.Sidebar frame() {
        String template = policy.text().get("sidebar.title");
        Component title =
                template.isBlank()
                        ? dungeon.decorate(TextDecoration.BOLD)
                        : LangUtils.trusted(template, substitutions);
        return new HudFrame.Sidebar(title, policy.layout(rows, substitutions));
    }
}

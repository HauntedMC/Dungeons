package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ExperiencePresentationTest {
    @Test
    void explicitFalseAndEmptyRowsOverrideWithoutMaterializingInheritedSettings() {
        var global = new YamlConfiguration();
        var local = new YamlConfiguration();
        global.set("experience.defaults.channels.sidebar", true);
        global.set("experience.defaults.profile", "minimal");
        global.set("experience.defaults.sidebar.rows", List.of("objective", "leave"));
        local.set("experience.channels.sidebar", false);
        local.set("experience.sidebar.rows", List.of());
        var snapshot = ExperiencePresentation.resolve(local, global);
        assertFalse(snapshot.flags().get("channels.sidebar"));
        assertEquals("minimal", snapshot.text().get("profile"));
        assertEquals(List.of(), snapshot.rows().orElseThrow());
        assertFalse(local.contains("experience.profile"));
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.flags().put("enabled", false));
    }

    @Test
    void malformedPresentationIsRejectedSoTheHostCanRetainItsLastValidSnapshot() {
        var global = new YamlConfiguration();
        var local = new YamlConfiguration();
        for (String invalid :
                List.of(
                        "profile",
                        "channels.sidebar",
                        "widgets.lives",
                        "sidebar.rows",
                        "notifications.completed.chat")) {
            local.set("experience." + invalid, "invalid");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ExperiencePresentation.resolve(local, global),
                    invalid);
            local.set("experience." + invalid, null);
        }
        assertEquals(
                "balanced", ExperiencePresentation.resolve(local, global).text().get("profile"));
    }

    @Test
    void inheritedProfilesAndWidgetVisibilityUseTheSamePolicy() {
        var global = new YamlConfiguration();
        var local = new YamlConfiguration();
        global.set("experience.defaults.profile", "immersive");
        var policy = ExperiencePresentation.resolve(local, global);
        assertEquals("immersive", policy.profile("inherit"));
        assertEquals("minimal", policy.profile("minimal"));
        local.set("experience.widgets.objective", "hidden");
        local.set("experience.widgets.elapsed", "shown");
        var widgets =
                java.util.Map.of(
                        "objective", net.kyori.adventure.text.Component.text("Hidden goal"),
                        "remaining", net.kyori.adventure.text.Component.text("Time left"),
                        "elapsed", net.kyori.adventure.text.Component.text("Elapsed"));
        policy = ExperiencePresentation.resolve(local, global);
        assertEquals(
                java.util.List.of(widgets.get("remaining"), widgets.get("elapsed")),
                policy.layout(widgets, java.util.Map.of()));
        local.set(
                "experience.sidebar.rows",
                List.of("", "objective", "remaining", "", "", "elapsed", ""));
        policy = ExperiencePresentation.resolve(local, global);
        assertEquals(
                java.util.List.of(
                        widgets.get("remaining"),
                        net.kyori.adventure.text.Component.empty(),
                        widgets.get("elapsed")),
                policy.layout(widgets, java.util.Map.of()));
    }
}

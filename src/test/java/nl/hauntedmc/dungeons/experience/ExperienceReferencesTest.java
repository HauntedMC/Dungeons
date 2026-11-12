package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ExperienceReferencesTest {
    @Test
    void nestedGraphReferencesAreValidatedWithoutInstantiatingSnapshotObjects() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("objectives:\n  seals: {source: signal, selector: seal}\n");
        var definition = ExperienceDefinition.parse(yaml);
        var valid =
                Map.of("==", "dungeons.function.experience", "operation", "signal", "id", "seal");
        assertDoesNotThrow(
                () -> ExperienceReferences.yaml(Map.of("children", List.of(valid)), definition));
        var invalid =
                Map.of(
                        "==",
                        "dungeons.function.experience",
                        "operation",
                        "advance",
                        "id",
                        "missing");
        assertThrows(
                IllegalArgumentException.class,
                () -> ExperienceReferences.yaml(Map.of("children", List.of(invalid)), definition));
        assertDoesNotThrow(
                () -> ExperienceReferences.yaml(invalid, ExperienceDefinition.empty()),
                "Disabling mechanics must leave the independent HUD usable");
    }
}

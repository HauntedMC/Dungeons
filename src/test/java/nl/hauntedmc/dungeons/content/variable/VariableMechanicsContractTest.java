package nl.hauntedmc.dungeons.content.variable;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.util.instance.InstanceUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VariableMechanicsContractTest {
    @TempDir Path directory;

    @Test
    void arithmeticAndTriggerComparisonsRetainLargeScoresAndExactIntegerText() throws Exception {
        try (var paper = new PaperBoundary()) {
            var dungeon = paper.dungeon(directory.resolve("crypt"), new YamlConfiguration());
            var run = new StaticInstance(dungeon, null);
            var variables = run.getInstanceVariables();
            variables.set("score", 3000000000.0);
            variables.add("score", 0.5);
            variables.subtract("score", 0.5);
            assertEquals("3000000000", variables.getString("score"));
            assertTrue(InstanceUtils.compareVars(run, "<score> >= 3000000000"));
            variables.set("exact", "9007199254740993");
            assertEquals("9007199254740993", variables.getString("exact"));
            assertEquals("score=3000000000", InstanceUtils.parseVars(run, "score=<score>"));
        }
    }
}

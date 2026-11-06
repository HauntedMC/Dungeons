package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class DungeonLoadExceptionTest {
    @Test
    void serializationKeepsDiagnosticDetails() throws Exception {
        var original = new DungeonLoadException("broken dungeon", true, "first detail");
        original.addMessage("second detail");
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(original); }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (DungeonLoadException) input.readObject();
            assertEquals("broken dungeon", restored.getMessage());
            assertEquals(List.of("first detail", "second detail"), restored.getInfo());
            restored.addMessage("third detail");
            assertEquals(3, restored.getInfo().size());
        }
    }
}

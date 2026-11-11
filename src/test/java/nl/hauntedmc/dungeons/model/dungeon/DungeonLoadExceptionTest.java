package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class DungeonLoadExceptionTest {
    @Test
    void diagnosticsUseTheProvidedLoggerAndExposeImmutableDetails() {
        var failure = new DungeonLoadException("broken dungeon", true, "original");
        var logger = mock(Logger.class);
        failure.printError(logger, new File("crypt"));
        verify(logger).error("The dungeon '{}' was not loaded: {}", "crypt", "broken dungeon");
        verify(logger).error("  - {}", "original");
        verify(logger).error("Dungeon initialization failed for '{}'.", "crypt", failure);
        assertThrows(UnsupportedOperationException.class, () -> failure.getInfo().clear());
        assertEquals(List.of("original"), failure.getInfo());
    }

    @Test
    void serializationKeepsDiagnosticDetails() throws Exception {
        var original = new DungeonLoadException("broken dungeon", true, "first detail");
        original.addMessage("second detail");
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (DungeonLoadException) input.readObject();
            assertEquals("broken dungeon", restored.getMessage());
            assertEquals(List.of("first detail", "second detail"), restored.getInfo());
            restored.addMessage("third detail");
            assertEquals(3, restored.getInfo().size());
        }
    }
}

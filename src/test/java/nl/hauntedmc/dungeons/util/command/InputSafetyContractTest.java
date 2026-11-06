package nl.hauntedmc.dungeons.util.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class InputSafetyContractTest {
    @Test
    void numericEditorInputCannotAdmitNonFiniteValuesOrThrowForMissingInput() {
        Player player = mock(Player.class);
        for (String invalid :
                new String[] {null, "", "NaN", "Infinity", "-Infinity", "1e309", "text"}) {
            assertTrue(
                    InputUtils.readDoubleInput(player, invalid).isEmpty(), String.valueOf(invalid));
        }
        assertEquals(-1.5, InputUtils.readDoubleInput(player, "-1.5").orElseThrow());
        assertEquals(0.25, InputUtils.readDoubleInput(player, "2.5e-1").orElseThrow());
        for (String invalid : new String[] {null, "2147483648", "1.5", "text"}) {
            assertTrue(
                    InputUtils.readIntegerInput(player, invalid).isEmpty(),
                    String.valueOf(invalid));
        }
        assertEquals(
                Integer.MAX_VALUE, InputUtils.readIntegerInput(player, "2147483647").orElseThrow());
    }
}

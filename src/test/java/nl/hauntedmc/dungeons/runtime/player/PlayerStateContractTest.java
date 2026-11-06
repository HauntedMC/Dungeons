package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.support.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerStateContractTest {
    @TempDir Path directory;
    @Test
    void savedInventoryAndArmorAreIndependentOfItemsMutatedDuringTheRunAndAfterRestoration() {
        try(var paper=new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var state=new PlayerState();
            state.contents[0]=PlayerState.stack(7);
            state.contents[40]=PlayerState.stack(2);
            state.armor[0]=PlayerState.stack(1);
            var session=new DungeonPlayerSession(paper.plugin,state.player);
            session.saveInventory();
            state.contents[0].setAmount(1);
            state.contents[40].setAmount(1);
            state.armor[0].setAmount(0);
            session.restoreInventory();
            assertEquals(7,state.contents[0].getAmount());
            assertEquals(2,state.contents[40].getAmount());
            assertEquals(1,state.armor[0].getAmount());
            state.contents[0].setAmount(3);
            session.restoreInventory();
            assertEquals(7,state.contents[0].getAmount());
        }
    }
}

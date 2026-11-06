package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.support.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class PlayerStateContractTest {
    @TempDir Path directory;

    @Test
    void savedInventoryAndArmorAreIndependentOfItemsMutatedDuringTheRunAndAfterRestoration() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            state.contents[40] = PlayerState.stack(2);
            state.armor[0] = PlayerState.stack(1);
            var session = new DungeonPlayerSession(paper.plugin, state.player);
            session.saveInventory();
            state.contents[0].setAmount(1);
            state.contents[40].setAmount(1);
            state.armor[0].setAmount(0);
            session.restoreInventory();
            assertEquals(7, state.contents[0].getAmount());
            assertEquals(2, state.contents[40].getAmount());
            assertEquals(1, state.armor[0].getAmount());
            state.contents[0].setAmount(3);
            session.restoreInventory();
            assertEquals(7, state.contents[0].getAmount());
        }
    }

    @Test
    void experienceRestorationRunsAfterTheRespawnTickAndAlsoWorksWhenThePluginIsDisabled() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var scheduler = new CapturedScheduler(paper);
            var state = new PlayerState();
            var session = new DungeonPlayerSession(paper.plugin, state.player);
            session.setSavedExp(.65f);
            session.setSavedLevel(24);
            when(paper.plugin.isEnabled()).thenReturn(true);
            session.restoreExp();
            assertEquals(17, state.level);
            assertEquals(1, scheduler.jobs.getFirst().delay);
            scheduler.jobs.getFirst().run();
            assertEquals(24, state.level);
            assertEquals(.65f, state.exp);
            state.level = 0;
            state.exp = 0;
            when(paper.plugin.isEnabled()).thenReturn(false);
            session.restoreExp();
            assertEquals(24, state.level);
            assertEquals(.65f, state.exp);
            assertEquals(1, scheduler.jobs.size());
        }
    }

    @Test
    void hotbarSnapshotsPreserveAllSlotsAndHeldSelectionWithoutSharingMutableItems() {
        var state = new PlayerState();
        for (int i = 0; i < 9; i++) state.contents[i] = PlayerState.stack(i + 1);
        var snapshot = new PlayerHotbarSnapshot(state.player);
        state.contents[0].setAmount(50);
        state.heldSlot = 8;
        snapshot.applyTo(state.inventory, true);
        assertEquals(1, state.contents[0].getAmount());
        assertEquals(9, state.contents[8].getAmount());
        assertEquals(2, state.heldSlot);
        state.contents[0].setAmount(30);
        snapshot.applyTo(state.inventory);
        assertEquals(1, state.contents[0].getAmount());
        var replacement = PlayerState.stack(5);
        snapshot.setItem(3, replacement);
        replacement.setAmount(60);
        snapshot.applyTo(state.inventory);
        assertEquals(5, state.contents[3].getAmount());
    }

    @Test
    void editorInventoryRestorationDoesNotShareMutableStacksWithTheSavedSnapshot() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(8);
            state.contents[40] = PlayerState.stack(3);
            state.armor[0] = PlayerState.stack(1);
            var session = new DungeonPlayerSession(paper.plugin, state.player);
            session.saveEditInventory();
            state.contents[0].setAmount(1);
            state.contents[40].setAmount(1);
            state.armor[0].setAmount(0);
            session.loadEditInventory();
            scheduler.jobs.getLast().run();
            assertEquals(8, state.contents[0].getAmount());
            assertEquals(3, state.contents[40].getAmount());
            assertEquals(1, state.armor[0].getAmount());
            state.contents[0].setAmount(4);
            session.loadEditInventory();
            scheduler.jobs.getLast().run();
            assertEquals(8, state.contents[0].getAmount());
        }
    }

    @Test
    void restoringVitalStatsPreservesFoodAndClampsSavedHealthToTheCurrentMaximum() {
        try (var paper = new PaperBoundary();
                var health =
                        org.mockito.Mockito.mockStatic(
                                nl.hauntedmc.dungeons.util.entity.EntityUtils.class)) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            when(paper.plugin.isEnabled()).thenReturn(true);
            var state = new PlayerState();
            var session = new DungeonPlayerSession(paper.plugin, state.player);
            session.saveInventory();
            state.health = 1;
            state.food = 0;
            health.when(
                            () ->
                                    nl.hauntedmc.dungeons.util.entity.EntityUtils.getMaxHealth(
                                            state.player))
                    .thenReturn(10.0);
            session.restoreInventory();
            assertEquals(10, state.health);
            assertEquals(9, state.food);
            health.when(
                            () ->
                                    nl.hauntedmc.dungeons.util.entity.EntityUtils.getMaxHealth(
                                            state.player))
                    .thenReturn(20.0);
            session.restoreInventory();
            assertEquals(13, state.health);
        }
    }
}

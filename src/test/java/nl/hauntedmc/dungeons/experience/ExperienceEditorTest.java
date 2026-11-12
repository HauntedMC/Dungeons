package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.staff.StaffOperations;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExperienceEditorTest {
    @TempDir Path directory;

    private final class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final org.mockito.MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class);
        final org.mockito.MockedStatic<LangUtils> language = mockStatic(LangUtils.class);
        final Player player = mock(Player.class);
        final DungeonPlayerSession session = mock(DungeonPlayerSession.class);
        final DungeonDefinition dungeon = mock(DungeonDefinition.class);
        final EditableInstance instance = mock(EditableInstance.class);
        final ExperienceEditor editor;
        final Path file = directory.resolve("experience.yml");
        final String original = "version: 1\ncompletion: manual\n";

        Fixture() throws Exception {
            Files.writeString(file, original);
            var sessions = mock(PlayerSessionRegistry.class);
            paper.runtime.setPlayerSessions(sessions);
            when(sessions.get(player)).thenReturn(session);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.hasPermission("dungeons.staff.edit")).thenReturn(true);
            when(session.isEditMode()).thenReturn(true);
            when(session.getInstance()).thenReturn(instance);
            when(instance.getRuntime()).thenReturn(paper.runtime);
            when(instance.getDungeon()).thenReturn(dungeon);
            when(instance.isEditInstance()).thenReturn(true);
            when(dungeon.getRuntime()).thenReturn(paper.runtime);
            when(dungeon.getEditSession()).thenReturn(instance);
            when(dungeon.getWorldName()).thenReturn("crypt");
            when(dungeon.getFolder()).thenReturn(directory.toFile());
            var staff = mock(StaffOperations.class);
            paper.runtime.setStaffOperations(staff);
            when(staff.maintenance("crypt")).thenReturn(true);
            when(staff.instances("crypt")).thenReturn(List.of(instance));
            editor = new ExperienceEditor(paper.runtime);
            command("set", "enabled", "false");
        }

        void command(String... args) {
            var command = new String[args.length + 2];
            command[0] = "edit";
            command[1] = "experience";
            System.arraycopy(args, 0, command, 2, args.length);
            editor.handle(player, command);
        }

        void assertDraftCanStillBeSaved() throws Exception {
            editor.saveDraft(player);
            assertTrue(Files.readString(file).contains("enabled: false"));
        }

        @Override
        public void close() {
            editor.close();
            language.close();
            handlers.close();
            paper.close();
        }
    }

    @Test
    void failedAtomicWritePreservesFileAndDraftForRetry() throws Exception {
        try (var fixture = new Fixture()) {
            var channel = mock(FileChannel.class);
            when(channel.write(any(ByteBuffer.class)))
                    .thenAnswer(
                            call -> {
                                ByteBuffer bytes = call.getArgument(0);
                                int size = bytes.remaining();
                                bytes.position(bytes.limit());
                                return size;
                            });
            doThrow(new IOException("disk full")).when(channel).force(true);
            try (var channels = mockStatic(FileChannel.class)) {
                channels.when(() -> FileChannel.open(any(Path.class), eq(StandardOpenOption.WRITE)))
                        .thenReturn(channel);
                var failure =
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> fixture.editor.saveDraft(fixture.player));
                assertTrue(failure.getMessage().contains("Could not save experience"));
            }
            assertEquals(fixture.original, Files.readString(fixture.file));
            try (var files = Files.list(directory)) {
                assertEquals(List.of(fixture.file), files.toList());
            }
            fixture.assertDraftCanStillBeSaved();
        }
    }

    @Test
    void externalModificationIsRejectedWithoutLosingDraft() throws Exception {
        try (var fixture = new Fixture()) {
            String external = fixture.original + "# another builder\n";
            Files.writeString(fixture.file, external);
            var failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> fixture.editor.saveDraft(fixture.player));
            assertTrue(failure.getMessage().contains("changed externally; reload"));
            assertEquals(external, Files.readString(fixture.file));
            Files.writeString(fixture.file, fixture.original);
            fixture.assertDraftCanStillBeSaved();
        }
    }

    @Test
    void lostPermissionBlocksBothDraftMutationAndSave() throws Exception {
        try (var fixture = new Fixture()) {
            when(fixture.player.hasPermission("dungeons.staff.edit")).thenReturn(false);
            var failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> fixture.editor.saveDraft(fixture.player));
            assertTrue(failure.getMessage().contains("Reopen"));
            fixture.language.verify(
                    () ->
                            LangUtils.sendRaw(
                                    fixture.player,
                                    "Missing permission: dungeons.staff.edit.crypt"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> fixture.command("set", "enabled", "true"));
            assertEquals(fixture.original, Files.readString(fixture.file));
            when(fixture.player.hasPermission("dungeons.staff.edit")).thenReturn(true);
            fixture.assertDraftCanStillBeSaved();
        }
    }

    @Test
    void changedEditorOwnershipBlocksStaleSave() throws Exception {
        try (var fixture = new Fixture()) {
            when(fixture.dungeon.getEditSession()).thenReturn(null);
            assertThrows(
                    IllegalArgumentException.class, () -> fixture.editor.saveDraft(fixture.player));
            fixture.language.verify(
                    () ->
                            LangUtils.sendRaw(
                                    fixture.player, "Editor ownership has changed; reopen it"));
            assertEquals(fixture.original, Files.readString(fixture.file));
            when(fixture.dungeon.getEditSession()).thenReturn(fixture.instance);
            fixture.assertDraftCanStillBeSaved();
        }
    }
}

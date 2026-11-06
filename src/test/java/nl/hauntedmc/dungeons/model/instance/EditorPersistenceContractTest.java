package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.concurrent.*;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorPersistenceContractTest {
    @TempDir Path directory;
    @Test
    void editorSaveCopiesTheWorldAndFunctionsWithoutReplacingRoomTemplatesOrWorldIdentity() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            Path source=Files.createDirectories(directory.resolve("live"));
            Files.writeString(source.resolve("level.dat"),"edited world"); Files.writeString(source.resolve("uid.dat"),"runtime identity");
            Path incoming=Files.createDirectories(source.resolve("rooms")); Files.writeString(incoming.resolve("room.yml"),"wrong room");
            Path rooms=Files.createDirectories(f.dungeon.getFolder().toPath().resolve("rooms"));
            Files.writeString(rooms.resolve("room.yml"),"template room");
            when(f.world.getWorldFolder()).thenReturn(source.toFile());
            var editor=new Editor(f); var latch=new CountDownLatch(1);
            assertTrue(editor.saveWorldAsync(latch).get(1,TimeUnit.SECONDS));
            assertEquals(0,latch.getCount()); assertFalse(f.dungeon.isSaving());
            assertEquals("edited world",Files.readString(f.dungeon.getFolder().toPath().resolve("level.dat")));
            assertEquals("template room",Files.readString(rooms.resolve("room.yml")));
            assertFalse(Files.exists(f.dungeon.getFolder().toPath().resolve("uid.dat")));
            assertTrue(Files.exists(f.dungeon.getFolder().toPath().resolve("functions.yml")));
            verify(f.world).save(true);
        }
    }
    static class Editor extends EditableInstance {
        CompletableFuture<Boolean> pending;
        boolean throwCommit;
        Editor(RunFixture f) { super(f.dungeon,null); this.instanceWorld=f.world; this.startLocation=new Location(f.world,0,70,0); }
        @Override protected CompletableFuture<Boolean> commitWorldAsync(World world) {
            if(throwCommit) throw new IllegalStateException("commit failed");
            return pending==null?super.commitWorldAsync(world):pending;
        }
    }
}

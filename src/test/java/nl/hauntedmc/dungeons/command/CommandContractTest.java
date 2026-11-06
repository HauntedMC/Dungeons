package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.queue.*;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandContractTest {
    @TempDir Path directory;
    @Test
    void rootPermissionDenialPreventsGameplayAndDestructiveCommandDispatch() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var command=new CommandFixture(f); command.permissions.clear();
            for(String[] args:List.of(new String[]{"play","crypt"},new String[]{"delete","crypt"},new String[]{"import","source"},new String[]{"reload"})) command.execute(args);
            verifyNoInteractions(command.coordinator,command.lifecycle,command.catalog);
            assertTrue(Files.isDirectory(f.dungeon.getFolder().toPath())); assertFalse(f.dungeon.isMarkedForDelete());
        }
    }

    @Test
    void playCommandsForwardTheCorrectPlayerAndRejectUnauthorizedBusyOrDisabledStarts() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var command=new CommandFixture(f); command.permissions.add("dungeons.play");
            command.execute("play","crypt"); verify(command.coordinator).sendToDungeon(command.caller.player,"crypt","");
            clearInvocations(command.coordinator);
            var other=new PlayerState(); var session=f.session(other); f.paper.bukkit.when(() -> Bukkit.getPlayer("Other")).thenReturn(other.player);
            command.execute("play","crypt","Other"); verifyNoInteractions(command.coordinator);
            command.permissions.add("dungeons.play.send"); command.execute("play","crypt:hard","Other");
            verify(command.coordinator).sendToDungeon(other.player,"crypt","hard"); clearInvocations(command.coordinator);
            session.setAwaitingDungeon(true); command.execute("play","crypt","Other"); verifyNoInteractions(command.coordinator);
            session.setAwaitingDungeon(false); session.setInstance(f.run); command.execute("play","crypt","Other"); verifyNoInteractions(command.coordinator);
            session.setInstance(null); f.dungeon.setSaveConfig("dungeon.enabled",false);
            command.execute("play","crypt","Other"); verifyNoInteractions(command.coordinator);
        }
    }
    static final class CommandFixture {
        final PlayerState caller=new PlayerState();
        final Set<String> permissions=new HashSet<>(Set.of("dungeons.core"));
        final DungeonRepository catalog=mock(DungeonRepository.class);
        final DungeonQueueCoordinator coordinator=mock(DungeonQueueCoordinator.class);
        final LifecycleCoordinator lifecycle=mock(LifecycleCoordinator.class);
        final DungeonQueueRegistry queues;
        final DungeonCommand root;
        CommandFixture(RunFixture f) {
            f.session(caller); when(caller.player.hasPermission(anyString())).thenAnswer(c -> permissions.contains(c.getArgument(0)));
            when(catalog.get("crypt")).thenReturn(f.dungeon); when(catalog.getLoadedDungeons()).thenReturn(List.of(f.dungeon));
            var server=mock(Server.class); when(f.paper.plugin.getServer()).thenReturn(server);
            queues=new DungeonQueueRegistry(f.paper.runtime.playerSessions(),f.paper.runtime.activeInstances());
            var teams=new DungeonTeamService(f.paper.plugin,f.paper.runtime.playerSessions(),queues);
            root=new DungeonCommand(f.paper.plugin,f.paper.runtime.playerSessions(),catalog,f.paper.runtime.activeInstances(),coordinator,queues,teams,
                mock(LootTableRepository.class),mock(FunctionRegistry.class),lifecycle,mock(GuiService.class),"dungeon");
        }
        void execute(String...args) { root.onCommand(caller.player,mock(org.bukkit.command.Command.class),"dungeon",args); }
    }
}

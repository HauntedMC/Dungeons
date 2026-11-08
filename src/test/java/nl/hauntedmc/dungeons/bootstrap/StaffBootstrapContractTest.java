package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.support.StaffFixture;

import org.bukkit.Server;
import org.bukkit.command.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;

class StaffBootstrapContractTest {
    @TempDir Path directory;

    @Test
    void registeredRootUsesStaffServicesForArchivalAndChecksCorePermissionFirst() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var server = mock(Server.class);
            var command = mock(PluginCommand.class);
            when(f.run.paper.plugin.getServer()).thenReturn(server);
            when(server.getPluginCommand("dungeon")).thenReturn(command);
            f.runtime.setGuiService(mock(GuiService.class));
            f.runtime.setFunctionRegistry(mock(FunctionRegistry.class));
            new CommandBootstrap(f.runtime, f.lifecycle).registerCommands();
            var capture = ArgumentCaptor.forClass(CommandExecutor.class);
            verify(command).setExecutor(capture.capture());
            var executor = capture.getValue();
            f.grants.clear();
            executor.onCommand(
                    f.actor,
                    command,
                    "dungeon",
                    new String[] {"admin", "archive", "crypt", "Retire"});
            assertTrue(f.run.dungeon.getFolder().exists());

            f.grants.add("*");
            executor.onCommand(
                    f.actor,
                    command,
                    "dungeon",
                    new String[] {"admin", "archive", "crypt", "Retire"});
            assertTrue(f.run.dungeon.getFolder().exists());

            String token = f.token();
            executor.onCommand(
                    f.actor,
                    command,
                    "dungeon",
                    new String[] {"admin", "archive", "crypt", "Retire", "--confirm", token});
            f.flush();
            assertFalse(f.run.dungeon.getFolder().exists());
        }
    }
}

package nl.hauntedmc.dungeons.content.function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class PlayerEffectContractTest {
    @TempDir Path directory;

    @Test
    void commandContextsSanitizeInputDeduplicateOnlineTargetsAndAnchorConsoleExecution()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var first = new PlayerState();
            when(first.player.getName()).thenReturn("Alice");
            var source = f.session(first);
            var second = new PlayerState();
            when(second.player.getName()).thenReturn("Bob");
            var other = f.session(second);
            var event = mock(TriggerFireEvent.class);
            when(event.getPlayerSession()).thenReturn(source);
            var command =
                    GameplayFunctionContractTest.bind(
                            new CommandFunction(
                                    Map.of(
                                            "command",
                                            " /say {player} ",
                                            "commandType",
                                            1,
                                            "forEachPlayer",
                                            true)),
                            f);
            command.setTargetType(FunctionTargetType.PLAYER);
            command.runFunction(event, List.of(source, source, other));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(first.player, "say Alice"));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(second.player, "say Bob"));
            second.online = false;
            command.runFunction(event, List.of(other));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(second.player, "say Bob"), times(1));
            var console = mock(ConsoleCommandSender.class);
            f.paper.bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            when(f.world.getKey()).thenReturn(new NamespacedKey("dungeons", "crypt_0"));
            var consoleCommand =
                    GameplayFunctionContractTest.bind(
                            new CommandFunction(Map.of("command", "/say ready", "commandType", 2)),
                            f);
            consoleCommand.runFunction(event, List.of());
            f.paper.bukkit.verify(
                    () ->
                            Bukkit.dispatchCommand(
                                    console,
                                    "execute in dungeons:crypt_0 positioned 0.0 70.0 0.0 run say ready"));
            GameplayFunctionContractTest.bind(new CommandFunction(Map.of("command", " / ")), f)
                    .runFunction(event, List.of(source));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(first.player, ""), never());
        }
    }
}

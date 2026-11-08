package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.gui.loot.LootTableEditor;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.queue.*;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.runtime.staff.StaffOperations;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.*;

/**
 * Real staff/runtime services; Paper storage, schedulers and command output are controlled
 * boundaries.
 */
public final class StaffFixture implements AutoCloseable {
    public final RunFixture run;
    public final DungeonsRuntime runtime;
    public final StaffOperations ops;
    public final LifecycleCoordinator lifecycle = mock(LifecycleCoordinator.class);
    public final CommandSender actor = mock(CommandSender.class);
    public final Set<String> grants = new HashSet<>(Set.of("*"));
    public final Set<String> denials = new HashSet<>();
    public final List<String> messages = new ArrayList<>();
    public final Map<String, StaticDungeon> catalog = new LinkedHashMap<>();
    public final Map<UUID, PlayerState> players = new LinkedHashMap<>();
    public final DungeonRepository repository = mock(DungeonRepository.class);
    private int immediateIndex;

    public StaffFixture(Path directory) throws Exception {
        run = new RunFixture(directory, new YamlConfiguration());
        runtime = run.paper.runtime;
        runtime.setDungeonFiles(directory.toFile());
        var queues = new DungeonQueueRegistry(runtime.playerSessions(), runtime.activeInstances());
        runtime.setQueueRegistry(queues);
        var teams = new DungeonTeamService(run.paper.plugin, runtime.playerSessions(), queues);
        runtime.setTeamService(teams);
        var coordinator =
                new DungeonQueueCoordinator(
                        run.paper.plugin,
                        runtime.playerSessions(),
                        runtime.activeInstances(),
                        queues,
                        teams);
        runtime.setDungeonQueueCoordinator(coordinator);
        queues.setQueueCoordinator(coordinator);
        catalog.put("crypt", run.dungeon);
        when(repository.get(anyString())).thenAnswer(c -> catalog.get(c.getArgument(0)));
        when(repository.getLoadedDungeons()).thenAnswer(c -> List.copyOf(catalog.values()));
        when(repository.loadFailures()).thenReturn(Map.of());
        when(repository.reloadAllAsync())
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(Map.of()));
        doAnswer(
                        c -> {
                            catalog.remove(((StaticDungeon) c.getArgument(0)).getWorldName());
                            return null;
                        })
                .when(repository)
                .remove(any());
        runtime.setDungeonCatalog(repository);
        coordinator.setDungeonCatalog(repository);
        var loot = mock(LootTableRepository.class);
        when(loot.getTables()).thenReturn(List.of());
        when(loot.isWritable()).thenReturn(true);
        runtime.setLootTableRepository(loot);
        runtime.setLootTableEditor(mock(LootTableEditor.class));
        when(actor.getName()).thenReturn("Staff");
        when(actor.hasPermission(anyString()))
                .thenAnswer(
                        c ->
                                !denials.contains(c.getArgument(0))
                                        && (grants.contains("*")
                                                || grants.contains(c.getArgument(0))));
        when(actor.isPermissionSet(anyString()))
                .thenAnswer(
                        c ->
                                denials.contains(c.getArgument(0))
                                        || grants.contains(c.getArgument(0)));
        doAnswer(
                        c -> {
                            messages.add(
                                    PlainTextComponentSerializer.plainText()
                                            .serialize(c.getArgument(0)));
                            return null;
                        })
                .when(actor)
                .sendMessage(any(Component.class));
        doAnswer(
                        c -> {
                            messages.add(c.getArgument(0));
                            return null;
                        })
                .when(actor)
                .sendMessage(anyString());
        run.paper
                .bukkit
                .when(Bukkit::getOnlinePlayers)
                .thenAnswer(
                        c ->
                                players.values().stream()
                                        .map(p -> p.player)
                                        .filter(org.bukkit.entity.Player::isOnline)
                                        .toList());
        run.paper
                .bukkit
                .when(() -> Bukkit.getPlayer(any(UUID.class)))
                .thenAnswer(
                        c ->
                                players.containsKey(c.getArgument(0))
                                        ? players.get(c.getArgument(0)).player
                                        : null);
        run.paper
                .bukkit
                .when(() -> Bukkit.getPlayerExact(anyString()))
                .thenAnswer(
                        c ->
                                players.values().stream()
                                        .map(p -> p.player)
                                        .filter(p -> p.getName().equals(c.getArgument(0)))
                                        .findFirst()
                                        .orElse(null));
        ops = new StaffOperations(runtime);
        runtime.setStaffOperations(ops);
    }

    public PlayerState player(String name) {
        var state = new PlayerState();
        when(state.player.getName()).thenReturn(name);
        when(state.player.hasPermission(anyString())).thenReturn(true);
        when(state.player.teleport(any(Location.class)))
                .thenAnswer(
                        c -> {
                            state.location = c.getArgument(0);
                            return true;
                        });
        UUID worldId = state.originWorld.getUID();
        run.paper.bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(state.originWorld);
        players.put(state.player.getUniqueId(), state);
        run.session(state);
        return state;
    }

    public StaticDungeon dungeon(String name) throws Exception {
        var d =
                run.paper.dungeon(
                        run.dungeon.getFolder().toPath().resolveSibling(name),
                        new YamlConfiguration());
        catalog.put(name, d);
        return d;
    }

    public void flush() {
        for (int pass = 0; pass < 20; pass++) {
            ConfigurationFile.flushPendingSaves();
            if (immediateIndex == run.scheduler.immediate.size()) return;
            while (immediateIndex < run.scheduler.immediate.size())
                run.scheduler.immediate.get(immediateIndex++).run();
        }
        throw new AssertionError("Scheduler did not settle");
    }

    public void tick() {
        run.scheduler.jobs.stream()
                .filter(j -> j.period == 20L && !j.cancelled)
                .toList()
                .forEach(CapturedScheduler.Job::run);
    }

    public String token() {
        return messages.stream()
                .filter(m -> m.contains("--confirm "))
                .reduce((a, b) -> b)
                .orElseThrow()
                .split("--confirm ")[1]
                .strip();
    }

    @Override
    public void close() {
        ops.close();
        flush();
        run.close();
    }
}

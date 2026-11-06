package nl.hauntedmc.dungeons.acceptance;

import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

import javax.xml.stream.XMLOutputFactory;

/** Test-only consumer of the packaged plugin, loaded into a disposable real Paper server. */
public final class AcceptancePlugin extends JavaPlugin {
    @FunctionalInterface
    interface Check {
        CompletionStage<Void> run() throws Exception;
    }

    record Scenario(String name, Check check) {}

    record Result(String name, double seconds, Throwable failure) {}

    private boolean running;

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("dungeonsacceptance")).setExecutor(this::runCommand);
    }

    DungeonsRuntime runtime() {
        return RuntimeContext.runtime();
    }

    private boolean runCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) || running) return false;
        if (args.length != 1 || !args[0].equals("run")) return false;
        running = true;
        var scenarios =
                List.of(
                        new Scenario("packaged-bootstrap", this::bootstrap),
                        new Scenario(
                                "native-item-and-graph-persistence",
                                () -> NativePersistence.run(this)),
                        new Scenario("copied-world-lifecycle", () -> WorldLifecycle.run(this)),
                        new Scenario("native-gameplay-mechanics", () -> NativeMechanics.run(this)),
                        new Scenario("native-tile-structure", () -> NativeStructure.run(this)),
                        new Scenario(
                                "native-branching-generation", () -> NativeBranching.run(this)));
        var results = new ArrayList<Result>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var scenario : scenarios) {
            chain = chain.thenCompose(ignored -> execute(scenario, results));
        }
        chain.whenComplete((ignored, failure) -> finish(args[0], scenarios, results, failure));
        return true;
    }

    private CompletionStage<Void> bootstrap() {
        var plugin = Bukkit.getPluginManager().getPlugin("Dungeons");
        require(plugin != null && plugin.isEnabled(), "Packaged Dungeons is not enabled");
        require(Bukkit.getPluginCommand("dungeon") != null, "Dungeon command is missing");
        require(runtime().environment().plugin() == plugin, "Runtime refers to another plugin");
        require(
                runtime().dungeonCatalog().get("config-migration") != null,
                "Migrated dungeon is absent from the actual catalog");
        require(
                runtime().functionRegistry().getRegisteredFunctions().size() == 33,
                "Unexpected function registry contents");
        require(
                runtime().triggerRegistry().getRegisteredTriggers().size() == 14,
                "Unexpected trigger registry contents");
        require(
                runtime().conditionRegistry().getRegisteredConditions().size() == 7,
                "Unexpected condition registry contents");
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> execute(Scenario scenario, List<Result> results) {
        long start = System.nanoTime();
        var done = new CompletableFuture<Void>();
        try {
            scenario.check()
                    .run()
                    .whenComplete(
                            (ignored, failure) ->
                                    Bukkit.getScheduler()
                                            .runTask(
                                                    this,
                                                    () -> {
                                                        results.add(
                                                                new Result(
                                                                        scenario.name(),
                                                                        (System.nanoTime() - start)
                                                                                / 1_000_000_000.0,
                                                                        failure));
                                                        if (failure == null) {
                                                            getLogger()
                                                                    .info(
                                                                            "DUNGEONS_ACCEPTANCE_CASE_PASS name="
                                                                                    + scenario
                                                                                            .name());
                                                            done.complete(null);
                                                        } else {
                                                            done.completeExceptionally(failure);
                                                        }
                                                    }));
        } catch (Throwable failure) {
            results.add(
                    new Result(
                            scenario.name(),
                            (System.nanoTime() - start) / 1_000_000_000.0,
                            failure));
            done.completeExceptionally(failure);
        }
        return done;
    }

    private void finish(
            String phase, List<Scenario> scenarios, List<Result> results, Throwable failure) {
        try {
            Files.createDirectories(getDataFolder().toPath());
            try (var output =
                    Files.newOutputStream(
                            getDataFolder().toPath().resolve("results-" + phase + ".xml"))) {
                var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
                xml.writeStartDocument("UTF-8", "1.0");
                xml.writeStartElement("testsuite");
                xml.writeAttribute("name", "Dungeons.Paper." + phase);
                xml.writeAttribute("tests", Integer.toString(scenarios.size()));
                xml.writeAttribute(
                        "failures",
                        Long.toString(
                                results.stream()
                                        .filter(result -> result.failure() != null)
                                        .count()));
                xml.writeAttribute("skipped", Integer.toString(scenarios.size() - results.size()));
                for (var scenario : scenarios) {
                    xml.writeStartElement("testcase");
                    xml.writeAttribute("classname", "Dungeons.Paper");
                    xml.writeAttribute("name", scenario.name());
                    var result =
                            results.stream()
                                    .filter(r -> r.name().equals(scenario.name()))
                                    .findFirst()
                                    .orElse(null);
                    if (result == null) {
                        xml.writeEmptyElement("skipped");
                    } else {
                        xml.writeAttribute("time", Double.toString(result.seconds()));
                        if (result.failure() != null) {
                            xml.writeStartElement("failure");
                            xml.writeCharacters(result.failure().toString());
                            xml.writeEndElement();
                        }
                    }
                    xml.writeEndElement();
                }
                xml.writeEndElement();
                xml.writeEndDocument();
                xml.close();
            }
        } catch (Exception reportFailure) {
            if (failure == null) failure = reportFailure;
            else failure.addSuppressed(reportFailure);
        }
        running = false;
        if (failure != null) {
            getLogger().log(Level.SEVERE, "DUNGEONS_ACCEPTANCE_FAIL phase=" + phase, failure);
        } else {
            getLogger()
                    .info(
                            "DUNGEONS_ACCEPTANCE_COMPLETE phase="
                                    + phase
                                    + " passed="
                                    + results.size());
        }
    }

    CompletableFuture<Void> await(String description, BooleanSupplier ready) {
        var result = new CompletableFuture<Void>();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (ready.getAsBoolean()) {
                        result.complete(null);
                    } else if (System.nanoTime() >= deadline) {
                        result.completeExceptionally(
                                new AssertionError("Timed out: " + description));
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
                if (result.isDone()) cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
        return result;
    }

    CompletableFuture<Void> onMain(CompletionStage<?> pending) {
        var result = new CompletableFuture<Void>();
        pending.whenComplete(
                (value, failure) ->
                        Bukkit.getScheduler()
                                .runTask(
                                        this,
                                        () -> {
                                            if (failure != null)
                                                result.completeExceptionally(failure);
                                            else if (Boolean.FALSE.equals(value))
                                                result.completeExceptionally(
                                                        new AssertionError(
                                                                "Asynchronous operation failed"));
                                            else result.complete(null);
                                        }));
        return result;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

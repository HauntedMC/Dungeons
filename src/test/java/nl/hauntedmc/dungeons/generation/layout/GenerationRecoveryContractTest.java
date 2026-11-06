package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.generation.room.RotatedRoom;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.*;
import java.util.concurrent.*;

class GenerationRecoveryContractTest {
    @Test
    void failedOrInterruptedGenerationCancelsWorkersShutsExecutorsAndPreservesInterruptStatus()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            for (Exception failure :
                    List.of(
                            new TimeoutException("timeout"),
                            new ExecutionException(new IllegalStateException("worker")),
                            new InterruptedException("interrupted"))) {
                try (var executors = mockStatic(Executors.class)) {
                    var executor = mock(ExecutorService.class);
                    Future<Layout.GenerationResult> future = mock();
                    executors.when(Executors::newSingleThreadExecutor).thenReturn(executor);
                    doReturn(future)
                            .when(executor)
                            .submit(ArgumentMatchers.<Callable<Layout.GenerationResult>>any());
                    doReturn(future).when(executor).submit(any(Runnable.class));
                    doThrow(failure).when(future).get(anyLong(), any(TimeUnit.class));
                    var graph = new LayoutExecutionContractTest.Graph(paper);
                    var expected =
                            failure instanceof TimeoutException
                                    ? Layout.GenerationResult.TIMED_OUT
                                    : Layout.GenerationResult.GENERIC_FAILURE;
                    try {
                        assertEquals(expected, graph.layout.generate());
                        assertEquals(
                                failure instanceof InterruptedException,
                                Thread.currentThread().isInterrupted());
                    } finally {
                        Thread.interrupted();
                    }
                    verify(future).cancel(true);
                    verify(executor).shutdownNow();
                    clearInvocations(future, executor);
                    try {
                        assertEquals(
                                Layout.GenerationResult.GENERIC_FAILURE,
                                graph.layout.processQueue());
                        assertEquals(
                                failure instanceof InterruptedException,
                                Thread.currentThread().isInterrupted());
                    } finally {
                        Thread.interrupted();
                    }
                    verify(future).cancel(true);
                    verify(executor).shutdownNow();
                }
            }
        }
    }

    @Test
    void rejectedWorkerSubmissionReturnsFailureAndClosesBothGenerationExecutors() throws Exception {
        try (var paper = new PaperBoundary();
                var executors = mockStatic(Executors.class)) {
            var executor = mock(ExecutorService.class);
            executors.when(Executors::newSingleThreadExecutor).thenReturn(executor);
            doThrow(new RejectedExecutionException("stopped"))
                    .when(executor)
                    .submit(ArgumentMatchers.<Callable<Layout.GenerationResult>>any());
            doThrow(new RejectedExecutionException("stopped"))
                    .when(executor)
                    .submit(any(Runnable.class));
            var graph = new LayoutExecutionContractTest.Graph(paper);
            assertEquals(
                    Layout.GenerationResult.GENERIC_FAILURE,
                    assertDoesNotThrow(graph.layout::generate));
            verify(executor).shutdownNow();
            clearInvocations(executor);
            assertEquals(
                    Layout.GenerationResult.GENERIC_FAILURE,
                    assertDoesNotThrow(() -> graph.layout.processQueue()));
            verify(executor).shutdownNow();
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void cancellationTerminatesGenerationAndDoesNotPoisonTheNextFullGeneration() {
        try (var paper = new PaperBoundary()) {
            var graph = new LayoutExecutionContractTest.Graph(paper);
            var settings = new YamlConfiguration();
            settings.set("generator.seal_open_connectors_with", "AIR");
            settings.set("generator.room_target.min", 2);
            settings.set("generator.room_target.max", 2);
            var layout =
                    new LayoutExecutionContractTest.TestLayout(graph.dungeon, settings) {
                        int attempts;

                        @Override
                        protected GenerationResult tryLayout() {
                            if (attempts++ == 0) {
                                this.cancelled = true;
                                return GenerationResult.BAD_LAYOUT;
                            }
                            return super.tryLayout();
                        }
                    };
            assertEquals(Layout.GenerationResult.CANCELLED, layout.generate());
            assertEquals(Layout.GenerationResult.SUCCESS, layout.generate());
            assertEquals(2, layout.getAllRooms().size());
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(20)
    void failedPartialBranchesRollbackRoomsAndUsedConnectorsWithoutDamagingTheTrunk() {
        try (var paper = new PaperBoundary()) {
            var graph = new BranchingExecutionContractTest.Graph(paper);
            graph.settings.set("generator.branching.branches.side.room_target.min", 2);
            graph.settings.set("generator.branching.branches.side.room_target.max", 2);
            var side = graph.definitions.get("side");
            var connectors =
                    List.of(
                            graph.entrance(1, 0, SimpleLocation.Direction.NORTH),
                            graph.exit(1, 2, SimpleLocation.Direction.SOUTH, "side"));
            when(side.getConnectors()).thenReturn(connectors);
            var rotation = new RotatedRoom(side, 0);
            when(side.getOrientations()).thenReturn(List.of(rotation));
            assertEquals(Layout.GenerationResult.BAD_LAYOUT, graph.layout.attempt());
            assertEquals(3, graph.layout.getAllRooms().size());
            assertTrue(
                    graph.layout.getAllRooms().stream()
                            .noneMatch(room -> room.getSource().getOrigin() == side));
            var middle =
                    graph.layout.getAllRooms().stream()
                            .filter(
                                    room ->
                                            room.getSource().getOrigin()
                                                    == graph.definitions.get("middle"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(2, middle.getUsedConnectors().size());
            assertTrue(
                    graph.layout.failureReasons.containsKey(
                            GenerationFailureReason.BRANCH_TARGET_TOO_SMALL));
        }
    }
}

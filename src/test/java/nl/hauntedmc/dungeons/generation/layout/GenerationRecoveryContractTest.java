package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.junit.jupiter.api.Test;

class GenerationRecoveryContractTest {
    @Test
    void failedOrInterruptedGenerationCancelsWorkersShutsExecutorsAndPreservesInterruptStatus() throws Exception {
        try(var paper=new PaperBoundary()) {
            for(Exception failure:List.of(new TimeoutException("timeout"),new ExecutionException(new IllegalStateException("worker")),new InterruptedException("interrupted"))) {
                try(var executors=mockStatic(Executors.class)) {
                    var executor=mock(ExecutorService.class); var future=mock(Future.class);
                    executors.when(Executors::newSingleThreadExecutor).thenReturn(executor);
                    doReturn(future).when(executor).submit(any(Callable.class));
                    doReturn(future).when(executor).submit(any(Runnable.class));
                    doThrow(failure).when(future).get(anyLong(),any(TimeUnit.class));
                    var graph=new LayoutExecutionContractTest.Graph(paper);
                    var expected=failure instanceof TimeoutException?Layout.GenerationResult.TIMED_OUT:Layout.GenerationResult.GENERIC_FAILURE;
                    try {
                        assertEquals(expected,graph.layout.generate());
                        assertEquals(failure instanceof InterruptedException,Thread.currentThread().isInterrupted());
                    } finally { Thread.interrupted(); }
                    verify(future).cancel(true); verify(executor).shutdownNow();
                    clearInvocations(future,executor);
                    try {
                        assertEquals(Layout.GenerationResult.GENERIC_FAILURE,graph.layout.processQueue());
                        assertEquals(failure instanceof InterruptedException,Thread.currentThread().isInterrupted());
                    } finally { Thread.interrupted(); }
                    verify(future).cancel(true); verify(executor).shutdownNow();
                }
            }
        }
    }
}

package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/** Captures Paper scheduling and cancellation; each run is driven explicitly without sleeping. */
public final class CapturedScheduler {
    public final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    public final List<Job> jobs = new ArrayList<>();
    public final List<Job> immediate = new ArrayList<>();
    public final List<Job> asynchronous = new ArrayList<>();
    public final java.util.concurrent.BlockingQueue<Job> readyImmediate = new java.util.concurrent.LinkedBlockingQueue<>();
    private final List<Job> all = new ArrayList<>();

    public CapturedScheduler(PaperBoundary paper) {
        paper.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong()))
                .thenAnswer(call -> capture(call.getArgument(1), call.getArgument(2), 0));
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(
                        call ->
                                capture(
                                        call.getArgument(1),
                                        call.getArgument(2),
                                        call.getArgument(3)));
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class)))
                .thenAnswer(c -> captureIn(immediate, c.getArgument(1), 0, 0));
        when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class)))
                .thenAnswer(c -> captureIn(asynchronous, c.getArgument(1), 0, 0));
        when(scheduler.runTaskLaterAsynchronously(
                        any(Plugin.class), any(Runnable.class), anyLong()))
                .thenAnswer(c -> captureIn(asynchronous, c.getArgument(1), c.getArgument(2), 0));
        doAnswer(
                        call -> {
                            all.stream()
                                    .filter(j -> j.id == (int) call.getArgument(0))
                                    .forEach(j -> j.cancelled = true);
                            return null;
                        })
                .when(scheduler)
                .cancelTask(anyInt());
    }

    private BukkitTask capture(Runnable work, long delay, long period) {
        return captureIn(jobs, work, delay, period);
    }

    private synchronized BukkitTask captureIn(List<Job> queue, Runnable work, long delay, long period) {
        Job job = new Job(all.size() + 1, work, delay, period);
        all.add(job);
        queue.add(job);
        if (queue == immediate) readyImmediate.add(job);
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(job.id);
        when(task.isCancelled()).thenAnswer(call -> job.cancelled);
        return task;
    }

    public static final class Job {
        public final int id;
        public final long delay;
        public final long period;
        public boolean cancelled;
        private final Runnable work;

        private Job(int id, Runnable work, long delay, long period) {
            this.id = id;
            this.work = work;
            this.delay = delay;
            this.period = period;
        }

        public void run() {
            if (!cancelled) work.run();
        }
    }
}

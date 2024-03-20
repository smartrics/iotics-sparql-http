package smartrics.iotics.sparqlhttp;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledExecutorTimerManager implements RunnerConcurrencyManager {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final long timeoutSeconds;
    private final Runnable timeoutTask;
    private ScheduledFuture<?> futureTimeout = null;

    public ScheduledExecutorTimerManager(long timeoutSeconds, Runnable timeoutTask) {
        this.timeoutSeconds = timeoutSeconds;
        this.timeoutTask = timeoutTask;
    }

    @Override
    public void scheduleTimeout(Runnable task, long delaySeconds) {
        if (futureTimeout != null && !futureTimeout.isDone()) {
            futureTimeout.cancel(false);
        }
        futureTimeout = scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }

    @Override
    public void resetTimeout() {
        scheduleTimeout(timeoutTask, timeoutSeconds);
    }

    @Override
    public void shutdownNow() {
        scheduler.shutdownNow();
    }
}

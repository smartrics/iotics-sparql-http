package smartrics.iotics.sparqlhttp;

public interface RunnerConcurrencyManager {
    void scheduleTimeout(Runnable task, long delaySeconds);
    void resetTimeout();
    void shutdownNow();
}

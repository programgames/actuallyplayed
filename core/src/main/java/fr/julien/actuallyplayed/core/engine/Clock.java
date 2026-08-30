package fr.julien.actuallyplayed.core.engine;

/**
 * Source of time for the whole tracking engine.
 * <p>
 * The engine never calls {@link System#currentTimeMillis()} directly: time is always
 * injected through this interface. This is what makes the AFK rules testable — a test
 * can simulate five minutes of inactivity instantly instead of waiting for them.
 */
public interface Clock {

    /**
     * Wall-clock time in milliseconds since the Unix epoch.
     * <p>
     * Wall-clock is deliberate rather than a monotonic counter: sessions are stamped with
     * real dates so the history and the monthly aggregates stay meaningful.
     */
    long currentTimeMillis();
}

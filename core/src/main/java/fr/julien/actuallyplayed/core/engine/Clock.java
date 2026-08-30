package fr.julien.actuallyplayed.core.engine;

/**
 * Source of time for the whole tracking engine.
 * <p>
 * The engine never calls {@link System#currentTimeMillis()} directly: time is always
 * injected through this interface. This is what makes the AFK rules testable — a test can
 * simulate five minutes of inactivity instantly instead of waiting for them.
 *
 * <h3>Two clocks, because they answer different questions</h3>
 * "What time is it?" and "how much time has passed?" look like the same question and are
 * not. The wall clock can be moved — by an NTP correction, by hand, by the RTC skew of a
 * dual-boot machine — and every duration derived from it moves with it.
 * <p>
 * Measuring durations against the wall clock let a one-hour forward jump credit a player
 * with an hour they never played: five real minutes were recorded as sixty-five. Inventing
 * playtime is the worst failure available to a mod whose entire product is the measurement.
 * <p>
 * So durations come from {@link #elapsedMillis()}, which only ever moves forward, and the
 * wall clock is kept for the one thing it alone can answer: the dates stamped on sessions.
 */
public interface Clock {

    /**
     * Wall-clock time in milliseconds since the Unix epoch, for timestamps that are stored
     * and displayed — when a session began, when it ended.
     * <p>
     * Never use this to measure how long something took.
     */
    long currentTimeMillis();

    /**
     * A monotonic counter in milliseconds, for measuring durations.
     * <p>
     * Its origin is arbitrary and meaningless; only differences between two readings carry
     * information. It is unaffected by anything that changes the system clock.
     */
    long elapsedMillis();
}

package fr.julien.actuallyplayed.core.engine;

import java.util.concurrent.TimeUnit;

/**
 * Production {@link Clock}: the system clock for dates, {@link System#nanoTime()} for
 * durations.
 */
public final class SystemClock implements Clock {

    public static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * {@code System.nanoTime()} is the JVM's monotonic source: unrelated to any notion of
     * date, unaffected by the system clock being changed, and guaranteed never to go
     * backwards on any platform that matters.
     */
    @Override
    public long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}

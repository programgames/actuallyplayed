package fr.julien.playtimetracker.core.engine;

/**
 * Production {@link Clock}, backed by the system clock.
 */
public final class SystemClock implements Clock {

    public static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}

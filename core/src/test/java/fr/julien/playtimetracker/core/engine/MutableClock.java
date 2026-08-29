package fr.julien.playtimetracker.core.engine;

/**
 * Test {@link Clock} whose time only moves when a test tells it to.
 * <p>
 * Every engine test drives time through this class, so AFK thresholds, retroactive
 * rollbacks and retention windows can be exercised in microseconds.
 */
public final class MutableClock implements Clock {

    private long now;

    public MutableClock(long startMillis) {
        this.now = startMillis;
    }

    @Override
    public long currentTimeMillis() {
        return now;
    }

    /** Moves time forward. Negative values are rejected: time never goes backwards here. */
    public void advanceMillis(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Cannot advance time by a negative amount: " + millis);
        }
        now += millis;
    }

    /**
     * Sets the time outright, backwards included — the only way to reproduce an NTP
     * correction, a daylight-saving shift or a user changing the system clock.
     */
    public void setTimeMillis(long millis) {
        now = millis;
    }

    public void advanceSeconds(long seconds) {
        advanceMillis(seconds * 1000L);
    }

    public void advanceMinutes(long minutes) {
        advanceMillis(minutes * 60_000L);
    }
}

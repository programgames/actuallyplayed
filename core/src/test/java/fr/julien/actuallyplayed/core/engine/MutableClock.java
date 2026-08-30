package fr.julien.actuallyplayed.core.engine;

/**
 * Test {@link Clock} whose time only moves when a test tells it to.
 * <p>
 * Every engine test drives time through this class, so AFK thresholds, retroactive
 * rollbacks and retention windows can be exercised in microseconds.
 * <p>
 * It keeps the two clocks separate on purpose. {@link #advanceMillis} moves both, the way
 * real time does. {@link #setTimeMillis} moves <em>only</em> the wall clock, which is
 * exactly what an NTP correction or a manual change does — and is the scenario that used to
 * credit a player with an hour they never played.
 */
public final class MutableClock implements Clock {

    private long wallMillis;
    private long elapsedMillis;

    public MutableClock(long startMillis) {
        this.wallMillis = startMillis;
        // Deliberately unrelated to the wall clock: nothing may assume the two share an
        // origin, because in production they do not.
        this.elapsedMillis = 5_000_000L;
    }

    @Override
    public long currentTimeMillis() {
        return wallMillis;
    }

    @Override
    public long elapsedMillis() {
        return elapsedMillis;
    }

    /** Real time passing: both clocks move together. */
    public void advanceMillis(long millis) {
        if (millis < 0L) {
            throw new IllegalArgumentException("Cannot advance time by a negative amount: " + millis);
        }
        wallMillis += millis;
        elapsedMillis += millis;
    }

    /**
     * Moves the wall clock without any real time passing — an NTP correction, a manual
     * change, a dual-boot RTC skew. Forwards or backwards; the monotonic clock is untouched,
     * because in reality nothing happened.
     */
    public void setTimeMillis(long millis) {
        wallMillis = millis;
    }

    public void advanceSeconds(long seconds) {
        advanceMillis(seconds * 1000L);
    }

    public void advanceMinutes(long minutes) {
        advanceMillis(minutes * 60_000L);
    }
}

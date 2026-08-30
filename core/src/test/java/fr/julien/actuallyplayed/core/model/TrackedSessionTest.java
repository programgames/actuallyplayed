package fr.julien.actuallyplayed.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A session is the unit everything else sums, so it refuses to exist in a state those sums
 * could not survive.
 */
public class TrackedSessionTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");

    private static TrackedSession session(long start, long end, long active, long afk) {
        return new TrackedSession(PLAYER, SERVER, start, end, active, afk);
    }

    @Test
    public void sumsItsTwoBuckets() {
        assertEquals(90_000L, session(0L, 90_000L, 60_000L, 30_000L).getTotalMillis());
    }

    @Test
    public void reportsTheShareActuallyPlayed() {
        assertEquals(2.0d / 3.0d, session(0L, 90_000L, 60_000L, 30_000L).getActiveRatio(), 0.0001d);
    }

    @Test
    public void anEmptySessionHasNoRatioRatherThanADivisionByZero() {
        assertEquals(0.0d, session(0L, 0L, 0L, 0L).getActiveRatio(), 0.0001d);
    }

    @Test
    public void acceptsAccountedTimeExceedingTheSpanBetweenItsTimestamps() {
        // Not a contradiction: a clock stepping backwards mid-session leaves more time
        // charged than the two timestamps span. Rejecting it would throw away real sessions
        // from the very users the rollback fix was written for.
        TrackedSession session = session(1000L, 2000L, 60_000L, 0L);

        assertEquals(60_000L, session.getTotalMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnEndBeforeItsStart() {
        session(2000L, 1000L, 0L, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativePlayedTime() {
        session(0L, 1000L, -1L, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeAfkTime() {
        session(0L, 1000L, 0L, -1L);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsAMissingPlayer() {
        new TrackedSession(null, SERVER, 0L, 1000L, 0L, 0L);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsAMissingTarget() {
        new TrackedSession(PLAYER, null, 0L, 1000L, 0L, 0L);
    }
}

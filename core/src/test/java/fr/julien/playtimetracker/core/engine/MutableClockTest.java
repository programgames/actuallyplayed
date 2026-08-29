package fr.julien.playtimetracker.core.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Smoke test proving the test harness itself is wired up, and that time injection
 * behaves as the engine tests will rely on.
 */
public class MutableClockTest {

    @Test
    public void advancesTimeByTheRequestedAmount() {
        MutableClock clock = new MutableClock(1_000L);

        clock.advanceMinutes(5);

        assertEquals(1_000L + 300_000L, clock.currentTimeMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToGoBackwards() {
        new MutableClock(0L).advanceMillis(-1L);
    }
}

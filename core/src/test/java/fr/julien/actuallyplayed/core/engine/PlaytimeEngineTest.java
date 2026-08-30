package fr.julien.actuallyplayed.core.engine;

import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.model.TrackedSession;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaytimeEngineTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");

    private static final long MINUTE = 60_000L;
    private static final long SECOND = 1_000L;

    private MutableClock clock;
    private AtomicReference<PlaytimeConfig> config;
    private PlaytimeEngine engine;

    @Before
    public void setUp() {
        clock = new MutableClock(1_700_000_000_000L);
        config = new AtomicReference<PlaytimeConfig>(PlaytimeConfig.defaults());
        engine = new PlaytimeEngine(clock, config::get);
    }

    /** Advances time in one-minute steps, signalling activity at each step. */
    private void playActively(long minutes) {
        for (long i = 0; i < minutes; i++) {
            clock.advanceMinutes(1);
            engine.onActivity();
        }
    }

    // --- session lifecycle -------------------------------------------------------

    @Test
    public void discardsSessionsShorterThanTheMinimum() {
        engine.beginSession(PLAYER, SERVER);
        clock.advanceSeconds(29);

        assertFalse("a 29s session must leave no trace at all", engine.endSession().isPresent());
    }

    @Test
    public void keepsSessionExactlyAtTheMinimum() {
        engine.beginSession(PLAYER, SERVER);
        clock.advanceSeconds(30);

        Optional<TrackedSession> session = engine.endSession();

        assertTrue("30s is 'not shorter than 30s' and must be kept", session.isPresent());
        assertEquals(30 * SECOND, session.get().getTotalMillis());
    }

    @Test
    public void countsUninterruptedPlayAsFullyActive() {
        engine.beginSession(PLAYER, SERVER);
        playActively(10);

        TrackedSession session = engine.endSession().get();

        assertEquals(10 * MINUTE, session.getActiveMillis());
        assertEquals(0L, session.getAfkMillis());
        assertEquals(1.0d, session.getActiveRatio(), 0.0001d);
    }

    @Test(expected = IllegalStateException.class)
    public void refusesToOpenTwoSessionsAtOnce() {
        engine.beginSession(PLAYER, SERVER);
        engine.beginSession(PLAYER, TargetKey.singleplayer("New World"));
    }

    @Test
    public void endingWithoutSessionIsHarmless() {
        assertFalse(engine.endSession().isPresent());
    }

    // --- the retroactive rollback, the core rule ---------------------------------

    @Test
    public void movesTheThresholdWindowFromActiveToAfk() {
        engine.beginSession(PLAYER, SERVER);
        playActively(10);

        // Five idle minutes: charged to "active" as they elapse, then taken back.
        clock.advanceMinutes(5);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.AFK, snapshot.getState());
        assertEquals("the 5 idle minutes must not be credited as playtime",
                10 * MINUTE, snapshot.getActiveMillis());
        assertEquals(5 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void doesNotRollBackTwiceWhileStayingAfk() {
        engine.beginSession(PLAYER, SERVER);
        playActively(10);
        clock.advanceMinutes(5);
        engine.tick();

        clock.advanceMinutes(3);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(10 * MINUTE, snapshot.getActiveMillis());
        assertEquals(8 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void resumesCountingImmediatelyOnActivity() {
        engine.beginSession(PLAYER, SERVER);
        playActively(10);
        clock.advanceMinutes(5);
        engine.tick();

        engine.onActivity();
        playActively(2);

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.ACTIVE, snapshot.getState());
        assertEquals(12 * MINUTE, snapshot.getActiveMillis());
        assertEquals(5 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void staysActiveJustBeforeTheThreshold() {
        engine.beginSession(PLAYER, SERVER);
        playActively(10);

        clock.advanceMillis(5 * MINUTE - 1);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.ACTIVE, snapshot.getState());
        assertEquals(10 * MINUTE + 5 * MINUTE - 1, snapshot.getActiveMillis());
        assertEquals(0L, snapshot.getAfkMillis());
    }

    @Test
    public void rollsBackEvenWhenTheGameFroze() {
        // A single tick covering far more than the threshold, as after a long stall.
        engine.beginSession(PLAYER, SERVER);
        playActively(10);

        clock.advanceMinutes(30);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals("the whole idle stretch belongs to AFK, not just the threshold",
                10 * MINUTE, snapshot.getActiveMillis());
        assertEquals(30 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void appliesTheRollbackWhenTheSessionEndsWhileIdle() {
        engine.beginSession(PLAYER, SERVER);
        playActively(10);
        clock.advanceMinutes(7);

        TrackedSession session = engine.endSession().get();

        assertEquals(10 * MINUTE, session.getActiveMillis());
        assertEquals(7 * MINUTE, session.getAfkMillis());
    }

    @Test
    public void honoursACustomThreshold() {
        config.set(config.get().toBuilder().afkThresholdMillis(MINUTE).build());
        engine.beginSession(PLAYER, SERVER);
        playActively(3);

        clock.advanceMinutes(1);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.AFK, snapshot.getState());
        assertEquals(3 * MINUTE, snapshot.getActiveMillis());
        assertEquals(MINUTE, snapshot.getAfkMillis());
    }

    // --- window focus ------------------------------------------------------------

    @Test
    public void losingFocusPausesImmediatelyWithoutWaitingForTheThreshold() {
        engine.beginSession(PLAYER, SERVER);
        playActively(2);

        clock.advanceSeconds(30);
        engine.onWindowFocusChanged(false);

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.AFK, snapshot.getState());
        assertEquals("the idle tail before alt-tab is rolled back too",
                2 * MINUTE, snapshot.getActiveMillis());
        assertEquals(30 * SECOND, snapshot.getAfkMillis());
    }

    @Test
    public void losingFocusMidActionRollsBackNothing() {
        engine.beginSession(PLAYER, SERVER);
        playActively(2);

        // Alt-tab in the very instant of an action: no idle time to take back.
        engine.onWindowFocusChanged(false);

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(2 * MINUTE, snapshot.getActiveMillis());
        assertEquals(0L, snapshot.getAfkMillis());
    }

    @Test
    public void timeSpentUnfocusedIsAllAfk() {
        engine.beginSession(PLAYER, SERVER);
        playActively(2);
        engine.onWindowFocusChanged(false);

        clock.advanceMinutes(20);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(2 * MINUTE, snapshot.getActiveMillis());
        assertEquals(20 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void activityWhileUnfocusedDoesNotResumeCounting() {
        engine.beginSession(PLAYER, SERVER);
        playActively(2);
        engine.onWindowFocusChanged(false);

        clock.advanceMinutes(5);
        engine.onActivity();
        clock.advanceMinutes(5);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.AFK, snapshot.getState());
        assertEquals(2 * MINUTE, snapshot.getActiveMillis());
        assertEquals(10 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void regainingFocusResumesCounting() {
        engine.beginSession(PLAYER, SERVER);
        playActively(2);
        engine.onWindowFocusChanged(false);
        clock.advanceMinutes(20);

        engine.onWindowFocusChanged(true);
        playActively(3);

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.ACTIVE, snapshot.getState());
        assertEquals(5 * MINUTE, snapshot.getActiveMillis());
        assertEquals(20 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void repeatedFocusEventsAreIdempotent() {
        engine.beginSession(PLAYER, SERVER);
        playActively(2);

        engine.onWindowFocusChanged(true);
        clock.advanceMinutes(1);
        engine.onWindowFocusChanged(true);

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.ACTIVE, snapshot.getState());
        assertEquals(3 * MINUTE, snapshot.getActiveMillis());
        assertEquals(0L, snapshot.getAfkMillis());
    }

    // --- robustness --------------------------------------------------------------

    @Test
    public void aForwardWallClockJumpInventsNoPlaytime() {
        // The failure this guards against: an NTP correction on a machine whose clock was
        // running slow moves the wall clock forward by an hour. Measuring durations against
        // that clock credited the player with the whole hour — five real minutes of play
        // were recorded as sixty-five. Inventing playtime is the worst thing a mod whose
        // product is the measurement can do.
        engine.beginSession(PLAYER, SERVER);
        playActively(5);

        clock.setTimeMillis(clock.currentTimeMillis() + 60 * MINUTE);
        engine.onActivity();
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals("an hour that never elapsed must not be counted",
                5 * MINUTE, snapshot.getActiveMillis());
        assertEquals(0L, snapshot.getAfkMillis());
    }

    @Test
    public void aBackwardWallClockJumpChangesNothing() {
        engine.beginSession(PLAYER, SERVER);
        playActively(5);

        clock.setTimeMillis(clock.currentTimeMillis() - 10 * MINUTE);
        engine.tick();
        // And real time keeps being counted correctly afterwards.
        playActively(3);

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(8 * MINUTE, snapshot.getActiveMillis());
        assertEquals(0L, snapshot.getAfkMillis());
    }

    @Test
    public void aSessionIsStampedWithWallClockDatesNotMonotonicOnes() {
        // The two clocks deliberately share no origin, so a session whose timestamps came
        // from the monotonic counter would be dated somewhere around 1970.
        long startedAt = clock.currentTimeMillis();
        engine.beginSession(PLAYER, SERVER);
        playActively(2);

        TrackedSession session = engine.endSession().get();

        assertEquals(startedAt, session.getStartedAt());
        assertEquals(startedAt + 2 * MINUTE, session.getEndedAt());
    }

    @Test
    public void rollsBackEverythingChargedEvenWhenTheClockJumpedBackMidIdle() {
        // Regression, from two angles. The rollback used to be measured as
        // (now - lastActivityAt), which came out shorter than what had actually been charged
        // when the clock regressed mid-idle, leaving the excess counted as playtime. The
        // engine now counts what it charges, and measures durations against a monotonic
        // clock, so a wall-clock jump in the middle of an idle stretch is a non-event.
        engine.beginSession(PLAYER, SERVER);
        playActively(5);

        clock.advanceMinutes(2);
        engine.tick();

        clock.setTimeMillis(clock.currentTimeMillis() - MINUTE);
        engine.tick();

        clock.advanceMinutes(4);
        engine.tick();

        SessionSnapshot snapshot = engine.snapshot().get();
        assertEquals(ActivityState.AFK, snapshot.getState());
        assertEquals("only the minutes actually played may remain active",
                5 * MINUTE, snapshot.getActiveMillis());
        assertEquals("every charged idle millisecond must land in AFK",
                6 * MINUTE, snapshot.getAfkMillis());
    }

    @Test
    public void tickResolutionDoesNotChangeTotals() {
        engine.beginSession(PLAYER, SERVER);
        // Many small ticks with no activity: must land exactly like a single big one.
        for (int i = 0; i < 600; i++) {
            clock.advanceSeconds(1);
            engine.tick();
        }

        TrackedSession session = engine.endSession().get();

        assertEquals(0L, session.getActiveMillis());
        assertEquals(10 * MINUTE, session.getAfkMillis());
    }

    @Test
    public void snapshotIsEmptyWithoutSession() {
        assertFalse(engine.snapshot().isPresent());
        assertFalse(engine.isSessionActive());
    }
}

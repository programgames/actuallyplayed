package fr.julien.playtimetracker.core;

import fr.julien.playtimetracker.core.config.PlaytimeConfig;
import fr.julien.playtimetracker.core.engine.MutableClock;
import fr.julien.playtimetracker.core.model.PlaytimeData;
import fr.julien.playtimetracker.core.model.ProvisionalSession;
import fr.julien.playtimetracker.core.model.TargetKey;
import fr.julien.playtimetracker.core.model.TrackedSession;
import fr.julien.playtimetracker.core.storage.PlaytimeCodec;
import fr.julien.playtimetracker.core.storage.PlaytimeRepository;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlaytimeTrackerTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");
    private static final long MINUTE = 60_000L;
    private static final long START = 1_700_000_000_000L;

    /** In-memory store that round-trips through the real codec, so serialisation is exercised too. */
    private static final class MemoryRepository implements PlaytimeRepository {

        private String document;
        int saveCount;
        IOException failWith;

        @Override
        public PlaytimeData load() {
            if (document == null) {
                return new PlaytimeData();
            }
            return PlaytimeCodec.read(new com.google.gson.JsonParser().parse(document).getAsJsonObject());
        }

        @Override
        public void save(PlaytimeData data) throws IOException {
            if (failWith != null) {
                throw failWith;
            }
            document = PlaytimeCodec.write(data).toString();
            saveCount++;
        }
    }

    private MutableClock clock;
    private PlaytimeConfig config;
    private MemoryRepository repository;
    private PlaytimeTracker tracker;

    @Before
    public void setUp() throws IOException {
        clock = new MutableClock(START);
        config = PlaytimeConfig.defaults();
        repository = new MemoryRepository();
        tracker = new PlaytimeTracker(repository, config, clock, java.time.ZoneId.of("Europe/Paris"));
        tracker.start();
    }

    private void playActively(long minutes) {
        for (long i = 0; i < minutes; i++) {
            clock.advanceMinutes(1);
            tracker.onActivity();
            tracker.tick();
        }
    }

    // --- normal flow ---------------------------------------------------------------

    @Test
    public void recordsAndPersistsAClosedSession() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        playActively(10);
        tracker.endSession();

        PlaytimeData reloaded = repository.load();
        assertEquals(10 * MINUTE, reloaded.player(PLAYER).getTotalActiveMillis());
        assertEquals("Hypixel", reloaded.player(PLAYER).find(SERVER).getDisplayName());
        assertNull("a clean close leaves nothing to recover", reloaded.getInProgress());
    }

    @Test
    public void doesNotPersistASessionBelowTheMinimum() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        clock.advanceSeconds(20);
        tracker.endSession();

        assertTrue(repository.load().isEmpty());
    }

    // --- autosave and crash recovery -------------------------------------------------

    @Test
    public void autosaveSnapshotsTheSessionInProgress() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        playActively(3);

        ProvisionalSession inProgress = repository.load().getInProgress();

        assertNotNull("an open session must be recoverable after a crash", inProgress);
        assertEquals(PLAYER, inProgress.getPlayerUuid());
        assertEquals(SERVER, inProgress.getTarget());
        assertEquals("Hypixel", inProgress.getDisplayName());
        assertEquals(3 * MINUTE, inProgress.getActiveMillis());
    }

    @Test
    public void doesNotAutosaveMoreOftenThanTheInterval() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        int before = repository.saveCount;

        for (int i = 0; i < 50; i++) {
            clock.advanceSeconds(1);
            tracker.tick();
        }

        assertEquals("50 seconds must not trigger a 60-second autosave", before, repository.saveCount);
    }

    @Test
    public void recoversTheSessionLeftByACrash() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        playActively(45);
        // No endSession(): the process died here.

        PlaytimeTracker afterCrash = new PlaytimeTracker(
                repository, config, clock, java.time.ZoneId.of("Europe/Paris"));
        PlaytimeTracker.StartupReport report = afterCrash.start();

        TrackedSession recovered = report.getRecoveredSession();
        assertNotNull("a crash must not swallow 45 minutes of play", recovered);
        assertEquals(45 * MINUTE, recovered.getActiveMillis());
        assertEquals(45 * MINUTE, afterCrash.getData().player(PLAYER).getTotalActiveMillis());
    }

    @Test
    public void crashRecoveryLosesAtMostTheAutosaveInterval() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        playActively(10);
        // Play on for another 59 seconds, just short of the next autosave, then die.
        clock.advanceSeconds(59);
        tracker.onActivity();
        tracker.tick();

        PlaytimeTracker afterCrash = new PlaytimeTracker(
                repository, config, clock, java.time.ZoneId.of("Europe/Paris"));
        TrackedSession recovered = afterCrash.start().getRecoveredSession();

        assertEquals("only the seconds since the last autosave are lost",
                10 * MINUTE, recovered.getActiveMillis());
    }

    @Test
    public void discardsARecoveredSessionThatIsTooShort() throws IOException {
        PlaytimeData data = new PlaytimeData();
        data.setInProgress(new ProvisionalSession(
                PLAYER, SERVER, "Hypixel", START, START + 10_000L, 10_000L, 0L));
        repository.save(data);

        PlaytimeTracker fresh = new PlaytimeTracker(
                repository, config, clock, java.time.ZoneId.of("Europe/Paris"));
        PlaytimeTracker.StartupReport report = fresh.start();

        assertNull("a crash 10 seconds in leaves no trace, like a clean 10-second visit",
                report.getRecoveredSession());
        assertTrue(fresh.getData().isEmpty());
    }

    @Test
    public void clearsTheProvisionalRecordAfterRecovery() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        playActively(45);

        new PlaytimeTracker(repository, config, clock, java.time.ZoneId.of("Europe/Paris")).start();

        assertNull("the same session must not be recovered twice", repository.load().getInProgress());

        PlaytimeTracker third = new PlaytimeTracker(
                repository, config, clock, java.time.ZoneId.of("Europe/Paris"));
        assertNull(third.start().getRecoveredSession());
        assertEquals(45 * MINUTE, third.getData().player(PLAYER).getTotalActiveMillis());
    }

    @Test
    public void appliesTheAfkRollbackToARecoveredSession() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        playActively(10);
        // Walk away and crash while AFK.
        clock.advanceMinutes(20);
        tracker.tick();

        PlaytimeTracker afterCrash = new PlaytimeTracker(
                repository, config, clock, java.time.ZoneId.of("Europe/Paris"));
        TrackedSession recovered = afterCrash.start().getRecoveredSession();

        assertEquals("AFK time must stay AFK through a crash", 10 * MINUTE, recovered.getActiveMillis());
        assertEquals(20 * MINUTE, recovered.getAfkMillis());
    }

    @Test
    public void saveNowCapturesTheSessionInProgress() throws IOException {
        tracker.beginSession(PLAYER, SERVER, "Hypixel");
        clock.advanceSeconds(40);
        tracker.tick();

        tracker.saveNow();

        assertNotNull(repository.load().getInProgress());
    }

    // --- resilience ------------------------------------------------------------------

    @Test
    public void aFailingSaveNeverBreaksTheGameLoop() {
        repository.failWith = new IOException("disk full");
        tracker.beginSession(PLAYER, SERVER, "Hypixel");

        playActively(3);

        IOException failure = tracker.consumeSaveFailure();
        assertNotNull("the failure must be reported, not swallowed", failure);
        assertEquals("disk full", failure.getMessage());
        assertNull("and reported only once", tracker.consumeSaveFailure());
        assertTrue("tracking carries on regardless", tracker.isSessionActive());
    }

    @Test
    public void startsCleanWhenNothingWasStored() throws IOException {
        PlaytimeTracker fresh = new PlaytimeTracker(
                repository, config, clock, java.time.ZoneId.of("Europe/Paris"));
        PlaytimeTracker.StartupReport report = fresh.start();

        assertNull(report.getRecoveredSession());
        assertEquals(0, report.getCompactedSessions());
        assertEquals("an untouched file must not be rewritten on startup", 0, repository.saveCount);
        assertFalse(fresh.isSessionActive());
    }
}

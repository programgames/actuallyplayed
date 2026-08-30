package fr.julien.actuallyplayed.core;

import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import fr.julien.actuallyplayed.core.engine.Clock;
import fr.julien.actuallyplayed.core.engine.PlaytimeEngine;
import fr.julien.actuallyplayed.core.engine.RetentionPolicy;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.model.PlaytimeData;
import fr.julien.actuallyplayed.core.model.ProvisionalSession;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.model.TrackedSession;
import fr.julien.actuallyplayed.core.storage.PlaytimeRepository;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * Ties the engine, the store and the retention policy together.
 * <p>
 * This is the whole API the Minecraft layer talks to. Keeping the orchestration here — not
 * in the Forge code — means the interesting behaviour (crash recovery, autosave timing,
 * what happens when a save fails) stays testable without launching a game.
 * <p>
 * <h3>Threading</h3>
 * Every public method is synchronised on this instance. Almost all calls come from the
 * Minecraft client thread, but the shutdown hook that flushes the session on exit runs on
 * a thread of its own, concurrently with a game loop that may still be ticking. Without
 * this lock, that final save could interleave with an accrual and record a torn state.
 */
public final class PlaytimeTracker {

    private final PlaytimeRepository repository;
    private final Clock clock;
    private final ZoneId zone;
    private final PlaytimeEngine engine;

    /**
     * Swapped wholesale when the player edits the settings. Volatile rather than guarded by
     * the instance lock, because the engine reads it outside that lock on every tick.
     */
    private volatile PlaytimeConfig config;

    private PlaytimeData data = new PlaytimeData();
    private String currentDisplayName;
    private long lastSaveAt;
    private IOException lastSaveFailure;

    public PlaytimeTracker(PlaytimeRepository repository, PlaytimeConfig config, Clock clock, ZoneId zone) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.engine = new PlaytimeEngine(clock, this::getConfig);
    }

    /**
     * Loads the stored data, recovers a session left behind by a crash, and compacts
     * anything past the retention window.
     *
     * @return what happened, for the caller to log
     */
    public synchronized StartupReport start() throws IOException {
        data = repository.load();
        lastSaveAt = clock.elapsedMillis();

        TrackedSession recovered = recoverInterruptedSession();
        int compacted = new RetentionPolicy(config.getRetentionDays(), zone)
                .compact(data, clock.currentTimeMillis());

        // Only rewrite when something actually changed, to avoid touching the file on
        // every launch for nothing.
        if (recovered != null || compacted > 0) {
            repository.save(data);
        }
        return new StartupReport(recovered, compacted);
    }

    /**
     * Turns a provisional session left by a crash into a real one.
     * <p>
     * The minimum-length rule applies here too: a game that crashed ten seconds after
     * joining leaves no trace, exactly as a clean ten-second visit would.
     */
    private TrackedSession recoverInterruptedSession() {
        ProvisionalSession provisional = data.getInProgress();
        data.setInProgress(null);
        if (provisional == null) {
            return null;
        }
        if (provisional.getTotalMillis() < config.getMinSessionMillis()) {
            return null;
        }
        TrackedSession session = provisional.toSession();
        data.record(session, provisional.getDisplayName());
        return session;
    }

    public synchronized void beginSession(String playerUuid, TargetKey target, String displayName) {
        currentDisplayName = displayName;
        engine.beginSession(playerUuid, target);
    }

    /**
     * Closes the session, records it if it is long enough, and saves immediately —
     * a disconnect is precisely when the player expects their time to be banked.
     */
    public synchronized Optional<TrackedSession> endSession() {
        Optional<TrackedSession> finished = engine.endSession();
        if (finished.isPresent()) {
            data.record(finished.get(), currentDisplayName);
        }
        // Cleared whether or not the session was kept: the game is exiting this world
        // cleanly, so there is nothing left to recover.
        data.setInProgress(null);
        currentDisplayName = null;
        trySave();
        return finished;
    }

    /**
     * Forgets everything recorded about the destination the player is currently on, and
     * restarts the running session from zero.
     * <p>
     * The running session is discarded rather than recorded: a player asking to reset does
     * not want the minutes they just spent typing the command to survive it. Other
     * destinations are untouched — this deliberately cannot wipe everything, because a
     * single command that erases months of history is a command someone will run by
     * accident.
     *
     * @return the destination that was reset, or empty if the player is not in a world
     */
    public synchronized Optional<TargetKey> resetCurrentTarget() {
        Optional<SessionSnapshot> snapshot = engine.snapshot();
        if (!snapshot.isPresent()) {
            return Optional.empty();
        }

        SessionSnapshot current = snapshot.get();
        String playerUuid = current.getPlayerUuid();
        TargetKey target = current.getTarget();
        String displayName = currentDisplayName;

        engine.endSession();
        PlayerPlaytime player = data.find(playerUuid);
        if (player != null) {
            player.remove(target);
        }
        data.setInProgress(null);

        engine.beginSession(playerUuid, target);
        currentDisplayName = displayName;
        trySave();
        return Optional.of(target);
    }

    public synchronized void onActivity() {
        engine.onActivity();
    }

    public synchronized void onWindowFocusChanged(boolean focused) {
        engine.onWindowFocusChanged(focused);
    }

    /** Advances the accounting and autosaves when due. Never throws. */
    public synchronized void tick() {
        engine.tick();

        // The autosave interval is a duration, so it is measured against the monotonic
        // clock: a system-clock change must not skip or storm the autosave.
        long now = clock.elapsedMillis();
        if (now - lastSaveAt < config.getAutosaveIntervalMillis()) {
            return;
        }
        lastSaveAt = now;
        captureInProgress();
        trySave();
    }

    /** Writes the session in progress into the data, so a crash cannot swallow it. */
    private void captureInProgress() {
        Optional<SessionSnapshot> snapshot = engine.snapshot();
        if (!snapshot.isPresent()) {
            data.setInProgress(null);
            return;
        }
        SessionSnapshot current = snapshot.get();
        data.setInProgress(new ProvisionalSession(
                current.getPlayerUuid(),
                current.getTarget(),
                currentDisplayName,
                current.getStartedAt(),
                clock.currentTimeMillis(),
                current.getActiveMillis(),
                current.getAfkMillis()));
    }

    /** Saves right now, snapshotting the session in progress. Used when the game closes. */
    public synchronized void saveNow() throws IOException {
        captureInProgress();
        lastSaveAt = clock.elapsedMillis();
        repository.save(data);
    }

    private void trySave() {
        try {
            repository.save(data);
        } catch (IOException e) {
            // A failed save must never break the game loop. The failure is kept for the
            // Minecraft layer to log once, and the next autosave will try again.
            lastSaveFailure = e;
        }
    }

    /** @return the last save failure, clearing it, or {@code null} if saving is healthy */
    public synchronized IOException consumeSaveFailure() {
        IOException failure = lastSaveFailure;
        lastSaveFailure = null;
        return failure;
    }

    public synchronized Optional<SessionSnapshot> snapshot() {
        return engine.snapshot();
    }

    public synchronized boolean isSessionActive() {
        return engine.isSessionActive();
    }

    public synchronized PlaytimeData getData() {
        return data;
    }

    public PlaytimeConfig getConfig() {
        return config;
    }

    /**
     * Publishes a new configuration. The change is visible to the engine on its next read,
     * with no restart and no window in which a half-updated setting could be observed.
     */
    public void setConfig(PlaytimeConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** What {@link #start()} found and did. */
    public static final class StartupReport {

        private final TrackedSession recoveredSession;
        private final int compactedSessions;

        StartupReport(TrackedSession recoveredSession, int compactedSessions) {
            this.recoveredSession = recoveredSession;
            this.compactedSessions = compactedSessions;
        }

        /** A session salvaged from a crashed run, or {@code null} if the last exit was clean. */
        public TrackedSession getRecoveredSession() {
            return recoveredSession;
        }

        public int getCompactedSessions() {
            return compactedSessions;
        }
    }
}

package fr.julien.actuallyplayed.core.engine;

import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.model.TrackedSession;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Splits wall-clock time into "actually played" and "AFK" for the session in progress.
 *
 * <h3>Two clocks</h3>
 * Durations are measured against {@link Clock#elapsedMillis()}, which only moves forward.
 * The wall clock is used for one thing only: the dates stamped on a stored session. Reading
 * durations from the wall clock let a forward jump — an NTP correction on a machine whose
 * clock was running slow — credit a player with time they never played.
 *
 * <h3>Accounting model</h3>
 * The engine holds two counters and charges every elapsed millisecond to exactly one of
 * them. Time is only accounted when the engine is driven — by {@link #tick()} or by any
 * other public method — so a frozen game never invents time it did not observe.
 *
 * <h3>Retroactive rollback</h3>
 * The subtle part. Elapsed time is charged to the active counter as it happens, because
 * the engine cannot know yet whether the player has stepped away. Once inactivity reaches
 * the configured threshold, that optimistic charge is undone: the idle span is moved from
 * the active counter to the AFK counter. The five minutes that triggered the AFK state are
 * therefore never counted as playtime.
 *
 * <p>Losing window focus applies the same rollback immediately, without waiting for the
 * threshold: alt-tabbing is unambiguous evidence the player is not playing.
 *
 * <p>The amount to take back is <em>counted as it is charged</em>, never re-derived from a
 * subtraction of two wall-clock readings. Those two quantities agree only while the clock
 * moves forward, and the clock does not always move forward: an NTP correction, a manual
 * change, or the RTC skew of a dual-boot machine can pull it backwards mid-session. When
 * that happened, the rollback window measured by subtraction came out shorter than what had
 * actually been charged, and the difference stayed misfiled as playtime.
 *
 * <h3>Threading</h3>
 * Not thread-safe. Every call is expected to come from the Minecraft client thread.
 */
public final class PlaytimeEngine {

    private final Clock clock;
    private final Supplier<PlaytimeConfig> config;

    private Session session;

    /**
     * @param config read on every use rather than captured once, so a setting changed from
     *               the in-game screen takes effect immediately
     */
    public PlaytimeEngine(Clock clock, Supplier<PlaytimeConfig> config) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Opens a session on the given target.
     *
     * @throws IllegalStateException if a session is already open — the caller must close
     *                               the previous one, otherwise its time would be lost
     *                               silently, which is exactly the bug this mod exists to
     *                               avoid.
     */
    public void beginSession(String playerUuid, TargetKey target) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(target, "target");
        if (session != null) {
            throw new IllegalStateException(
                    "A session is already open on " + session.target + "; close it before opening " + target);
        }
        session = new Session(playerUuid, target,
                clock.currentTimeMillis(), clock.elapsedMillis());
    }

    /**
     * Closes the session in progress.
     *
     * @return the finished session, or empty if no session was open or if it was shorter
     *         than {@link PlaytimeConfig#getMinSessionMillis()} and was therefore discarded
     */
    public Optional<TrackedSession> endSession() {
        if (session == null) {
            return Optional.empty();
        }

        long now = clock.elapsedMillis();
        // Account the tail and apply any pending rollback, so a session that ended after
        // a long idle stretch is not credited with that stretch.
        accrue(now);
        applyAfkThreshold(now);

        Session finished = session;
        session = null;

        long total = finished.activeMillis + finished.afkMillis;
        if (total < config.get().getMinSessionMillis()) {
            return Optional.empty();
        }

        return Optional.of(new TrackedSession(
                finished.playerUuid,
                finished.target,
                finished.startedAt,
                // The stored end date comes from the wall clock: it is a date, not a duration.
                clock.currentTimeMillis(),
                finished.activeMillis,
                finished.afkMillis));
    }

    /**
     * Registers a sign of life: movement intent, camera rotation, a key or mouse press,
     * or a gameplay interaction. Resets the AFK countdown and resumes the active counter
     * immediately.
     */
    public void onActivity() {
        if (session == null) {
            return;
        }
        long now = clock.elapsedMillis();
        accrue(now);

        // While the window is not focused the player cannot be playing, whatever stray
        // signals the game may still deliver.
        if (!session.windowFocused) {
            return;
        }
        markActive(now);
    }

    /**
     * Reports a change of window focus. Losing focus pauses the counter at once, with the
     * same retroactive rollback as a threshold expiry; regaining it counts as activity,
     * because coming back to the window is itself a deliberate act.
     */
    public void onWindowFocusChanged(boolean focused) {
        if (session == null) {
            return;
        }
        long now = clock.elapsedMillis();
        accrue(now);

        if (session.windowFocused == focused) {
            return;
        }
        session.windowFocused = focused;

        if (focused) {
            markActive(now);
        } else {
            switchToAfk(now);
        }
    }

    /**
     * Advances the accounting. Expected to be called on every client tick; calling it
     * more or less often only changes the resolution, never the totals.
     */
    public void tick() {
        if (session == null) {
            return;
        }
        long now = clock.elapsedMillis();
        accrue(now);
        applyAfkThreshold(now);
    }

    /** @return a fresh view of the session in progress, or empty if none is open */
    public Optional<SessionSnapshot> snapshot() {
        if (session == null) {
            return Optional.empty();
        }
        tick();
        return Optional.of(new SessionSnapshot(
                session.playerUuid,
                session.target,
                session.startedAt,
                session.activeMillis,
                session.afkMillis,
                session.state,
                clock.elapsedMillis() - session.lastActivityAt));
    }

    public boolean isSessionActive() {
        return session != null;
    }

    // --- internals ---------------------------------------------------------------

    /** Charges the time elapsed since the last accounting point to the current bucket. */
    private void accrue(long now) {
        long elapsed = now - session.lastAccountedAt;
        if (elapsed <= 0L) {
            // Belt and braces. The monotonic clock should never regress, but charging a
            // negative duration would corrupt every total downstream, so the guard stays.
            session.lastAccountedAt = now;
            return;
        }
        if (session.state == ActivityState.ACTIVE) {
            session.activeMillis += elapsed;
            // Charged in lock-step with activeMillis, and reset by markActive. This is the
            // exact figure switchToAfk has to take back.
            session.activeSinceLastActivity += elapsed;
        } else {
            session.afkMillis += elapsed;
        }
        session.lastAccountedAt = now;
    }

    /** Switches to AFK once inactivity has lasted longer than the configured threshold. */
    private void applyAfkThreshold(long now) {
        if (session.state != ActivityState.ACTIVE) {
            return;
        }
        if (now - session.lastActivityAt < config.get().getAfkThresholdMillis()) {
            return;
        }
        switchToAfk(now);
    }

    /**
     * Pauses the counter and undoes the optimistic charge: the whole idle span is moved
     * from the active counter to the AFK counter.
     */
    private void switchToAfk(long now) {
        if (session.state == ActivityState.AFK) {
            return;
        }
        // Capped by the session total as a belt-and-braces measure: the active counter must
        // never go negative, whatever happened to the clock.
        long rollback = Math.min(session.activeSinceLastActivity, session.activeMillis);
        session.activeMillis -= rollback;
        session.afkMillis += rollback;
        session.activeSinceLastActivity = 0L;
        session.state = ActivityState.AFK;
    }

    private void markActive(long now) {
        session.lastActivityAt = now;
        session.activeSinceLastActivity = 0L;
        session.state = ActivityState.ACTIVE;
    }

    /** Mutable state of the session in progress. */
    private static final class Session {

        final String playerUuid;
        final TargetKey target;
        /** Wall-clock date the session began, for the record that gets stored. */
        final long startedAt;

        /** Both measured against the monotonic clock: these exist only to be subtracted. */
        long lastAccountedAt;
        long lastActivityAt;
        long activeMillis;
        long afkMillis;
        /** Time charged to {@link #activeMillis} since the last sign of life. */
        long activeSinceLastActivity;
        ActivityState state = ActivityState.ACTIVE;
        boolean windowFocused = true;

        Session(String playerUuid, TargetKey target, long startedAt, long startedElapsed) {
            this.playerUuid = playerUuid;
            this.target = target;
            this.startedAt = startedAt;
            this.lastAccountedAt = startedElapsed;
            // Joining a world is an activity in itself: the session starts active.
            this.lastActivityAt = startedElapsed;
        }
    }
}

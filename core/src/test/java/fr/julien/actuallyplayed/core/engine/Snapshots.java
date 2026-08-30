package fr.julien.actuallyplayed.core.engine;

import fr.julien.actuallyplayed.core.model.TargetKey;

/**
 * Builds {@link SessionSnapshot} instances directly, for tests that need one without driving
 * an engine to produce it.
 * <p>
 * Lives in this package because the snapshot's constructor is package-private, and it stays
 * that way on purpose: outside the tests a snapshot must only ever come from the engine, so
 * that what the screen shows cannot disagree with what is being measured.
 */
public final class Snapshots {

    private Snapshots() {
    }

    public static SessionSnapshot of(TargetKey target,
                                     long startedAt,
                                     long activeMillis,
                                     long afkMillis,
                                     ActivityState state,
                                     long idleMillis) {
        return new SessionSnapshot("00000000-0000-0000-0000-000000000001",
                target, startedAt, activeMillis, afkMillis, state, idleMillis);
    }

    /** An active session, which is what most screen tests care about. */
    public static SessionSnapshot active(TargetKey target, long startedAt, long activeMillis, long afkMillis) {
        return of(target, startedAt, activeMillis, afkMillis, ActivityState.ACTIVE, 0L);
    }
}

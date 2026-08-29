package fr.julien.playtimetracker.core.model;

import java.util.Objects;

/**
 * A snapshot of the session in progress, persisted so a crash cannot swallow it whole.
 *
 * <h3>Why this exists</h3>
 * Sessions are only committed when they close, because two rules can still cancel time
 * after the fact: the retroactive AFK rollback, and the discarding of sessions shorter
 * than the minimum. That commit-at-close design has one hole — a crash three hours into a
 * session would lose all three hours, which makes a one-minute autosave interval
 * meaningless.
 * <p>
 * So every autosave also writes the session in progress here, as a provisional record.
 * On a clean disconnect it is cleared and replaced by a real {@link TrackedSession}. It is
 * only ever read back when the game did <em>not</em> exit cleanly, which is exactly when
 * it is needed. The rollback has already been applied by the engine at snapshot time, so
 * a recovered session is accounted the same way a closed one would have been.
 */
public final class ProvisionalSession {

    private final String playerUuid;
    private final TargetKey target;
    private final String displayName;
    private final long startedAt;
    private final long lastUpdatedAt;
    private final long activeMillis;
    private final long afkMillis;

    public ProvisionalSession(String playerUuid,
                              TargetKey target,
                              String displayName,
                              long startedAt,
                              long lastUpdatedAt,
                              long activeMillis,
                              long afkMillis) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.target = Objects.requireNonNull(target, "target");
        this.displayName = displayName;
        this.startedAt = startedAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.activeMillis = activeMillis;
        this.afkMillis = afkMillis;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public TargetKey getTarget() {
        return target;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getStartedAt() {
        return startedAt;
    }

    /** When this snapshot was taken — the session's end time if it has to be recovered. */
    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public long getActiveMillis() {
        return activeMillis;
    }

    public long getAfkMillis() {
        return afkMillis;
    }

    public long getTotalMillis() {
        return activeMillis + afkMillis;
    }

    /** Turns this snapshot into the closed session it stands for. */
    public TrackedSession toSession() {
        return new TrackedSession(playerUuid, target, startedAt, lastUpdatedAt, activeMillis, afkMillis);
    }

    @Override
    public String toString() {
        return "ProvisionalSession{" + target + ", active=" + activeMillis + "ms, afk=" + afkMillis + "ms}";
    }
}

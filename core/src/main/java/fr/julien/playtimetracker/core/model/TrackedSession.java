package fr.julien.playtimetracker.core.model;

import java.util.Objects;

/**
 * A finished play session, split between time actually played and time spent AFK.
 * <p>
 * Immutable, and only ever produced by the engine when a session closes — never
 * incrementally. That is a direct consequence of two rules that can both cancel time
 * after the fact: the retroactive AFK rollback, and the discarding of sessions shorter
 * than the configured minimum.
 */
public final class TrackedSession {

    private final String playerUuid;
    private final TargetKey target;
    private final long startedAt;
    private final long endedAt;
    private final long activeMillis;
    private final long afkMillis;

    public TrackedSession(String playerUuid,
                          TargetKey target,
                          long startedAt,
                          long endedAt,
                          long activeMillis,
                          long afkMillis) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.target = Objects.requireNonNull(target, "target");
        if (endedAt < startedAt) {
            throw new IllegalArgumentException("Session ends before it starts: " + startedAt + " > " + endedAt);
        }
        if (activeMillis < 0L || afkMillis < 0L) {
            throw new IllegalArgumentException(
                    "Session durations cannot be negative: active=" + activeMillis + ", afk=" + afkMillis);
        }
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.activeMillis = activeMillis;
        this.afkMillis = afkMillis;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public TargetKey getTarget() {
        return target;
    }

    /** Wall-clock epoch millis at which the session started. */
    public long getStartedAt() {
        return startedAt;
    }

    /** Wall-clock epoch millis at which the session ended. */
    public long getEndedAt() {
        return endedAt;
    }

    public long getActiveMillis() {
        return activeMillis;
    }

    public long getAfkMillis() {
        return afkMillis;
    }

    /**
     * Accounted duration, i.e. active plus AFK.
     * <p>
     * This can be slightly below {@code endedAt - startedAt}: the engine only accounts
     * time when it is ticked, so the tail between the last tick and the session's close
     * is measured, not assumed.
     */
    public long getTotalMillis() {
        return activeMillis + afkMillis;
    }

    /** Share of the session actually played, in {@code [0, 1]}. Zero for an empty session. */
    public double getActiveRatio() {
        long total = getTotalMillis();
        return total == 0L ? 0.0d : (double) activeMillis / (double) total;
    }

    @Override
    public String toString() {
        return "TrackedSession{" + target + ", active=" + activeMillis + "ms, afk=" + afkMillis + "ms}";
    }
}

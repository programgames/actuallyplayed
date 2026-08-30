package fr.julien.actuallyplayed.core.engine;

import fr.julien.actuallyplayed.core.model.TargetKey;

import java.util.Objects;

/**
 * Read-only view of the session in progress, for live display in the stats screen.
 * <p>
 * A snapshot is a value taken at one instant; it does not update on its own. The GUI
 * asks the engine for a fresh one whenever it redraws.
 */
public final class SessionSnapshot {

    private final String playerUuid;
    private final TargetKey target;
    private final long startedAt;
    private final long activeMillis;
    private final long afkMillis;
    private final ActivityState state;
    private final long idleMillis;

    SessionSnapshot(String playerUuid,
                    TargetKey target,
                    long startedAt,
                    long activeMillis,
                    long afkMillis,
                    ActivityState state,
                    long idleMillis) {
        this.playerUuid = playerUuid;
        this.target = target;
        this.startedAt = startedAt;
        this.activeMillis = activeMillis;
        this.afkMillis = afkMillis;
        this.state = Objects.requireNonNull(state, "state");
        this.idleMillis = idleMillis;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public TargetKey getTarget() {
        return target;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getActiveMillis() {
        return activeMillis;
    }

    public long getAfkMillis() {
        return afkMillis;
    }

    public ActivityState getState() {
        return state;
    }

    /** Time since the last activity signal. Useful to show "AFK for 12 min". */
    public long getIdleMillis() {
        return idleMillis;
    }

    public long getTotalMillis() {
        return activeMillis + afkMillis;
    }
}

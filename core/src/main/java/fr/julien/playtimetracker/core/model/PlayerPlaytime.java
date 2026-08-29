package fr.julien.playtimetracker.core.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * All targets tracked for one Minecraft account.
 * <p>
 * Keyed by account UUID rather than by name, so a name change does not split a player's
 * history in two.
 */
public final class PlayerPlaytime {

    private final String playerUuid;
    private final Map<TargetKey, TrackedTarget> targets = new LinkedHashMap<TargetKey, TrackedTarget>();

    public PlayerPlaytime(String playerUuid) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    /** Returns the target for this key, creating an empty one on first use. */
    public TrackedTarget target(TargetKey key) {
        Objects.requireNonNull(key, "key");
        TrackedTarget target = targets.get(key);
        if (target == null) {
            target = new TrackedTarget(key);
            targets.put(key, target);
        }
        return target;
    }

    public TrackedTarget find(TargetKey key) {
        return targets.get(key);
    }

    public Collection<TrackedTarget> getTargets() {
        return Collections.unmodifiableCollection(targets.values());
    }

    /**
     * Records a finished session, refreshing the target's display name along the way.
     *
     * @param displayName the current label for the target, or {@code null} to keep the
     *                    one already stored
     */
    public void record(TrackedSession session, String displayName) {
        Objects.requireNonNull(session, "session");
        TrackedTarget target = target(session.getTarget());
        target.setDisplayName(displayName);
        target.addSession(session);
    }

    // --- totals across every target ------------------------------------------------

    public long getTotalActiveMillis() {
        long total = 0L;
        for (TrackedTarget target : targets.values()) {
            total += target.getTotalActiveMillis();
        }
        return total;
    }

    public long getTotalAfkMillis() {
        long total = 0L;
        for (TrackedTarget target : targets.values()) {
            total += target.getTotalAfkMillis();
        }
        return total;
    }

    public long getTotalMillis() {
        return getTotalActiveMillis() + getTotalAfkMillis();
    }

    public double getActiveRatio() {
        long total = getTotalMillis();
        return total == 0L ? 0.0d : (double) getTotalActiveMillis() / (double) total;
    }

    public int getSessionCount() {
        int count = 0;
        for (TrackedTarget target : targets.values()) {
            count += target.getSessionCount();
        }
        return count;
    }
}

package fr.julien.actuallyplayed.core.screen;

import fr.julien.actuallyplayed.core.model.TrackedTarget;

/**
 * What has already been recorded for a destination, flattened to five numbers and a name.
 *
 * <h3>Why not just pass the target</h3>
 * Every one of these figures is <em>derived</em>: {@code TrackedTarget} stores no counters, it
 * walks its sessions and its monthly aggregates on each call. That is the right design — a
 * stored total can drift from what it summarises — but it means reading them is proportional
 * to the history. The screen redraws sixty times a second, and nothing can close a session
 * while it is open, so the walk happens once and the answer is carried in this object.
 */
public final class RecordedTotals {

    private static final RecordedTotals EMPTY =
            new RecordedTotals(null, 0L, 0L, 0, 0L, 0L);

    private final String displayName;
    private final long activeMillis;
    private final long afkMillis;
    private final int sessionCount;
    private final long longestSessionMillis;
    private final long firstSeenAt;

    private RecordedTotals(String displayName,
                           long activeMillis,
                           long afkMillis,
                           int sessionCount,
                           long longestSessionMillis,
                           long firstSeenAt) {
        this.displayName = displayName;
        this.activeMillis = activeMillis;
        this.afkMillis = afkMillis;
        this.sessionCount = sessionCount;
        this.longestSessionMillis = longestSessionMillis;
        this.firstSeenAt = firstSeenAt;
    }

    /**
     * Reads a target's totals once. A {@code null} target — the player has never closed a
     * session on this destination — yields all zeros, which is exactly right: the running
     * session then accounts for everything the screen shows.
     */
    public static RecordedTotals of(TrackedTarget target) {
        if (target == null) {
            return EMPTY;
        }
        return new RecordedTotals(
                target.getDisplayName(),
                target.getTotalActiveMillis(),
                target.getTotalAfkMillis(),
                target.getSessionCount(),
                target.getLongestSessionMillis(),
                target.getFirstSeenAt());
    }

    public static RecordedTotals empty() {
        return EMPTY;
    }

    /** The destination's label, or {@code null} if none was ever stored. */
    public String getDisplayName() {
        return displayName;
    }

    public long getActiveMillis() {
        return activeMillis;
    }

    public long getAfkMillis() {
        return afkMillis;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public long getLongestSessionMillis() {
        return longestSessionMillis;
    }

    /** Epoch millis of the first session recorded here, or {@code 0} if there is none. */
    public long getFirstSeenAt() {
        return firstSeenAt;
    }
}

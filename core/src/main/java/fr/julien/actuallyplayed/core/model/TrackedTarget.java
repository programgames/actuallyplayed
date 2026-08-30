package fr.julien.actuallyplayed.core.model;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Everything recorded about one server or one singleplayer world, for one account.
 * <p>
 * Holds recent sessions in full detail plus older months already compacted into
 * {@link MonthlyAggregate}s.
 *
 * <h3>Why totals are derived rather than stored</h3>
 * Every total on this class is computed from the sessions and the aggregates, never kept
 * as a separate running counter. A stored counter could drift away from the data it
 * summarises — after a compaction bug, a partial write, or a hand-edit of the JSON file.
 * Deriving makes the invariant "compaction never changes the totals" true by construction
 * instead of by discipline.
 */
public final class TrackedTarget {

    private final TargetKey key;
    private String displayName;
    private final List<TrackedSession> sessions = new ArrayList<TrackedSession>();
    private final Map<YearMonth, MonthlyAggregate> aggregates = new TreeMap<YearMonth, MonthlyAggregate>();

    public TrackedTarget(TargetKey key) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = key.getId();
    }

    public TargetKey getKey() {
        return key;
    }

    /**
     * Human-readable label: the name the player gave the server in their server list, or
     * the world's name. Purely cosmetic — never used for identity, since it can change.
     */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (displayName != null && !displayName.trim().isEmpty()) {
            this.displayName = displayName;
        }
    }

    public void addSession(TrackedSession session) {
        sessions.add(Objects.requireNonNull(session, "session"));
    }

    /** Detailed sessions still inside the retention window, in insertion order. */
    public List<TrackedSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    /** Compacted months, ordered chronologically. */
    public Collection<MonthlyAggregate> getAggregates() {
        return Collections.unmodifiableCollection(aggregates.values());
    }

    public void putAggregate(MonthlyAggregate aggregate) {
        Objects.requireNonNull(aggregate, "aggregate");
        aggregates.put(aggregate.getMonth(), aggregate);
    }

    /**
     * Folds a session into its month's aggregate and drops it from the detailed list.
     * <p>
     * Meant for the retention policy only. The target's totals are unaffected, because
     * the aggregate absorbs exactly what the session contributed.
     */
    public void compactSession(TrackedSession session, YearMonth month) {
        MonthlyAggregate current = aggregates.get(month);
        if (current == null) {
            current = MonthlyAggregate.empty(month);
        }
        aggregates.put(month, current.withSession(session));
        sessions.remove(session);
    }

    // --- derived statistics -------------------------------------------------------

    public int getSessionCount() {
        int count = sessions.size();
        for (MonthlyAggregate aggregate : aggregates.values()) {
            count += aggregate.getSessionCount();
        }
        return count;
    }

    public long getTotalActiveMillis() {
        long total = 0L;
        for (TrackedSession session : sessions) {
            total += session.getActiveMillis();
        }
        for (MonthlyAggregate aggregate : aggregates.values()) {
            total += aggregate.getActiveMillis();
        }
        return total;
    }

    public long getTotalAfkMillis() {
        long total = 0L;
        for (TrackedSession session : sessions) {
            total += session.getAfkMillis();
        }
        for (MonthlyAggregate aggregate : aggregates.values()) {
            total += aggregate.getAfkMillis();
        }
        return total;
    }

    public long getTotalMillis() {
        return getTotalActiveMillis() + getTotalAfkMillis();
    }

    /** Share of the time on this target actually played, in {@code [0, 1]}. */
    public double getActiveRatio() {
        long total = getTotalMillis();
        return total == 0L ? 0.0d : (double) getTotalActiveMillis() / (double) total;
    }

    /** @return epoch millis of the first ever connection, or 0 if nothing is recorded */
    public long getFirstSeenAt() {
        long first = Long.MAX_VALUE;
        for (TrackedSession session : sessions) {
            first = Math.min(first, session.getStartedAt());
        }
        for (MonthlyAggregate aggregate : aggregates.values()) {
            if (aggregate.getSessionCount() > 0) {
                first = Math.min(first, aggregate.getFirstStartedAt());
            }
        }
        return first == Long.MAX_VALUE ? 0L : first;
    }

    public long getLongestSessionMillis() {
        long longest = 0L;
        for (TrackedSession session : sessions) {
            longest = Math.max(longest, session.getTotalMillis());
        }
        for (MonthlyAggregate aggregate : aggregates.values()) {
            longest = Math.max(longest, aggregate.getLongestSessionMillis());
        }
        return longest;
    }

    @Override
    public String toString() {
        return "TrackedTarget{" + key + ", " + getSessionCount() + " sessions}";
    }
}

package fr.julien.playtimetracker.core.model;

import java.time.YearMonth;
import java.util.Objects;

/**
 * One month of sessions on a target, collapsed into a summary.
 * <p>
 * Produced when detailed sessions age past the retention window. Compaction discards the
 * session-by-session detail but never the time itself: an aggregate carries the exact
 * sums of what it replaced, so the target's totals are unchanged by compaction.
 * <p>
 * Immutable — {@link #withSession(TrackedSession)} returns a new instance.
 */
public final class MonthlyAggregate {

    private final YearMonth month;
    private final int sessionCount;
    private final long activeMillis;
    private final long afkMillis;
    private final long firstStartedAt;
    private final long lastEndedAt;
    private final long longestSessionMillis;

    private MonthlyAggregate(YearMonth month,
                             int sessionCount,
                             long activeMillis,
                             long afkMillis,
                             long firstStartedAt,
                             long lastEndedAt,
                             long longestSessionMillis) {
        this.month = Objects.requireNonNull(month, "month");
        this.sessionCount = sessionCount;
        this.activeMillis = activeMillis;
        this.afkMillis = afkMillis;
        this.firstStartedAt = firstStartedAt;
        this.lastEndedAt = lastEndedAt;
        this.longestSessionMillis = longestSessionMillis;
    }

    /** Rebuilds an aggregate read back from storage. */
    public static MonthlyAggregate of(YearMonth month,
                                      int sessionCount,
                                      long activeMillis,
                                      long afkMillis,
                                      long firstStartedAt,
                                      long lastEndedAt,
                                      long longestSessionMillis) {
        return new MonthlyAggregate(month, sessionCount, activeMillis, afkMillis,
                firstStartedAt, lastEndedAt, longestSessionMillis);
    }

    public static MonthlyAggregate empty(YearMonth month) {
        return new MonthlyAggregate(month, 0, 0L, 0L, Long.MAX_VALUE, Long.MIN_VALUE, 0L);
    }

    /** @return a copy of this aggregate with one more session folded in */
    public MonthlyAggregate withSession(TrackedSession session) {
        Objects.requireNonNull(session, "session");
        return new MonthlyAggregate(
                month,
                sessionCount + 1,
                activeMillis + session.getActiveMillis(),
                afkMillis + session.getAfkMillis(),
                Math.min(firstStartedAt, session.getStartedAt()),
                Math.max(lastEndedAt, session.getEndedAt()),
                Math.max(longestSessionMillis, session.getTotalMillis()));
    }

    public YearMonth getMonth() {
        return month;
    }

    public int getSessionCount() {
        return sessionCount;
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

    /** @return epoch millis of the earliest session in the month, or 0 if the aggregate is empty */
    public long getFirstStartedAt() {
        return sessionCount == 0 ? 0L : firstStartedAt;
    }

    /** @return epoch millis of the latest session in the month, or 0 if the aggregate is empty */
    public long getLastEndedAt() {
        return sessionCount == 0 ? 0L : lastEndedAt;
    }

    public long getLongestSessionMillis() {
        return longestSessionMillis;
    }

    @Override
    public String toString() {
        return "MonthlyAggregate{" + month + ", " + sessionCount + " sessions}";
    }
}

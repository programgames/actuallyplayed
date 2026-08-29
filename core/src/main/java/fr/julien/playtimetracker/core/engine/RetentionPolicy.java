package fr.julien.playtimetracker.core.engine;

import fr.julien.playtimetracker.core.model.PlayerPlaytime;
import fr.julien.playtimetracker.core.model.PlaytimeData;
import fr.julien.playtimetracker.core.model.TrackedSession;
import fr.julien.playtimetracker.core.model.TrackedTarget;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the data file bounded by collapsing old sessions into monthly summaries.
 * <p>
 * Sessions that ended more than {@code retentionDays} ago lose their individual detail and
 * are folded into the aggregate for the month they started in. Nothing is deleted in the
 * accounting sense: because {@link TrackedTarget}'s totals are derived from sessions
 * <em>and</em> aggregates alike, compaction leaves every total untouched.
 * <p>
 * Runs once at game start.
 */
public final class RetentionPolicy {

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private final int retentionDays;
    private final ZoneId zone;

    /**
     * @param zone time zone used to decide which month a session belongs to. The player's
     *             local zone is the meaningful one here: a session is filed under the month
     *             they experienced, not under UTC's.
     */
    public RetentionPolicy(int retentionDays, ZoneId zone) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("Retention must be at least one day: " + retentionDays);
        }
        this.retentionDays = retentionDays;
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public static RetentionPolicy withSystemZone(int retentionDays) {
        return new RetentionPolicy(retentionDays, ZoneId.systemDefault());
    }

    /**
     * Compacts every target in place.
     *
     * @param nowMillis current wall-clock time
     * @return how many sessions were folded into aggregates
     */
    public int compact(PlaytimeData data, long nowMillis) {
        Objects.requireNonNull(data, "data");
        long cutoff = nowMillis - retentionDays * MILLIS_PER_DAY;

        int compacted = 0;
        for (PlayerPlaytime player : data.getPlayers()) {
            for (TrackedTarget target : player.getTargets()) {
                compacted += compact(target, cutoff);
            }
        }
        return compacted;
    }

    private int compact(TrackedTarget target, long cutoff) {
        // Collected first, then applied: compaction mutates the session list, so iterating
        // it directly while removing would be undefined.
        List<TrackedSession> expired = new ArrayList<TrackedSession>();
        for (TrackedSession session : target.getSessions()) {
            if (session.getEndedAt() < cutoff) {
                expired.add(session);
            }
        }

        for (TrackedSession session : expired) {
            target.compactSession(session, monthOf(session.getStartedAt()));
        }
        return expired.size();
    }

    private YearMonth monthOf(long epochMillis) {
        return YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}

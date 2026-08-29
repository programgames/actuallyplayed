package fr.julien.playtimetracker.core.engine;

import fr.julien.playtimetracker.core.model.MonthlyAggregate;
import fr.julien.playtimetracker.core.model.PlaytimeData;
import fr.julien.playtimetracker.core.model.TargetKey;
import fr.julien.playtimetracker.core.model.TrackedSession;
import fr.julien.playtimetracker.core.model.TrackedTarget;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RetentionPolicyTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static final long MINUTE = 60_000L;

    private final PlaytimeData data = new PlaytimeData();

    private static long at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0).atZone(PARIS).toInstant().toEpochMilli();
    }

    private TrackedSession session(long startedAt, long activeMillis, long afkMillis) {
        TrackedSession session = new TrackedSession(
                PLAYER, SERVER, startedAt, startedAt + activeMillis + afkMillis, activeMillis, afkMillis);
        data.record(session, "Hypixel");
        return session;
    }

    private TrackedTarget target() {
        return data.player(PLAYER).target(SERVER);
    }

    @Test
    public void leavesRecentSessionsAlone() {
        long now = at(2026, 8, 29, 12);
        session(at(2026, 8, 1, 20), 60 * MINUTE, 10 * MINUTE);

        int compacted = new RetentionPolicy(90, PARIS).compact(data, now);

        assertEquals(0, compacted);
        assertEquals(1, target().getSessions().size());
        assertTrue(target().getAggregates().isEmpty());
    }

    @Test
    public void compactsSessionsOlderThanTheWindow() {
        long now = at(2026, 8, 29, 12);
        session(at(2026, 1, 10, 20), 60 * MINUTE, 10 * MINUTE);
        session(at(2026, 1, 20, 20), 30 * MINUTE, 5 * MINUTE);

        int compacted = new RetentionPolicy(90, PARIS).compact(data, now);

        assertEquals(2, compacted);
        assertTrue("compacted sessions leave the detailed list", target().getSessions().isEmpty());
        assertEquals("both January sessions land in one aggregate", 1, target().getAggregates().size());

        MonthlyAggregate january = target().getAggregates().iterator().next();
        assertEquals(YearMonth.of(2026, 1), january.getMonth());
        assertEquals(2, january.getSessionCount());
        assertEquals(90 * MINUTE, january.getActiveMillis());
        assertEquals(15 * MINUTE, january.getAfkMillis());
        assertEquals(70 * MINUTE, january.getLongestSessionMillis());
    }

    @Test
    public void neverChangesTheTotals() {
        long now = at(2026, 8, 29, 12);
        List<Long> starts = new ArrayList<Long>();
        starts.add(at(2025, 11, 3, 18));
        starts.add(at(2025, 12, 24, 21));
        starts.add(at(2026, 2, 14, 9));
        starts.add(at(2026, 8, 20, 19));
        for (long start : starts) {
            session(start, 45 * MINUTE, 12 * MINUTE);
        }

        long activeBefore = target().getTotalActiveMillis();
        long afkBefore = target().getTotalAfkMillis();
        int countBefore = target().getSessionCount();
        long firstBefore = target().getFirstSeenAt();
        long longestBefore = target().getLongestSessionMillis();

        new RetentionPolicy(90, PARIS).compact(data, now);

        assertEquals("compaction must never lose playtime", activeBefore, target().getTotalActiveMillis());
        assertEquals(afkBefore, target().getTotalAfkMillis());
        assertEquals(countBefore, target().getSessionCount());
        assertEquals(firstBefore, target().getFirstSeenAt());
        assertEquals(longestBefore, target().getLongestSessionMillis());
    }

    @Test
    public void separatesMonths() {
        long now = at(2026, 8, 29, 12);
        session(at(2025, 11, 3, 18), 10 * MINUTE, 0L);
        session(at(2025, 12, 24, 21), 20 * MINUTE, 0L);
        session(at(2026, 2, 14, 9), 30 * MINUTE, 0L);

        new RetentionPolicy(90, PARIS).compact(data, now);

        assertEquals(3, target().getAggregates().size());
    }

    @Test
    public void isIdempotent() {
        long now = at(2026, 8, 29, 12);
        session(at(2026, 1, 10, 20), 60 * MINUTE, 10 * MINUTE);

        RetentionPolicy policy = new RetentionPolicy(90, PARIS);
        policy.compact(data, now);
        long activeAfterFirst = target().getTotalActiveMillis();

        int secondPass = policy.compact(data, now);

        assertEquals("nothing left to compact on a second run", 0, secondPass);
        assertEquals(activeAfterFirst, target().getTotalActiveMillis());
        assertEquals(1, target().getAggregates().size());
    }

    @Test
    public void filesASessionUnderTheMonthItStartedIn() {
        // Starts on 31 January at 23:00 Paris time, ends in February.
        long now = at(2026, 8, 29, 12);
        session(at(2026, 1, 31, 23), 90 * MINUTE, 0L);

        new RetentionPolicy(90, PARIS).compact(data, now);

        assertEquals(YearMonth.of(2026, 1), target().getAggregates().iterator().next().getMonth());
    }
}

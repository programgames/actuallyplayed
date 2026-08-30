package fr.julien.actuallyplayed.core.model;

import org.junit.Test;

import java.time.YearMonth;

import static org.junit.Assert.assertEquals;

public class TrackedTargetTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");
    private static final long MINUTE = 60_000L;

    private final TrackedTarget target = new TrackedTarget(SERVER);

    private void addSession(long startedAt, long activeMillis, long afkMillis) {
        target.addSession(new TrackedSession(
                PLAYER, SERVER, startedAt, startedAt + activeMillis + afkMillis, activeMillis, afkMillis));
    }

    @Test
    public void startsEmpty() {
        assertEquals(0, target.getSessionCount());
        assertEquals(0L, target.getTotalMillis());
        assertEquals(0L, target.getFirstSeenAt());
        assertEquals(0.0d, target.getActiveRatio(), 0.0001d);
    }

    @Test
    public void fallsBackToTheKeyAsDisplayName() {
        assertEquals("mc.hypixel.net:25565", target.getDisplayName());
    }

    @Test
    public void ignoresABlankDisplayName() {
        target.setDisplayName("Hypixel");
        target.setDisplayName(null);
        target.setDisplayName("   ");

        assertEquals("a blank label must not erase a good one", "Hypixel", target.getDisplayName());
    }

    @Test
    public void derivesStatisticsFromSessions() {
        addSession(1000L, 60 * MINUTE, 10 * MINUTE);
        addSession(1000L + 200 * MINUTE, 30 * MINUTE, 0L);

        assertEquals(2, target.getSessionCount());
        assertEquals(90 * MINUTE, target.getTotalActiveMillis());
        assertEquals(10 * MINUTE, target.getTotalAfkMillis());
        assertEquals(100 * MINUTE, target.getTotalMillis());
        assertEquals(0.9d, target.getActiveRatio(), 0.0001d);
        assertEquals(70 * MINUTE, target.getLongestSessionMillis());
        assertEquals(1000L, target.getFirstSeenAt());
    }

    @Test
    public void countsSessionsAndAggregatesTogether() {
        addSession(500_000L, 30 * MINUTE, 0L);
        target.putAggregate(MonthlyAggregate.of(
                YearMonth.of(2026, 1), 3, 90 * MINUTE, 20 * MINUTE, 1000L, 9000L, 60 * MINUTE));

        assertEquals(4, target.getSessionCount());
        assertEquals(120 * MINUTE, target.getTotalActiveMillis());
        assertEquals(20 * MINUTE, target.getTotalAfkMillis());
        assertEquals("the longest session may live in an aggregate", 60 * MINUTE,
                target.getLongestSessionMillis());
        assertEquals("first seen may predate every detailed session", 1000L, target.getFirstSeenAt());
    }


    @Test
    public void aggregatesAcrossEveryTargetOfAnAccount() {
        PlayerPlaytime player = new PlayerPlaytime(PLAYER);
        player.record(new TrackedSession(PLAYER, SERVER, 0L, 70 * MINUTE, 60 * MINUTE, 10 * MINUTE), "Hypixel");
        player.record(new TrackedSession(PLAYER, TargetKey.singleplayer("Solo"),
                0L, 30 * MINUTE, 30 * MINUTE, 0L), "Solo");

        assertEquals(90 * MINUTE, player.getTotalActiveMillis());
        assertEquals(10 * MINUTE, player.getTotalAfkMillis());
        assertEquals(100 * MINUTE, player.getTotalMillis());
        assertEquals(2, player.getSessionCount());
        assertEquals(0.9d, player.getActiveRatio(), 0.0001d);
    }
}

package fr.julien.actuallyplayed.core.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DurationFormatterTest {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;

    @Test
    public void formatsSeconds() {
        assertEquals("45s", DurationFormatter.format(45 * SECOND));
        assertEquals("1s", DurationFormatter.format(1500L));
    }

    @Test
    public void formatsMinutesWithSeconds() {
        assertEquals("12m 30s", DurationFormatter.format(12 * MINUTE + 30 * SECOND));
        assertEquals("1m 0s", DurationFormatter.format(MINUTE));
    }

    @Test
    public void dropsSecondsPastAnHour() {
        assertEquals("seconds are noise across hours", "5h 12m",
                DurationFormatter.format(5 * HOUR + 12 * MINUTE + 45 * SECOND));
        assertEquals("2h 0m", DurationFormatter.format(2 * HOUR));
    }

    @Test
    public void handlesLargeDurations() {
        assertEquals("1000h 0m", DurationFormatter.format(1000 * HOUR));
    }

    @Test
    public void showsZeroForNothingAndForNegatives() {
        assertEquals("0s", DurationFormatter.format(0L));
        assertEquals("0s", DurationFormatter.format(999L));
        assertEquals("a negative duration means a bug upstream, not a minus sign on screen",
                "0s", DurationFormatter.format(-5000L));
    }

    @Test
    public void formatsPercentages() {
        assertEquals("100%", DurationFormatter.formatPercent(1.0d));
        assertEquals("0%", DurationFormatter.formatPercent(0.0d));
        assertEquals("90%", DurationFormatter.formatPercent(0.9d));
        assertEquals("67%", DurationFormatter.formatPercent(2.0d / 3.0d));
    }
}

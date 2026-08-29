package fr.julien.playtimetracker.core.util;

/**
 * Renders durations the way a player reads them.
 * <p>
 * Deliberately unit-lettered ({@code 5h 12m}) rather than worded: the letters read the same
 * in French and in English, so the stats screen needs no translation for its numbers, and
 * the columns stay narrow enough to fit beside a server name.
 * <p>
 * Precision drops as the duration grows — seconds matter for a short visit, minutes do not
 * matter across five hours.
 */
public final class DurationFormatter {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;

    private DurationFormatter() {
    }

    /**
     * @param millis a duration; negative values are clamped to zero rather than rendered
     *               with a minus sign, which would only ever mean a bug upstream
     */
    public static String format(long millis) {
        if (millis < SECOND) {
            return "0s";
        }

        long hours = millis / HOUR;
        long minutes = (millis % HOUR) / MINUTE;
        long seconds = (millis % MINUTE) / SECOND;

        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /** @param ratio a value in {@code [0, 1]} */
    public static String formatPercent(double ratio) {
        return Math.round(ratio * 100.0d) + "%";
    }
}

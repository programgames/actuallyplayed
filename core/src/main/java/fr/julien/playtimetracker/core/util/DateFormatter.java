package fr.julien.playtimetracker.core.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renders timestamps for the stats screens.
 * <p>
 * Year-first ({@code 2026-08-29}) rather than a locale-specific order: {@code 08/09/2026}
 * means two different days depending on who reads it, and this mod has both French and
 * English users looking at the same numbers. Year-first is unambiguous everywhere and
 * sorts correctly as text.
 */
public final class DateFormatter {

    /** Shown in place of a date that was never recorded. */
    public static final String UNKNOWN = "—";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateFormatter() {
    }

    public static String formatDate(long epochMillis, ZoneId zone) {
        if (epochMillis <= 0L) {
            return UNKNOWN;
        }
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

}

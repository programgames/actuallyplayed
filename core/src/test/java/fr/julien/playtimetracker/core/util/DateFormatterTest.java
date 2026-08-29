package fr.julien.playtimetracker.core.util;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;

public class DateFormatterTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(PARIS).toInstant().toEpochMilli();
    }


    @Test
    public void formatsDateOnly() {
        assertEquals("2026-08-29", DateFormatter.formatDate(at(2026, 8, 29, 15, 31), PARIS));
    }



    @Test
    public void showsADashForMissingTimestamps() {
        assertEquals(DateFormatter.UNKNOWN, DateFormatter.formatDate(0L, PARIS));
        assertEquals(DateFormatter.UNKNOWN, DateFormatter.formatDate(-1L, PARIS));
    }

}

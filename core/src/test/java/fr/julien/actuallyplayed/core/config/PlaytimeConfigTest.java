package fr.julien.actuallyplayed.core.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The builder validates every setting as it is supplied, so an invalid configuration cannot
 * exist at all. These tests pin that down: the values arrive from a file a player can edit
 * by hand, and a zero threshold would put the engine in a state no other code guards against.
 */
public class PlaytimeConfigTest {

    @Test
    public void defaultsMatchTheDocumentedBehaviour() {
        PlaytimeConfig config = PlaytimeConfig.defaults();

        assertEquals(5 * 60 * 1000L, config.getAfkThresholdMillis());
        assertEquals(30 * 1000L, config.getMinSessionMillis());
        assertEquals(60 * 1000L, config.getAutosaveIntervalMillis());
        assertEquals(90, config.getRetentionDays());
    }

    @Test
    public void toBuilderChangesOneSettingAndKeepsTheRest() {
        PlaytimeConfig changed = PlaytimeConfig.defaults().toBuilder()
                .afkThresholdMillis(60_000L)
                .build();

        assertEquals(60_000L, changed.getAfkThresholdMillis());
        assertEquals("the untouched settings must survive", 30 * 1000L, changed.getMinSessionMillis());
        assertEquals(90, changed.getRetentionDays());
    }

    @Test
    public void theOriginalIsUnaffectedByABuilderDerivedFromIt() {
        PlaytimeConfig original = PlaytimeConfig.defaults();
        original.toBuilder().afkThresholdMillis(1000L).build();

        assertEquals("immutability is the whole point of swapping instead of editing",
                5 * 60 * 1000L, original.getAfkThresholdMillis());
    }

    @Test
    public void keepsAZeroMinimumSessionLength() {
        assertEquals("0 means keep every session, and is a legitimate setting",
                0L, PlaytimeConfig.builder().minSessionMillis(0L).build().getMinSessionMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAZeroAfkThreshold() {
        PlaytimeConfig.builder().afkThresholdMillis(0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsANegativeAfkThreshold() {
        PlaytimeConfig.builder().afkThresholdMillis(-1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsANegativeMinimumSessionLength() {
        PlaytimeConfig.builder().minSessionMillis(-1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAZeroAutosaveInterval() {
        PlaytimeConfig.builder().autosaveIntervalMillis(0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAZeroRetention() {
        PlaytimeConfig.builder().retentionDays(0);
    }
}

package fr.julien.actuallyplayed.core.config;

/**
 * Tunable behaviour of the tracking engine.
 *
 * <h3>Immutable, and swapped rather than edited</h3>
 * Settings are read by the client thread on every tick and by the shutdown hook on its own
 * thread, while the in-game settings screen writes them. A mutable object shared that way
 * is a data race: a non-volatile {@code long} may be seen half-written.
 * <p>
 * Making the object immutable removes the race by construction rather than by discipline —
 * a value that never changes cannot be observed in an inconsistent state. Changing a
 * setting builds a new instance and publishes it in one assignment, which readers either
 * see entirely or not at all.
 */
public final class PlaytimeConfig {

    public static final long DEFAULT_AFK_THRESHOLD_MILLIS = 5L * 60L * 1000L;
    public static final long DEFAULT_MIN_SESSION_MILLIS = 30L * 1000L;
    public static final long DEFAULT_AUTOSAVE_INTERVAL_MILLIS = 60L * 1000L;
    public static final int DEFAULT_RETENTION_DAYS = 90;

    private final long afkThresholdMillis;
    private final long minSessionMillis;
    private final long autosaveIntervalMillis;
    private final int retentionDays;

    private PlaytimeConfig(Builder builder) {
        this.afkThresholdMillis = builder.afkThresholdMillis;
        this.minSessionMillis = builder.minSessionMillis;
        this.autosaveIntervalMillis = builder.autosaveIntervalMillis;
        this.retentionDays = builder.retentionDays;
    }

    public static PlaytimeConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Starts from this configuration, to change one setting without restating the rest. */
    public Builder toBuilder() {
        return new Builder()
                .afkThresholdMillis(afkThresholdMillis)
                .minSessionMillis(minSessionMillis)
                .autosaveIntervalMillis(autosaveIntervalMillis)
                .retentionDays(retentionDays);
    }

    /**
     * Inactivity after which the counter stops and the elapsed idle time is retroactively
     * moved from the active bucket to the AFK bucket.
     */
    public long getAfkThresholdMillis() {
        return afkThresholdMillis;
    }

    /**
     * Sessions shorter than this are discarded entirely — neither recorded in the history
     * nor added to the target's totals.
     */
    public long getMinSessionMillis() {
        return minSessionMillis;
    }

    public long getAutosaveIntervalMillis() {
        return autosaveIntervalMillis;
    }

    /** Age past which detailed sessions are compacted into monthly aggregates. */
    public int getRetentionDays() {
        return retentionDays;
    }

    @Override
    public String toString() {
        return "PlaytimeConfig{afk=" + afkThresholdMillis + "ms"
                + ", minSession=" + minSessionMillis + "ms"
                + ", autosave=" + autosaveIntervalMillis + "ms"
                + ", retention=" + retentionDays + "d}";
    }

    /** Validates every setting as it is supplied, so an invalid configuration cannot exist. */
    public static final class Builder {

        private long afkThresholdMillis = DEFAULT_AFK_THRESHOLD_MILLIS;
        private long minSessionMillis = DEFAULT_MIN_SESSION_MILLIS;
        private long autosaveIntervalMillis = DEFAULT_AUTOSAVE_INTERVAL_MILLIS;
        private int retentionDays = DEFAULT_RETENTION_DAYS;

        private Builder() {
        }

        public Builder afkThresholdMillis(long value) {
            if (value <= 0L) {
                throw new IllegalArgumentException("AFK threshold must be positive: " + value);
            }
            this.afkThresholdMillis = value;
            return this;
        }

        public Builder minSessionMillis(long value) {
            if (value < 0L) {
                throw new IllegalArgumentException("Minimum session length cannot be negative: " + value);
            }
            this.minSessionMillis = value;
            return this;
        }

        public Builder autosaveIntervalMillis(long value) {
            if (value <= 0L) {
                throw new IllegalArgumentException("Autosave interval must be positive: " + value);
            }
            this.autosaveIntervalMillis = value;
            return this;
        }

        public Builder retentionDays(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Retention must be at least one day: " + value);
            }
            this.retentionDays = value;
            return this;
        }

        public PlaytimeConfig build() {
            return new PlaytimeConfig(this);
        }
    }
}

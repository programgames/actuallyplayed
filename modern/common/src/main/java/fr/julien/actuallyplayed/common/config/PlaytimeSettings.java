package fr.julien.actuallyplayed.common.config;

import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's settings, as a plain text file both loaders read the same way.
 *
 * <h3>Why a file and no screen</h3>
 * The 1.12 build has an in-game settings screen because Forge provides one for free. Fabric
 * does not: the usual answer is Cloth Config and ModMenu, which the player has to install
 * alongside the mod. That is the same toll this project refused when it ruled out Architectury
 * API — a small client-side mod should be one jar dropped into {@code mods/} and nothing else.
 * Writing the screen twice, once per loader, would also mean maintaining two of them for five
 * settings.
 * <p>
 * So the settings live in one commented file, read by one piece of code, on every loader. The
 * cost is honest and worth naming: a player who could click a slider on 1.12 now edits a file.
 *
 * <h3>Tolerance</h3>
 * A value that cannot be read falls back to its default and says so in the log. Losing one
 * misspelt setting is better than refusing to start, and it matches how the storage layer
 * treats a damaged entry.
 */
public final class PlaytimeSettings {

    private static final String FILE_NAME = "actuallyplayed.properties";

    private static final String KEY_AFK = "afkThresholdSeconds";
    private static final String KEY_MIN_SESSION = "minSessionSeconds";
    private static final String KEY_AUTOSAVE = "autosaveIntervalSeconds";
    private static final String KEY_RETENTION = "retentionDays";
    private static final String KEY_DEBUG = "debugLogging";

    private static final List<String> TEMPLATE = List.of(
            "# Actually Played",
            "#",
            "# Edit while the game is closed; the file is read once at startup.",
            "# Delete this file to get the defaults back.",
            "",
            "# Inactivity after which the counter stops. The idle time already elapsed is taken",
            "# back out of your played total and moved to AFK.",
            KEY_AFK + "=300",
            "",
            "# Sessions shorter than this are discarded entirely, so brief visits do not clutter",
            "# your statistics. Set to 0 to keep every session.",
            KEY_MIN_SESSION + "=30",
            "",
            "# How often the data file is written. This also bounds how much of a running session",
            "# a crash can cost you.",
            KEY_AUTOSAVE + "=60",
            "",
            "# How long each session is kept in full detail. Older ones are merged into monthly",
            "# summaries. No playtime is ever lost, only the detail.",
            KEY_RETENTION + "=90",
            "",
            "# Writes every switch between playing and AFK to the log. The mod is silent by",
            "# default; turn this on to check that activity detection behaves as you expect.",
            KEY_DEBUG + "=false");

    private final PlaytimeConfig config;
    private final boolean debugLogging;

    private PlaytimeSettings(PlaytimeConfig config, boolean debugLogging) {
        this.config = config;
        this.debugLogging = debugLogging;
    }

    /**
     * Reads the settings from {@code configDir}, writing a commented file with the defaults if
     * there is none.
     * <p>
     * Never throws: a directory that cannot be written, or a file that cannot be read, leaves
     * the mod running on defaults rather than not running at all.
     */
    public static PlaytimeSettings load(Path configDir, Logger logger) {
        Path file = configDir.resolve(FILE_NAME);

        if (!Files.exists(file)) {
            try {
                Files.write(file, TEMPLATE, StandardCharsets.UTF_8);
            } catch (IOException | UncheckedIOException e) {
                logger.warn("Could not write the default settings to {}; using the built-in "
                        + "defaults for this session.", file, e);
            }
            return new PlaytimeSettings(PlaytimeConfig.defaults(), false);
        }

        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                values.put(trimmed.substring(0, separator).trim(),
                        trimmed.substring(separator + 1).trim());
            }
        } catch (IOException | UncheckedIOException e) {
            logger.warn("Could not read {}; using the defaults for this session.", file, e);
            return new PlaytimeSettings(PlaytimeConfig.defaults(), false);
        }

        PlaytimeConfig config = PlaytimeConfig.builder()
                .afkThresholdMillis(seconds(values, KEY_AFK, 300, 5, 86400, logger))
                .minSessionMillis(seconds(values, KEY_MIN_SESSION, 30, 0, 3600, logger))
                .autosaveIntervalMillis(seconds(values, KEY_AUTOSAVE, 60, 5, 3600, logger))
                .retentionDays(integer(values, KEY_RETENTION, 90, 1, 3650, logger))
                .build();

        return new PlaytimeSettings(config, bool(values, KEY_DEBUG, logger));
    }

    private static long seconds(Map<String, String> values, String key,
                                int fallback, int min, int max, Logger logger) {
        return integer(values, key, fallback, min, max, logger) * 1000L;
    }

    private static int integer(Map<String, String> values, String key,
                               int fallback, int min, int max, Logger logger) {
        String raw = values.get(key);
        if (raw == null) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            logger.warn("{} is not a whole number in the settings ('{}'); using {}.",
                    key, raw, fallback);
            return fallback;
        }
        if (parsed < min || parsed > max) {
            logger.warn("{} must be between {} and {} ('{}'); using {}.",
                    key, min, max, raw, fallback);
            return fallback;
        }
        return parsed;
    }

    private static boolean bool(Map<String, String> values, String key, Logger logger) {
        String raw = values.get(key);
        if (raw == null) {
            return false;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        logger.warn("{} must be true or false ('{}'); using false.", key, raw);
        return false;
    }

    public PlaytimeConfig getConfig() {
        return config;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }
}

package fr.julien.actuallyplayed.forge.config;

import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the Forge configuration file onto the Minecraft-free {@link PlaytimeConfig}.
 * <p>
 * The file is expressed in seconds and days because that is what a player reasons in; the
 * engine works in milliseconds.
 */
public final class ForgeConfig {

    static final String CATEGORY_TRACKING = "tracking";
    static final String CATEGORY_STORAGE = "storage";
    static final String CATEGORY_DEBUG = "debug";

    private static Configuration configuration;
    private static boolean debugLogging;

    private ForgeConfig() {
    }

    /**
     * Reads the configuration file, creating it with defaults on first run.
     * <p>
     * The {@link Configuration} is kept afterwards so the in-game settings screen can edit
     * the same instance rather than a second, diverging copy.
     */
    public static PlaytimeConfig load(File file) {
        configuration = new Configuration(file);
        configuration.load();
        return read();
    }

    /**
     * Re-reads the file into a fresh {@link PlaytimeConfig}. Called again after an in-game
     * edit; the caller publishes the result, which is what makes the swap atomic.
     */
    /**
     * Reads one setting and tags it with a language key.
     * <p>
     * Without the key, Forge's config screen falls back to the raw property name and the
     * English comment — so a French player got an English settings screen from a mod that
     * ships a French translation. {@code GuiConfig} looks up {@code key} for the label and
     * {@code key + ".tooltip"} for the hover text.
     */
    private static Property tagged(String name, String category, int defaultValue,
                                   int min, int max, String comment) {
        Property property = configuration.get(category, name, defaultValue, comment, min, max);
        property.setLanguageKey("actuallyplayed.config." + name);
        return property;
    }

    public static PlaytimeConfig read() {
        configuration.getCategory(CATEGORY_TRACKING)
                .setLanguageKey("actuallyplayed.config.category.tracking");
        configuration.getCategory(CATEGORY_STORAGE)
                .setLanguageKey("actuallyplayed.config.category.storage");
        configuration.getCategory(CATEGORY_DEBUG)
                .setLanguageKey("actuallyplayed.config.category.debug");
        int afkSeconds = tagged("afkThresholdSeconds", CATEGORY_TRACKING,
                (int) (PlaytimeConfig.DEFAULT_AFK_THRESHOLD_MILLIS / 1000L), 10, 3600,
                "Inactivity, in seconds, after which the counter stops. The elapsed idle time is "
                        + "then removed from the played total and moved to the AFK total.").getInt();

        int minSessionSeconds = tagged("minSessionSeconds", CATEGORY_TRACKING,
                (int) (PlaytimeConfig.DEFAULT_MIN_SESSION_MILLIS / 1000L), 0, 3600,
                "Sessions shorter than this are discarded entirely, so brief visits do not "
                        + "clutter the list. Set to 0 to keep every session.").getInt();

        int autosaveSeconds = tagged("autosaveIntervalSeconds", CATEGORY_STORAGE,
                (int) (PlaytimeConfig.DEFAULT_AUTOSAVE_INTERVAL_MILLIS / 1000L), 10, 3600,
                "How often the data file is written. This also bounds how much of an ongoing "
                        + "session a crash can cost you.").getInt();

        int retentionDays = tagged("retentionDays", CATEGORY_STORAGE,
                PlaytimeConfig.DEFAULT_RETENTION_DAYS, 1, 3650,
                "How long individual sessions are kept in full detail. Older ones are merged "
                        + "into monthly summaries; no playtime is ever lost, only the detail.").getInt();

        Property debugProperty = configuration.get(CATEGORY_DEBUG, "debugLogging", false,
                "Logs every switch between playing and AFK, plus session start and end. Off by "
                        + "default: the mod is meant to be silent. Turn it on to check that "
                        + "activity detection behaves as you expect.");
        debugProperty.setLanguageKey("actuallyplayed.config.debugLogging");
        debugLogging = debugProperty.getBoolean();

        if (configuration.hasChanged()) {
            configuration.save();
        }

        return PlaytimeConfig.builder()
                .afkThresholdMillis(afkSeconds * 1000L)
                // Clamped rather than passed straight through: 0 is a legitimate setting
                // here, but the core rejects a negative value.
                .minSessionMillis(Math.max(0, minSessionSeconds) * 1000L)
                .autosaveIntervalMillis(autosaveSeconds * 1000L)
                .retentionDays(retentionDays)
                .build();
    }

    /** Whether to log activity transitions. Read live, so the setting applies immediately. */
    public static boolean isDebugLogging() {
        return debugLogging;
    }

    static Configuration getConfiguration() {
        return configuration;
    }

    /** Categories exposed by the in-game settings screen. */
    static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        elements.addAll(new ConfigElement(configuration.getCategory(CATEGORY_TRACKING)).getChildElements());
        elements.addAll(new ConfigElement(configuration.getCategory(CATEGORY_STORAGE)).getChildElements());
        elements.addAll(new ConfigElement(configuration.getCategory(CATEGORY_DEBUG)).getChildElements());
        return elements;
    }
}

package fr.julien.actuallyplayed.common;

import fr.julien.actuallyplayed.common.config.PlaytimeSettings;
import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import fr.julien.actuallyplayed.core.engine.SystemClock;
import fr.julien.actuallyplayed.core.model.TrackedSession;
import fr.julien.actuallyplayed.core.storage.JsonPlaytimeStore;
import fr.julien.actuallyplayed.core.storage.UnsupportedSchemaException;
import fr.julien.actuallyplayed.core.util.DurationFormatter;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;

/**
 * Starts and stops tracking. Everything each loader has to do is call {@link #start} once and
 * then {@link #tick} every client tick.
 * <p>
 * The mod is client-only: it needs no server-side counterpart and works on any vanilla or
 * modded server. This class does nothing but wiring — the tracking rules live in the
 * Minecraft-free {@code core} module.
 */
public final class ActuallyPlayed {

    public static final String MOD_ID = "actuallyplayed";
    public static final String MOD_NAME = "Actually Played";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PlaytimeClient client;

    private ActuallyPlayed() {
    }

    /**
     * @param configDir the directory the data file and the settings live in
     * @return {@code false} if the stored data could not be opened, in which case the mod stays
     *         loaded but records nothing — far better than overwriting a file we failed to
     *         understand
     */
    public static synchronized boolean start(Path configDir) {
        if (client != null) {
            return true;
        }

        PlaytimeSettings settings = PlaytimeSettings.load(configDir, LOGGER);
        PlaytimeConfig config = settings.getConfig();

        JsonPlaytimeStore store = new JsonPlaytimeStore(configDir.resolve("playtime.json"));
        PlaytimeTracker tracker =
                new PlaytimeTracker(store, config, SystemClock.INSTANCE, ZoneId.systemDefault());

        try {
            PlaytimeTracker.StartupReport report = tracker.start();
            logStartup(store, report);
        } catch (UnsupportedSchemaException e) {
            LOGGER.error("{} Tracking is disabled to protect your history.", e.getMessage());
            return false;
        } catch (IOException e) {
            LOGGER.error("Could not read the playtime data file at {}", store.getFile(), e);
            return false;
        }

        client = new PlaytimeClient(tracker, Minecraft.getInstance(), LOGGER, settings.isDebugLogging());

        // Minecraft can exit through System.exit, and no loader event catches every path. A
        // shutdown hook does, so the session in progress is closed and written rather than
        // being left for crash recovery.
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown, MOD_ID + "-shutdown"));

        LOGGER.info("{} active (client-side only).", MOD_NAME);
        return true;
    }

    /** Call from the loader's client tick event. Does nothing until {@link #start} succeeds. */
    public static void tick() {
        PlaytimeClient current = client;
        if (current != null) {
            current.onClientTick();
        }
    }

    /** @return the tracker, or {@code null} while the mod is inactive */
    public static PlaytimeTracker tracker() {
        PlaytimeClient current = client;
        return current == null ? null : current.getTracker();
    }

    private static void logStartup(JsonPlaytimeStore store, PlaytimeTracker.StartupReport report) {
        if (store.getLastQuarantinedFile() != null) {
            LOGGER.warn("The playtime data file was unreadable and has been set aside as {}. "
                            + "Tracking restarts from scratch; your old file is still there.",
                    store.getLastQuarantinedFile());
        }

        TrackedSession recovered = report.getRecoveredSession();
        if (recovered != null) {
            LOGGER.info("Recovered {} from a session the last run did not close ({} played, {} AFK).",
                    DurationFormatter.format(recovered.getTotalMillis()),
                    DurationFormatter.format(recovered.getActiveMillis()),
                    DurationFormatter.format(recovered.getAfkMillis()));
        }
        if (report.getCompactedSessions() > 0) {
            LOGGER.info("Compacted {} old session(s) into monthly summaries.", report.getCompactedSessions());
        }
    }
}

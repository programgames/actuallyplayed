package fr.julien.playtimetracker.forge;

import fr.julien.playtimetracker.core.PlaytimeTracker;
import fr.julien.playtimetracker.core.config.PlaytimeConfig;
import fr.julien.playtimetracker.core.engine.SystemClock;
import fr.julien.playtimetracker.core.model.TrackedSession;
import fr.julien.playtimetracker.core.storage.JsonPlaytimeStore;
import fr.julien.playtimetracker.core.storage.UnsupportedSchemaException;
import fr.julien.playtimetracker.core.util.DurationFormatter;
import fr.julien.playtimetracker.forge.config.ConfigChangeHandler;
import fr.julien.playtimetracker.forge.config.ForgeConfig;
import fr.julien.playtimetracker.forge.event.PlaytimeClientHandler;
import fr.julien.playtimetracker.forge.event.StatsGuiHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;

/**
 * Entry point of Playtime Tracker.
 * <p>
 * The mod is client-only: it needs no server-side counterpart and works on any vanilla or
 * modded server. This class does nothing but wiring — the tracking rules live in the
 * Minecraft-free {@code core} module.
 */
@Mod(
        modid = Reference.MOD_ID,
        name = Reference.MOD_NAME,
        version = Reference.VERSION,
        acceptedMinecraftVersions = Reference.ACCEPTED_MC_VERSIONS,
        clientSideOnly = true,
        // The mod stores nothing on the server and sends no packets, so joining a server
        // that does not have it must never be blocked.
        acceptableRemoteVersions = "*",
        guiFactory = "fr.julien.playtimetracker.forge.config.PlaytimeGuiFactory"
)
public final class PlaytimeTrackerMod {

    private Logger logger;
    private PlaytimeTracker tracker;
    private JsonPlaytimeStore store;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        this.logger = event.getModLog();

        File directory = new File(event.getModConfigurationDirectory(), Reference.MOD_ID);
        PlaytimeConfig config = ForgeConfig.load(new File(directory, Reference.MOD_ID + ".cfg"));

        Path dataFile = new File(directory, "playtime.json").toPath();
        store = new JsonPlaytimeStore(dataFile);
        tracker = new PlaytimeTracker(store, config, SystemClock.INSTANCE, ZoneId.systemDefault());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!startTracking()) {
            logger.warn("{} is loaded but inactive: playtime will not be recorded.", Reference.MOD_NAME);
            return;
        }

        final PlaytimeClientHandler handler =
                new PlaytimeClientHandler(tracker, Minecraft.getMinecraft(), logger);
        MinecraftForge.EVENT_BUS.register(handler);
        MinecraftForge.EVENT_BUS.register(new StatsGuiHandler(tracker));
        MinecraftForge.EVENT_BUS.register(new ConfigChangeHandler(tracker));

        // Minecraft has no reliable "game is closing" event in 1.12.2, and it can exit
        // through System.exit. A shutdown hook is the only thing that catches every path,
        // so the session in progress is closed and written instead of being left for
        // crash recovery.
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                handler.shutdown();
            }
        }, Reference.MOD_ID + "-shutdown"));

        logger.info("{} {} active (client-side only).", Reference.MOD_NAME, Reference.VERSION);
    }

    /**
     * @return {@code false} if the stored data could not be opened, in which case the mod
     *         stays loaded but records nothing — far better than overwriting a file we
     *         failed to understand
     */
    private boolean startTracking() {
        try {
            PlaytimeTracker.StartupReport report = tracker.start();
            logStartup(report);
            return true;
        } catch (UnsupportedSchemaException e) {
            logger.error("{} Tracking is disabled to protect your history.", e.getMessage());
            return false;
        } catch (IOException e) {
            logger.error("Could not read the playtime data file at {}", store.getFile(), e);
            return false;
        }
    }

    private void logStartup(PlaytimeTracker.StartupReport report) {
        if (store.getLastQuarantinedFile() != null) {
            logger.warn("The playtime data file was unreadable and has been set aside as {}. "
                            + "Tracking restarts from scratch; your old file is still there.",
                    store.getLastQuarantinedFile());
        }

        TrackedSession recovered = report.getRecoveredSession();
        if (recovered != null) {
            logger.info("Recovered {} from a session the last run did not close ({} played, {} AFK).",
                    DurationFormatter.format(recovered.getTotalMillis()),
                    DurationFormatter.format(recovered.getActiveMillis()),
                    DurationFormatter.format(recovered.getAfkMillis()));
        }
        if (report.getCompactedSessions() > 0) {
            logger.info("Compacted {} old session(s) into monthly summaries.", report.getCompactedSessions());
        }
    }
}

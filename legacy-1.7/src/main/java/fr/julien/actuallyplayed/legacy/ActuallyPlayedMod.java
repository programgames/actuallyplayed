package fr.julien.actuallyplayed.legacy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import fr.julien.actuallyplayed.core.engine.SystemClock;
import fr.julien.actuallyplayed.core.model.TrackedSession;
import fr.julien.actuallyplayed.core.storage.JsonPlaytimeStore;
import fr.julien.actuallyplayed.core.storage.UnsupportedSchemaException;
import fr.julien.actuallyplayed.core.util.DurationFormatter;
import fr.julien.actuallyplayed.legacy.config.LegacySettings;
import fr.julien.actuallyplayed.legacy.event.PlaytimeClientHandler;
import fr.julien.actuallyplayed.legacy.event.StatsGuiHandler;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;

/**
 * Entry point on Minecraft 1.7.10.
 * <p>
 * The mod is client-only and does nothing but wiring - the tracking rules live in the
 * Minecraft-free {@code core} module, shared with every other version this project ships.
 */
@Mod(
        modid = Reference.MOD_ID,
        name = Reference.MOD_NAME,
        version = Reference.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        // The mod stores nothing on the server and sends no packets, so joining a server that
        // does not have it must never be blocked. 1.7.10's @Mod has no clientSideOnly flag;
        // the client-only nature comes from the code touching nothing server-side.
        acceptableRemoteVersions = "*"
)
public final class ActuallyPlayedMod {

    private Logger logger;
    private PlaytimeTracker tracker;
    private JsonPlaytimeStore store;
    private LegacySettings settings;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        this.logger = event.getModLog();

        File directory = new File(event.getModConfigurationDirectory(), Reference.MOD_ID);
        if (!directory.exists() && !directory.mkdirs()) {
            logger.warn("Could not create {}; the defaults will be used.", directory);
        }

        settings = LegacySettings.load(directory.toPath(), logger);
        PlaytimeConfig config = settings.getConfig();

        store = new JsonPlaytimeStore(new File(directory, "playtime.json").toPath());
        tracker = new PlaytimeTracker(store, config, SystemClock.INSTANCE, ZoneId.systemDefault());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!startTracking()) {
            logger.warn("{} is loaded but inactive: playtime will not be recorded.", Reference.MOD_NAME);
            return;
        }

        final PlaytimeClientHandler handler = new PlaytimeClientHandler(
                tracker, Minecraft.getMinecraft(), logger, settings.isDebugLogging());
        // Two buses, and putting a handler on the wrong one fails silently. On 1.7.10 the tick
        // and input events live on FML's bus, while GUI events live on Forge's. Registering
        // everything on Forge's left the button working and the counter never ticking, with no
        // error anywhere.
        FMLCommonHandler.instance().bus().register(handler);
        MinecraftForge.EVENT_BUS.register(new StatsGuiHandler(tracker));

        // 1.7.10 has no reliable "game is closing" event, and it can exit through System.exit.
        // A shutdown hook is the only thing that catches every path.
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                handler.shutdown();
            }
        }, Reference.MOD_ID + "-shutdown"));

        logger.info("{} {} active (client-side only).", Reference.MOD_NAME, Reference.VERSION);
    }

    /**
     * @return {@code false} if the stored data could not be opened, in which case the mod stays
     *         loaded but records nothing - far better than overwriting a file we failed to
     *         understand
     */
    private boolean startTracking() {
        try {
            logStartup(tracker.start());
            return true;
        } catch (UnsupportedSchemaException e) {
            logger.error("{} Tracking is disabled to protect your history.", e.getMessage());
            return false;
        } catch (IOException e) {
            logger.error("Could not read the playtime data file at " + store.getFile(), e);
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

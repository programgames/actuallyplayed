package fr.julien.playtimetracker.forge.config;

import fr.julien.playtimetracker.core.PlaytimeTracker;
import fr.julien.playtimetracker.forge.Reference;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Applies settings edited from the in-game screen, without a restart.
 * <p>
 * A fresh immutable configuration is built from the file and published in one assignment,
 * so no reader can ever observe a half-updated set of values. Every setting is safe to
 * change mid-session: the engine reads the AFK threshold on each evaluation rather than
 * caching it, and the autosave interval is compared against the clock on every tick.
 */
public final class ConfigChangeHandler {

    private final PlaytimeTracker tracker;

    public ConfigChangeHandler(PlaytimeTracker tracker) {
        this.tracker = tracker;
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Reference.MOD_ID.equals(event.getModID())) {
            tracker.setConfig(ForgeConfig.read());
        }
    }
}

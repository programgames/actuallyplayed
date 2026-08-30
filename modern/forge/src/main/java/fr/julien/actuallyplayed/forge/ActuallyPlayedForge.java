package fr.julien.actuallyplayed.forge;

import fr.julien.actuallyplayed.common.ActuallyPlayed;
import fr.julien.actuallyplayed.common.client.PlaytimeStatsScreen;
import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Forge entry point. Translates two Forge events into calls on the shared adapter and does
 * nothing else — every tracking rule lives in {@code core}, and everything version-specific
 * but loader-neutral lives in {@code common}.
 * <p>
 * The whole loader-specific surface of this mod is this file. That is the return on the tick
 * polling: with seven event subscriptions it would have been seven translations here and seven
 * Mixins on Fabric.
 */
@Mod(ActuallyPlayed.MOD_ID)
public final class ActuallyPlayedForge {

    public ActuallyPlayedForge() {
        if (!FMLEnvironment.dist.isClient()) {
            // Client-only by design. The mod is marked as such, but a dedicated server that
            // loads it anyway must not start a tracker with no client to read.
            return;
        }

        Path configDir = FMLPaths.CONFIGDIR.get().resolve(ActuallyPlayed.MOD_ID);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create " + configDir, e);
        }

        // TODO(phase 2): read the settings from a Forge config file, as the 1.12 build does.
        // Defaults keep the milestone honest: the mod records correctly, it is simply not yet
        // configurable here.
        if (ActuallyPlayed.start(configDir, PlaytimeConfig.defaults(), false)) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ActuallyPlayed.tick();
        }
    }

    /**
     * Grafts the button onto the vanilla Statistics screen.
     * <p>
     * Placed under the vanilla buttons rather than among them, so a resource pack or another
     * mod rearranging that screen does not overlap it.
     */
    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof StatsScreen stats)) {
            return;
        }
        if (ActuallyPlayed.tracker() == null) {
            return;
        }
        event.addListener(Button.builder(
                        Component.translatable("actuallyplayed.gui.button"),
                        button -> stats.getMinecraft().setScreen(
                                new PlaytimeStatsScreen(stats, ActuallyPlayed.tracker())))
                .bounds(stats.width / 2 - 100, stats.height - 52, 200, 20)
                .build());
    }
}

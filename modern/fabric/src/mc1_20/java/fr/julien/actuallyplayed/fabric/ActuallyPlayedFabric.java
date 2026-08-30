package fr.julien.actuallyplayed.fabric;

import fr.julien.actuallyplayed.common.ActuallyPlayed;
import fr.julien.actuallyplayed.common.client.PlaytimeStatsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fabric entry point, and the twin of {@code ActuallyPlayedForge}.
 * <p>
 * The two files say the same thing in each loader's dialect: start the adapter, feed it the
 * client tick, graft a button onto the vanilla Statistics screen. Everything else — the
 * measurement, the storage, the screen's contents, the target resolution — is shared. That the
 * loader-specific half fits on one page each is the whole point of the architecture, and it is
 * why this mod needs no cross-loader runtime library. See {@code PORTING.md} §3.3.
 */
public final class ActuallyPlayedFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(ActuallyPlayed.MOD_ID);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create " + configDir, e);
        }

        if (!ActuallyPlayed.start(configDir)) {
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> ActuallyPlayed.tick());

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof StatsScreen stats) || ActuallyPlayed.tracker() == null) {
                return;
            }
            // Top right, for the reason spelled out in the Forge entry point: the bottom of
            // the vanilla screen is fully occupied by the tabs and Done.
            Screens.getButtons(screen).add(Button.builder(
                            Component.translatable("actuallyplayed.gui.button"),
                            button -> client.setScreen(
                                    new PlaytimeStatsScreen(stats, ActuallyPlayed.tracker())))
                    .bounds(width - 110, 6, 100, 20)
                    .build());
        });
    }
}

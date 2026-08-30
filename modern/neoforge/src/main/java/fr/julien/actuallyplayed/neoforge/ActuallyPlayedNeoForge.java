package fr.julien.actuallyplayed.neoforge;

import fr.julien.actuallyplayed.common.ActuallyPlayed;
import fr.julien.actuallyplayed.common.client.PlaytimeStatsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * NeoForge entry point, and the twin of the Forge and Fabric ones.
 * <p>
 * NeoForge replaced Forge on modern Minecraft versions, and its API is close enough that this
 * file differs from the 1.20.1 Forge one mostly in package names: {@code net.neoforged} rather
 * than {@code net.minecraftforge}, and a client tick event that no longer carries a phase.
 */
@Mod(value = ActuallyPlayed.MOD_ID, dist = Dist.CLIENT)
public final class ActuallyPlayedNeoForge {

    public ActuallyPlayedNeoForge() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(ActuallyPlayed.MOD_ID);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create " + configDir, e);
        }

        if (ActuallyPlayed.start(configDir)) {
            NeoForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        ActuallyPlayed.tick();
    }

    /**
     * Grafts the button onto the vanilla Statistics screen.
     * <p>
     * Top right, because the bottom of that screen is fully occupied: the General / Items /
     * Mobs tabs sit at {@code height - 52} and Done at {@code height - 28}. A button placed
     * there lands on top of the tabs, which is what the first 1.20 attempt did.
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
                .bounds(stats.width - 110, 6, 100, 20)
                .build());
    }
}

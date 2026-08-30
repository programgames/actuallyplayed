package fr.julien.actuallyplayed.forge.client;

import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.forge.client.gui.GuiPlaytimeStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Opens the statistics screen from a key.
 * <p>
 * Registered <em>unbound</em>: a mod that claims a key by default eventually collides with
 * one the player already uses, and this screen is not something anyone needs on a hotkey
 * until they decide they do. It appears in Options → Controls under the mod's own category,
 * ready to be assigned.
 */
public final class PlaytimeKeyBindings {

    private static final String CATEGORY = "key.categories.actuallyplayed";

    private final PlaytimeTracker tracker;
    private final Minecraft minecraft;
    private final KeyBinding openStats;

    public PlaytimeKeyBindings(PlaytimeTracker tracker, Minecraft minecraft) {
        this.tracker = tracker;
        this.minecraft = minecraft;
        this.openStats = new KeyBinding("key.actuallyplayed.open", Keyboard.KEY_NONE, CATEGORY);
        ClientRegistry.registerKeyBinding(openStats);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            // isPressed consumes the press, so it fires once per keystroke.
            if (openStats.isPressed() && minecraft.currentScreen == null && minecraft.world != null) {
                // No parent: opened from the world, Done and ESC return to the world.
                minecraft.displayGuiScreen(new GuiPlaytimeStats(null, tracker));
            }
        } catch (Throwable ignored) {
            // A key binding must never be the reason a game crashes.
        }
    }
}

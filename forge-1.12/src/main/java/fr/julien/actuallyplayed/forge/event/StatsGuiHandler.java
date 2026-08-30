package fr.julien.actuallyplayed.forge.event;

import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.forge.client.gui.GuiPlaytimeStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.achievement.GuiStats;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Grafts a Playtime button onto the vanilla statistics screen.
 * <p>
 * The vanilla screen itself is left untouched: we add a button and open a screen of our
 * own, rather than trying to squeeze durations into a page built for plain counters.
 */
public final class StatsGuiHandler {

    /**
     * Deliberately far from the small ids vanilla uses for its own buttons, so the two
     * cannot collide when the action event fires.
     */
    private static final int BUTTON_PLAYTIME = 7913;

    private final PlaytimeTracker tracker;

    public StatsGuiHandler(PlaytimeTracker tracker) {
        this.tracker = tracker;
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiStats)) {
            return;
        }
        int x = event.getGui().width - 110;
        event.getButtonList().add(new GuiButton(
                BUTTON_PLAYTIME, x, 6, 100, 20, I18n.format("actuallyplayed.gui.button")));
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiStats)) {
            return;
        }
        if (event.getButton().id != BUTTON_PLAYTIME) {
            return;
        }
        // Cancelled so the vanilla screen does not also react to an id it does not know.
        event.setCanceled(true);
        Minecraft.getMinecraft().displayGuiScreen(new GuiPlaytimeStats(event.getGui(), tracker));
    }
}

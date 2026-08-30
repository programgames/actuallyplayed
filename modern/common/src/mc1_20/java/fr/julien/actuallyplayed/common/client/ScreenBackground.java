package fr.julien.actuallyplayed.common.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * The one call that differs between the Minecraft versions this mod targets.
 * <p>
 * On 1.20.1 {@code Screen.renderBackground} takes the graphics context alone; from 1.21 it
 * also takes the mouse position and the partial tick. Compiling the whole adapter against
 * 1.21.1 turned up this and nothing else — not the target resolution, not the input polling,
 * not {@code core}. So rather than a preprocessor, the difference lives in a source directory
 * per version, selected by the build. See {@code PORTING.md} §3.7.
 * <p>
 * It lands in the draw loop, which is where §4.2 predicted a version break would reach, and is
 * the reason the screen's layout was moved into {@code core} in the first place.
 */
final class ScreenBackground {

    private ScreenBackground() {
    }

    static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        screen.renderBackground(graphics);
    }
}

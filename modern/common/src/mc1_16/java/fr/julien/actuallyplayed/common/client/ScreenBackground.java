package fr.julien.actuallyplayed.common.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;

/**
 * The 1.16 spelling of the screen background. See the 1.20 twin for why this class exists.
 */
final class ScreenBackground {

    private ScreenBackground() {
    }

    static void render(Screen screen, PoseStack poses, int mouseX, int mouseY, float partialTick) {
        screen.renderBackground(poses);
    }
}

package fr.julien.actuallyplayed.common.bridge;

import net.minecraft.client.player.LocalPlayer;

/**
 * Where the player is looking.
 * <p>
 * Getters since 1.17; public fields before that, which is why this is one class per version
 * rather than a call in {@code PlaytimeClient}. See {@code PORTING.md} section 3.7.
 */
public final class PlayerRotation {

    private PlayerRotation() {
    }

    public static float yaw(LocalPlayer player) {
        return player.yRot;
    }

    public static float pitch(LocalPlayer player) {
        return player.xRot;
    }
}

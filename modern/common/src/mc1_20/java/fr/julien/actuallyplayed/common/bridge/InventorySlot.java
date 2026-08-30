package fr.julien.actuallyplayed.common.bridge;

import net.minecraft.client.player.LocalPlayer;

/**
 * The hotbar slot the player has selected.
 * <p>
 * Reached through a getter since 1.17, through a public field before that. The mouse wheel
 * leaves no state of its own, so this is how its use is detected.
 */
public final class InventorySlot {

    private InventorySlot() {
    }

    public static int selected(LocalPlayer player) {
        return player.getInventory().selected;
    }
}

package fr.julien.actuallyplayed.common.bridge;

import net.minecraft.client.User;

import java.util.UUID;

/**
 * The UUID of the account currently logged in.
 * <p>
 * 1.17 added {@code getProfileId}; before that the id was a string on the user's profile.
 * Returns {@code null} when there is none, which the caller turns into an offline key.
 */
public final class PlayerAccount {

    private PlayerAccount() {
    }

    public static UUID uuid(User user) {
        return user.getProfileId();
    }
}

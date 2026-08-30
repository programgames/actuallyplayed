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
        // 1.16 stores the id as a string on the user, and it may be malformed on an offline
        // profile: a bad value must not stop tracking, only fall back to the offline key.
        try {
            return UUID.fromString(user.getUuid());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}

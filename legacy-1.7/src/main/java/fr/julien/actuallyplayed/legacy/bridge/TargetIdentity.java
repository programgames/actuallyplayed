package fr.julien.actuallyplayed.legacy.bridge;

import fr.julien.actuallyplayed.core.model.TargetKey;

import java.util.Objects;

/**
 * Where the player currently is: the stable key plus the label to show for it.
 */
public final class TargetIdentity {

    private final TargetKey key;
    private final String displayName;

    public TargetIdentity(TargetKey key, String displayName) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName;
    }

    public TargetKey getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }
}

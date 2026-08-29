package fr.julien.playtimetracker.core.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable identity of a tracked target — one server or one singleplayer world.
 * <p>
 * The key is deliberately technical and stable: a server is identified by its
 * {@code host:port}, never by the display name the player gave it in their server list,
 * because that name can be renamed at any time. The display name is stored separately,
 * as mutable metadata.
 * <p>
 * Networks that route several minigames behind one address (Hypixel and friends) collapse
 * into a single key. That is intentional: Minecraft 1.12.2 gives the client no reliable
 * way to tell BungeeCord sub-servers apart.
 */
public final class TargetKey {

    private static final char SEPARATOR = ':';

    private final TargetType type;
    private final String id;

    private TargetKey(TargetType type, String id) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = requireNonBlank(id);
    }

    /** @param hostAndPort the server address, e.g. {@code mc.hypixel.net:25565} */
    public static TargetKey server(String hostAndPort) {
        return new TargetKey(TargetType.SERVER, hostAndPort);
    }

    /** @param saveFolderName the world's save folder name, not its display name */
    public static TargetKey singleplayer(String saveFolderName) {
        return new TargetKey(TargetType.SINGLEPLAYER, saveFolderName);
    }

    public TargetType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    /**
     * Stable textual form used as a JSON object key.
     * <p>
     * Only the first separator is significant when parsing back, so an id containing
     * a colon (a {@code host:port} always does) round-trips safely.
     */
    public String serialize() {
        return type.name().toLowerCase(Locale.ROOT) + SEPARATOR + id;
    }

    public static TargetKey deserialize(String serialized) {
        Objects.requireNonNull(serialized, "serialized");
        int separator = serialized.indexOf(SEPARATOR);
        if (separator <= 0 || separator == serialized.length() - 1) {
            throw new IllegalArgumentException("Malformed target key: " + serialized);
        }
        String rawType = serialized.substring(0, separator);
        String id = serialized.substring(separator + 1);
        TargetType type;
        try {
            type = TargetType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown target type in key: " + serialized, e);
        }
        return new TargetKey(type, id);
    }

    private static String requireNonBlank(String value) {
        Objects.requireNonNull(value, "id");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Target id must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TargetKey)) {
            return false;
        }
        TargetKey other = (TargetKey) o;
        return type == other.type && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + id.hashCode();
    }

    @Override
    public String toString() {
        return serialize();
    }
}

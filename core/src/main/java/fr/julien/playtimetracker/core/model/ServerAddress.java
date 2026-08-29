package fr.julien.playtimetracker.core.model;

import java.util.Locale;

/**
 * Normalises a server address so one destination always yields one key.
 * <p>
 * {@code Mc.Hypixel.Net} and {@code mc.hypixel.net:25565} are the same server. Left as
 * typed, they would become two separate rows in the stats screen and split a player's
 * history in half.
 * <p>
 * Lives in {@code core} rather than beside the Minecraft code because it is pure text
 * handling with several edge cases — exactly the kind of logic worth testing without
 * launching a game.
 */
public final class ServerAddress {

    /** The port Minecraft assumes when the player types a bare host name. */
    public static final int DEFAULT_PORT = 25565;

    private ServerAddress() {
    }

    /**
     * @param rawAddress the address as Minecraft stores it in the server list
     * @return {@code host:port}, lower-cased, with the default port supplied when absent
     */
    public static String normalize(String rawAddress) {
        String address = rawAddress == null ? "" : rawAddress.trim().toLowerCase(Locale.ROOT);
        if (address.isEmpty()) {
            return "unknown";
        }
        return hasPort(address) ? address : address + ":" + DEFAULT_PORT;
    }

    private static boolean hasPort(String address) {
        if (address.startsWith("[")) {
            // An IPv6 literal is bracketed, and the colons inside it are part of the
            // address rather than a port separator. Only a colon after the closing
            // bracket introduces a port.
            int closing = address.indexOf(']');
            return closing >= 0 && address.indexOf(':', closing) > closing;
        }
        // A bare IPv6 address (several colons, no brackets) has no port either.
        int firstColon = address.indexOf(':');
        if (firstColon < 0) {
            return false;
        }
        return address.indexOf(':', firstColon + 1) < 0;
    }
}

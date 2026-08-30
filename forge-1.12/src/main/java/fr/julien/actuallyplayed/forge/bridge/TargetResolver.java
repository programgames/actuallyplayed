package fr.julien.actuallyplayed.forge.bridge;

import com.mojang.authlib.GameProfile;
import fr.julien.actuallyplayed.core.model.ServerAddress;
import fr.julien.actuallyplayed.core.model.TargetKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Session;

import java.util.Locale;
import java.util.UUID;

/**
 * Answers "where is the player right now?" and "which account is playing?" from the
 * Minecraft client.
 * <p>
 * Everything version-specific about identifying a target lives here, so porting to another
 * Minecraft version means rewriting this class and nothing in {@code core}.
 */
public final class TargetResolver {

    private final Minecraft minecraft;

    public TargetResolver(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /**
     * @return where the player is, or {@code null} when they are not in a world (main menu,
     *         loading screen) or on a destination we cannot identify
     */
    public TargetIdentity resolve() {
        if (minecraft.world == null || minecraft.player == null) {
            return null;
        }

        if (minecraft.isSingleplayer()) {
            IntegratedServer server = minecraft.getIntegratedServer();
            if (server == null) {
                return null;
            }
            // The save folder, not the world's display name: renaming a world in the UI
            // must not split its history in two.
            return new TargetIdentity(
                    TargetKey.singleplayer(server.getFolderName()),
                    server.getWorldName());
        }

        ServerData server = minecraft.getCurrentServerData();
        if (server == null) {
            // Realms, and any other connection Minecraft does not describe as a server
            // entry. Nothing stable to key on, so we do not track it.
            return null;
        }
        return new TargetIdentity(
                TargetKey.server(ServerAddress.normalize(server.serverIP)),
                server.serverName);
    }

    /**
     * Identity of the account currently logged in.
     * <p>
     * Taken from the Mojang session rather than from the player entity: on an offline-mode
     * server the entity's UUID is derived from the name and differs from server to server,
     * which would scatter one account's history across several entries.
     */
    public String resolvePlayerId() {
        Session session = minecraft.getSession();
        if (session == null) {
            return "unknown";
        }
        GameProfile profile = session.getProfile();
        UUID id = profile == null ? null : profile.getId();
        if (id != null) {
            return id.toString();
        }
        String username = session.getUsername();
        return "offline:" + (username == null ? "unknown" : username.toLowerCase(Locale.ROOT));
    }
}

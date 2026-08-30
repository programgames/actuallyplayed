package fr.julien.actuallyplayed.legacy.bridge;

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
 * Answers "where is the player right now?" and "which account is playing?" on Minecraft 1.7.10.
 * <p>
 * The 1.12 counterpart is the same twenty lines against slightly different names.
 */
public final class TargetResolver {

    private final Minecraft minecraft;

    public TargetResolver(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /**
     * @return where the player is, or {@code null} when they are not in a world or on a
     *         destination we cannot identify
     */
    public TargetIdentity resolve() {
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            return null;
        }

        if (minecraft.isSingleplayer()) {
            IntegratedServer server = minecraft.getIntegratedServer();
            if (server == null) {
                return null;
            }
            // The save folder, not the world's display name: renaming a world in the UI must
            // not split its history in two.
            return new TargetIdentity(
                    TargetKey.singleplayer(server.getFolderName()),
                    server.getWorldName());
        }

        ServerData server = currentServerData();
        if (server == null) {
            // Nothing stable to key on, so we do not track it - see CLAUDE.md section 2.4.
            return null;
        }
        return new TargetIdentity(
                TargetKey.server(ServerAddress.normalize(server.serverIP)),
                server.serverName);
    }

    /**
     * The server the client is connected to.
     * <p>
     * <strong>The one obfuscated name in this project.</strong> {@code CLAUDE.md} section 6
     * forbids them, and rightly so on 1.12 where the mappings are near complete. On 1.7.10 this
     * getter simply has no mapped name: MCP left it as {@code func_147104_D}, and the field
     * behind it is private. The alternatives are worse - an access transformer for one string,
     * or digging the address out of the network handler and losing the label the player gave
     * the server in their list.
     */
    private ServerData currentServerData() {
        return minecraft.func_147104_D();
    }

    /**
     * Identity of the account currently logged in.
     * <p>
     * Taken from the session rather than from the player entity: on an offline-mode server the
     * entity's UUID is derived from the name and differs from server to server, which would
     * scatter one account's history across several entries.
     */
    public String resolvePlayerId() {
        Session session = minecraft.getSession();
        if (session == null) {
            return "unknown";
        }
        GameProfile profile = session.func_148256_e();
        UUID id = profile == null ? null : profile.getId();
        if (id != null) {
            return id.toString();
        }
        String username = session.getUsername();
        return "offline:" + (username == null ? "unknown" : username.toLowerCase(Locale.ROOT));
    }
}

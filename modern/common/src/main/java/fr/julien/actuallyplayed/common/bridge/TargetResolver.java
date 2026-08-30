package fr.julien.actuallyplayed.common.bridge;

import fr.julien.actuallyplayed.core.model.ServerAddress;
import fr.julien.actuallyplayed.core.model.TargetKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Answers "where is the player right now?" and "which account is playing?" from the Minecraft
 * client.
 * <p>
 * Everything version-specific about identifying a target lives here. The 1.12 counterpart is
 * the same twenty lines against different names, which is exactly what this module is for.
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
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        if (minecraft.hasSingleplayerServer()) {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null) {
                return null;
            }
            // The save folder, not the world's display name: renaming a world in the UI must
            // not split its history in two.
            //
            // 1.12 could ask the server for its folder name directly. Here the folder is only
            // reachable as a path, and getWorldPath is public where the storage source behind
            // it is not - which avoids needing an access transformer for one string.
            Path root = server.getWorldPath(LevelResource.ROOT).normalize();
            Path folder = root.getFileName();
            if (folder == null) {
                return null;
            }
            return new TargetIdentity(
                    TargetKey.singleplayer(folder.toString()),
                    server.getWorldData().getLevelName());
        }

        ServerData server = minecraft.getCurrentServer();
        if (server == null) {
            // Realms, and any other connection Minecraft does not describe as a server entry.
            // Nothing stable to key on, so we do not track it - see CLAUDE.md section 2.4.
            return null;
        }
        return new TargetIdentity(
                TargetKey.server(ServerAddress.normalize(server.ip)),
                server.name);
    }

    /**
     * Identity of the account currently logged in.
     * <p>
     * Taken from the client's user rather than from the player entity: on an offline-mode
     * server the entity's UUID is derived from the name and differs from server to server,
     * which would scatter one account's history across several entries.
     */
    public String resolvePlayerId() {
        User user = minecraft.getUser();
        if (user == null) {
            return "unknown";
        }
        UUID id = PlayerAccount.uuid(user);
        if (id != null) {
            return id.toString();
        }
        String username = user.getName();
        return "offline:" + (username == null ? "unknown" : username.toLowerCase(Locale.ROOT));
    }
}

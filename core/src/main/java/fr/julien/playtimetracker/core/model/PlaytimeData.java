package fr.julien.playtimetracker.core.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Root of everything the mod persists: one entry per Minecraft account.
 */
public final class PlaytimeData {

    private final Map<String, PlayerPlaytime> players = new LinkedHashMap<String, PlayerPlaytime>();

    private ProvisionalSession inProgress;

    /** Returns this account's data, creating an empty entry on first use. */
    public PlayerPlaytime player(String playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        PlayerPlaytime player = players.get(playerUuid);
        if (player == null) {
            player = new PlayerPlaytime(playerUuid);
            players.put(playerUuid, player);
        }
        return player;
    }

    public PlayerPlaytime find(String playerUuid) {
        return players.get(playerUuid);
    }

    public Collection<PlayerPlaytime> getPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public boolean isEmpty() {
        return players.isEmpty() && inProgress == null;
    }

    /**
     * The session that was in progress when the file was last written, or {@code null}.
     * <p>
     * Its presence at load time means the previous run did not close cleanly — see
     * {@link ProvisionalSession}.
     */
    public ProvisionalSession getInProgress() {
        return inProgress;
    }

    public void setInProgress(ProvisionalSession inProgress) {
        this.inProgress = inProgress;
    }

    /**
     * Records a finished session under its own player's account.
     *
     * @param displayName current label for the target, or {@code null} to keep the stored one
     */
    public void record(TrackedSession session, String displayName) {
        Objects.requireNonNull(session, "session");
        player(session.getPlayerUuid()).record(session, displayName);
    }
}

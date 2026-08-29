package fr.julien.playtimetracker.core.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.julien.playtimetracker.core.model.MonthlyAggregate;
import fr.julien.playtimetracker.core.model.PlayerPlaytime;
import fr.julien.playtimetracker.core.model.PlaytimeData;
import fr.julien.playtimetracker.core.model.ProvisionalSession;
import fr.julien.playtimetracker.core.model.TargetKey;
import fr.julien.playtimetracker.core.model.TrackedSession;
import fr.julien.playtimetracker.core.model.TrackedTarget;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Converts {@link PlaytimeData} to and from JSON.
 * <p>
 * The mapping is written by hand rather than driven by reflection. Three reasons:
 * the JSON stays a stable, documented contract the player can read and hand-edit;
 * field renames in Java cannot silently break existing files; and nothing depends on
 * reflection surviving the obfuscation of a shipped mod jar.
 * <p>
 * Unknown or malformed fragments are skipped rather than fatal — a partially damaged file
 * should cost the player the damaged entries, not their entire history.
 */
public final class PlaytimeCodec {

    /** Bump when the on-disk shape changes, and add a migration in {@link #read}. */
    public static final int SCHEMA_VERSION = 1;

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private PlaytimeCodec() {
    }

    // --- writing ------------------------------------------------------------------

    public static JsonObject write(PlaytimeData data) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);

        JsonObject players = new JsonObject();
        for (PlayerPlaytime player : data.getPlayers()) {
            players.add(player.getPlayerUuid(), writePlayer(player));
        }
        root.add("players", players);

        ProvisionalSession inProgress = data.getInProgress();
        if (inProgress != null) {
            root.add("inProgress", writeProvisional(inProgress));
        }
        return root;
    }

    private static JsonObject writePlayer(PlayerPlaytime player) {
        JsonObject targets = new JsonObject();
        for (TrackedTarget target : player.getTargets()) {
            targets.add(target.getKey().serialize(), writeTarget(target));
        }

        JsonObject json = new JsonObject();
        json.add("targets", targets);
        return json;
    }

    private static JsonObject writeTarget(TrackedTarget target) {
        JsonArray sessions = new JsonArray();
        for (TrackedSession session : target.getSessions()) {
            sessions.add(writeSession(session));
        }

        JsonObject aggregates = new JsonObject();
        for (MonthlyAggregate aggregate : target.getAggregates()) {
            aggregates.add(aggregate.getMonth().format(MONTH_FORMAT), writeAggregate(aggregate));
        }

        JsonObject json = new JsonObject();
        json.addProperty("displayName", target.getDisplayName());
        json.add("sessions", sessions);
        json.add("aggregates", aggregates);
        return json;
    }

    private static JsonObject writeProvisional(ProvisionalSession session) {
        JsonObject json = new JsonObject();
        json.addProperty("player", session.getPlayerUuid());
        json.addProperty("target", session.getTarget().serialize());
        if (session.getDisplayName() != null) {
            json.addProperty("displayName", session.getDisplayName());
        }
        json.addProperty("start", session.getStartedAt());
        json.addProperty("updated", session.getLastUpdatedAt());
        json.addProperty("active", session.getActiveMillis());
        json.addProperty("afk", session.getAfkMillis());
        return json;
    }

    private static JsonObject writeSession(TrackedSession session) {
        JsonObject json = new JsonObject();
        json.addProperty("start", session.getStartedAt());
        json.addProperty("end", session.getEndedAt());
        json.addProperty("active", session.getActiveMillis());
        json.addProperty("afk", session.getAfkMillis());
        return json;
    }

    private static JsonObject writeAggregate(MonthlyAggregate aggregate) {
        JsonObject json = new JsonObject();
        json.addProperty("sessions", aggregate.getSessionCount());
        json.addProperty("active", aggregate.getActiveMillis());
        json.addProperty("afk", aggregate.getAfkMillis());
        json.addProperty("first", aggregate.getFirstStartedAt());
        json.addProperty("last", aggregate.getLastEndedAt());
        json.addProperty("longest", aggregate.getLongestSessionMillis());
        return json;
    }

    // --- reading ------------------------------------------------------------------

    /**
     * @throws UnsupportedSchemaException if the file was written by a newer version of the
     *                                    mod, which this build cannot safely interpret
     */
    public static PlaytimeData read(JsonObject root) {
        PlaytimeData data = new PlaytimeData();
        if (root == null) {
            return data;
        }

        int version = optInt(root, "schemaVersion", SCHEMA_VERSION);
        if (version > SCHEMA_VERSION) {
            throw new UnsupportedSchemaException(version, SCHEMA_VERSION);
        }

        data.setInProgress(readProvisional(optObject(root, "inProgress")));

        JsonObject players = optObject(root, "players");
        if (players == null) {
            return data;
        }

        for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            String playerUuid = entry.getKey();
            readPlayer(data.player(playerUuid), playerUuid, entry.getValue().getAsJsonObject());
        }
        return data;
    }

    private static ProvisionalSession readProvisional(JsonObject json) {
        if (json == null) {
            return null;
        }
        String playerUuid = optString(json, "player");
        String rawTarget = optString(json, "target");
        if (playerUuid == null || rawTarget == null) {
            return null;
        }
        TargetKey target;
        try {
            target = TargetKey.deserialize(rawTarget);
        } catch (IllegalArgumentException e) {
            return null;
        }
        long start = optLong(json, "start", -1L);
        long updated = optLong(json, "updated", -1L);
        long active = optLong(json, "active", -1L);
        long afk = optLong(json, "afk", -1L);
        if (start < 0L || updated < start || active < 0L || afk < 0L) {
            return null;
        }
        return new ProvisionalSession(
                playerUuid, target, optString(json, "displayName"), start, updated, active, afk);
    }

    private static void readPlayer(PlayerPlaytime player, String playerUuid, JsonObject json) {
        JsonObject targets = optObject(json, "targets");
        if (targets == null) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : targets.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            TargetKey key;
            try {
                key = TargetKey.deserialize(entry.getKey());
            } catch (IllegalArgumentException e) {
                // An unreadable key means an entry we can neither attribute nor display.
                continue;
            }
            readTarget(player.target(key), playerUuid, entry.getValue().getAsJsonObject());
        }
    }

    /**
     * @param playerUuid stored once as the key of the enclosing "players" entry rather than
     *                   repeated in every session, so it has to be threaded back down here
     */
    private static void readTarget(TrackedTarget target, String playerUuid, JsonObject json) {
        target.setDisplayName(optString(json, "displayName"));

        JsonArray sessions = optArray(json, "sessions");
        if (sessions != null) {
            for (JsonElement element : sessions) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject session = element.getAsJsonObject();
                long start = optLong(session, "start", -1L);
                long end = optLong(session, "end", -1L);
                long active = optLong(session, "active", -1L);
                long afk = optLong(session, "afk", -1L);
                if (start < 0L || end < start || active < 0L || afk < 0L) {
                    continue;
                }
                target.addSession(new TrackedSession(
                        playerUuid, target.getKey(), start, end, active, afk));
            }
        }

        JsonObject aggregates = optObject(json, "aggregates");
        if (aggregates != null) {
            for (Map.Entry<String, JsonElement> entry : aggregates.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                YearMonth month;
                try {
                    month = YearMonth.parse(entry.getKey(), MONTH_FORMAT);
                } catch (RuntimeException e) {
                    continue;
                }
                JsonObject aggregate = entry.getValue().getAsJsonObject();
                target.putAggregate(MonthlyAggregate.of(
                        month,
                        optInt(aggregate, "sessions", 0),
                        optLong(aggregate, "active", 0L),
                        optLong(aggregate, "afk", 0L),
                        optLong(aggregate, "first", 0L),
                        optLong(aggregate, "last", 0L),
                        optLong(aggregate, "longest", 0L)));
            }
        }
    }

    // --- lenient accessors ---------------------------------------------------------

    private static JsonObject optObject(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray optArray(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String optString(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static long optLong(JsonObject json, String name, long fallback) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int optInt(JsonObject json, String name, int fallback) {
        return (int) optLong(json, name, fallback);
    }
}

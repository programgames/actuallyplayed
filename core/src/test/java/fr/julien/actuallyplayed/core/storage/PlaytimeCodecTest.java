package fr.julien.actuallyplayed.core.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.julien.actuallyplayed.core.model.MonthlyAggregate;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.model.PlaytimeData;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.model.TrackedSession;
import fr.julien.actuallyplayed.core.model.TrackedTarget;
import org.junit.Test;

import java.time.YearMonth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlaytimeCodecTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");
    private static final TargetKey WORLD = TargetKey.singleplayer("Mon Monde");

    private static final long MINUTE = 60_000L;

    private static JsonObject parse(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    @Test
    public void roundTripsEverything() {
        PlaytimeData original = new PlaytimeData();
        original.record(new TrackedSession(PLAYER, SERVER, 1000L, 1000L + 70 * MINUTE, 60 * MINUTE, 10 * MINUTE),
                "Hypixel");
        original.record(new TrackedSession(PLAYER, WORLD, 5000L, 5000L + 30 * MINUTE, 30 * MINUTE, 0L),
                "Ma partie solo");
        original.player(PLAYER).target(SERVER).putAggregate(
                MonthlyAggregate.of(YearMonth.of(2026, 1), 4, 200 * MINUTE, 20 * MINUTE, 10L, 99L, 80 * MINUTE));

        PlaytimeData restored = PlaytimeCodec.read(PlaytimeCodec.write(original));

        PlayerPlaytime player = restored.find(PLAYER);
        assertNotNull(player);
        assertEquals(2, player.getTargets().size());

        TrackedTarget server = player.find(SERVER);
        assertEquals("Hypixel", server.getDisplayName());
        assertEquals(1, server.getSessions().size());
        assertEquals(1, server.getAggregates().size());
        assertEquals(original.player(PLAYER).getTotalActiveMillis(), restored.player(PLAYER).getTotalActiveMillis());
        assertEquals(original.player(PLAYER).getTotalAfkMillis(), restored.player(PLAYER).getTotalAfkMillis());
        assertEquals(original.player(PLAYER).getSessionCount(), restored.player(PLAYER).getSessionCount());

        TrackedSession session = server.getSessions().get(0);
        assertEquals(PLAYER, session.getPlayerUuid());
        assertEquals(SERVER, session.getTarget());
        assertEquals(60 * MINUTE, session.getActiveMillis());
    }

    @Test
    public void keepsTheHostPortColonInTargetKeys() {
        PlaytimeData original = new PlaytimeData();
        original.record(new TrackedSession(PLAYER, SERVER, 0L, MINUTE, MINUTE, 0L), "Hypixel");

        PlaytimeData restored = PlaytimeCodec.read(PlaytimeCodec.write(original));

        assertNotNull("a key like server:host:port must survive the round trip",
                restored.player(PLAYER).find(SERVER));
        assertEquals("mc.hypixel.net:25565", restored.player(PLAYER).find(SERVER).getKey().getId());
    }

    @Test
    public void readsAnEmptyDocument() {
        assertTrue(PlaytimeCodec.read(parse("{}")).isEmpty());
        assertTrue(PlaytimeCodec.read(null).isEmpty());
    }

    @Test(expected = UnsupportedSchemaException.class)
    public void refusesAFileFromANewerVersion() {
        PlaytimeCodec.read(parse("{\"schemaVersion\": 99, \"players\": {}}"));
    }

    @Test
    public void skipsDamagedSessionsButKeepsTheRest() {
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"players\": {\"" + PLAYER + "\": {\"targets\": {"
                + "  \"server:mc.hypixel.net:25565\": {"
                + "     \"displayName\": \"Hypixel\","
                + "     \"sessions\": ["
                + "        {\"start\": 1000, \"end\": 61000, \"active\": 60000, \"afk\": 0},"
                + "        {\"start\": 5000, \"end\": 1000, \"active\": 60000, \"afk\": 0},"
                + "        {\"start\": 9000, \"active\": 60000},"
                + "        \"not an object\","
                + "        {\"start\": 20000, \"end\": 80000, \"active\": 50000, \"afk\": 10000}"
                + "     ],"
                + "     \"aggregates\": {}"
                + "  }"
                + "}}}}";

        PlaytimeData data = PlaytimeCodec.read(parse(json));

        TrackedTarget target = data.player(PLAYER).find(SERVER);
        assertEquals("only the two well-formed sessions survive", 2, target.getSessions().size());
        assertEquals(110_000L, target.getTotalActiveMillis());
    }

    @Test
    public void skipsUnparseableTargetKeys() {
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"players\": {\"" + PLAYER + "\": {\"targets\": {"
                + "  \"garbage-without-separator\": {\"sessions\": [], \"aggregates\": {}},"
                + "  \"unknown_type:whatever\": {\"sessions\": [], \"aggregates\": {}},"
                + "  \"server:mc.hypixel.net:25565\": {\"displayName\": \"Hypixel\","
                + "     \"sessions\": [{\"start\": 0, \"end\": 60000, \"active\": 60000, \"afk\": 0}],"
                + "     \"aggregates\": {}}"
                + "}}}}";

        PlaytimeData data = PlaytimeCodec.read(parse(json));

        assertEquals(1, data.player(PLAYER).getTargets().size());
        assertNotNull(data.player(PLAYER).find(SERVER));
    }

    @Test
    public void skipsMalformedMonthKeys() {
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"players\": {\"" + PLAYER + "\": {\"targets\": {"
                + "  \"server:mc.hypixel.net:25565\": {\"displayName\": \"Hypixel\", \"sessions\": [],"
                + "     \"aggregates\": {"
                + "        \"pas-un-mois\": {\"sessions\": 3, \"active\": 100, \"afk\": 0},"
                + "        \"2026-01\": {\"sessions\": 2, \"active\": 500, \"afk\": 100,"
                + "                     \"first\": 10, \"last\": 99, \"longest\": 400}"
                + "     }}"
                + "}}}}";

        PlaytimeData data = PlaytimeCodec.read(parse(json));

        TrackedTarget target = data.player(PLAYER).find(SERVER);
        assertEquals(1, target.getAggregates().size());
        assertEquals(500L, target.getTotalActiveMillis());
    }

    @Test
    public void writesAReadableDocument() {
        PlaytimeData data = new PlaytimeData();
        data.record(new TrackedSession(PLAYER, SERVER, 1000L, 61000L, 60000L, 0L), "Hypixel");

        JsonObject json = PlaytimeCodec.write(data);

        assertEquals(PlaytimeCodec.SCHEMA_VERSION, json.get("schemaVersion").getAsInt());
        JsonObject target = json.getAsJsonObject("players")
                .getAsJsonObject(PLAYER)
                .getAsJsonObject("targets")
                .getAsJsonObject("server:mc.hypixel.net:25565");
        assertEquals("Hypixel", target.get("displayName").getAsString());
        assertEquals(1, target.getAsJsonArray("sessions").size());
        assertNull("the uuid is the players key, never repeated per session",
                target.getAsJsonArray("sessions").get(0).getAsJsonObject().get("playerUuid"));
    }
}

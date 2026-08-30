package fr.julien.actuallyplayed.core.model;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link TargetKey} is the map key every destination is filed under, so its equality and its
 * round trip through storage decide whether a player's history stays in one piece.
 */
public class TargetKeyTest {

    @Test
    public void equalKeysAreInterchangeableAsMapKeys() {
        Map<TargetKey, String> map = new HashMap<TargetKey, String>();
        map.put(TargetKey.server("mc.hypixel.net:25565"), "Hypixel");

        assertEquals("a key rebuilt from the same address must find the same entry",
                "Hypixel", map.get(TargetKey.server("mc.hypixel.net:25565")));
        assertEquals(TargetKey.server("mc.hypixel.net:25565").hashCode(),
                TargetKey.server("mc.hypixel.net:25565").hashCode());
    }

    @Test
    public void typeIsPartOfTheIdentity() {
        assertNotEquals("a world and a server that share a name are not the same destination",
                TargetKey.server("New World"), TargetKey.singleplayer("New World"));
    }

    @Test
    public void differentIdsAreDifferentKeys() {
        assertNotEquals(TargetKey.server("a:25565"), TargetKey.server("b:25565"));
    }

    @Test
    public void isNotEqualToOtherTypes() {
        assertFalse(TargetKey.server("a:25565").equals("server:a:25565"));
        assertFalse(TargetKey.server("a:25565").equals(null));
    }

    @Test
    public void roundTripsThroughItsStorageForm() {
        TargetKey server = TargetKey.server("mc.hypixel.net:25565");
        TargetKey world = TargetKey.singleplayer("Mon Monde");

        assertEquals(server, TargetKey.deserialize(server.serialize()));
        assertEquals(world, TargetKey.deserialize(world.serialize()));
    }

    @Test
    public void keepsTheColonsInsideAnAddress() {
        TargetKey ipv6 = TargetKey.server("[2001:db8::1]:25565");

        assertEquals("only the first separator is structural",
                ipv6, TargetKey.deserialize(ipv6.serialize()));
        assertEquals("[2001:db8::1]:25565", TargetKey.deserialize(ipv6.serialize()).getId());
    }

    @Test
    public void acceptsAKeyWrittenWithAnUnexpectedCase() {
        assertEquals(TargetKey.server("a:25565"), TargetKey.deserialize("SERVER:a:25565"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAKeyWithNoSeparator() {
        TargetKey.deserialize("garbage");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAKeyWithNoType() {
        TargetKey.deserialize(":something");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAKeyWithNoId() {
        TargetKey.deserialize("server:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnUnknownType() {
        TargetKey.deserialize("dimension:overworld");
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNull() {
        TargetKey.deserialize(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsABlankId() {
        TargetKey.server("   ");
    }

    @Test
    public void exposesItsType() {
        assertTrue(TargetKey.server("a:1").getType() == TargetType.SERVER);
        assertTrue(TargetKey.singleplayer("w").getType() == TargetType.SINGLEPLAYER);
    }
}

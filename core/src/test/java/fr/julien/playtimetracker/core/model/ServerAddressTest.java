package fr.julien.playtimetracker.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerAddressTest {

    @Test
    public void addsTheDefaultPortWhenAbsent() {
        assertEquals("mc.hypixel.net:25565", ServerAddress.normalize("mc.hypixel.net"));
    }

    @Test
    public void keepsAnExplicitPort() {
        assertEquals("play.example.com:19132", ServerAddress.normalize("play.example.com:19132"));
    }

    @Test
    public void lowerCasesTheHost() {
        assertEquals("the same server must not become two rows", "mc.hypixel.net:25565",
                ServerAddress.normalize("Mc.Hypixel.Net"));
    }

    @Test
    public void treatsAnAddressWithAndWithoutItsDefaultPortAsOne() {
        assertEquals(ServerAddress.normalize("mc.hypixel.net"),
                ServerAddress.normalize("mc.hypixel.net:25565"));
    }

    @Test
    public void trimsSurroundingWhitespace() {
        assertEquals("mc.hypixel.net:25565", ServerAddress.normalize("  mc.hypixel.net  "));
    }

    @Test
    public void handlesRawIpv4() {
        assertEquals("192.168.1.20:25565", ServerAddress.normalize("192.168.1.20"));
        assertEquals("192.168.1.20:25577", ServerAddress.normalize("192.168.1.20:25577"));
    }

    @Test
    public void doesNotMistakeIpv6ColonsForAPort() {
        assertEquals("a bare IPv6 address has no port and must still get the default",
                "2001:db8::1:25565", ServerAddress.normalize("2001:db8::1"));
    }

    @Test
    public void handlesBracketedIpv6() {
        assertEquals("[2001:db8::1]:25565", ServerAddress.normalize("[2001:db8::1]"));
        assertEquals("[2001:db8::1]:25577", ServerAddress.normalize("[2001:db8::1]:25577"));
    }

    @Test
    public void fallsBackWhenThereIsNothingToNormalise() {
        assertEquals("unknown", ServerAddress.normalize(null));
        assertEquals("unknown", ServerAddress.normalize(""));
        assertEquals("unknown", ServerAddress.normalize("   "));
    }
}

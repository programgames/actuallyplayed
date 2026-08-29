package fr.julien.playtimetracker.core.storage;

import fr.julien.playtimetracker.core.model.PlaytimeData;
import fr.julien.playtimetracker.core.model.TargetKey;
import fr.julien.playtimetracker.core.model.TrackedSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonPlaytimeStoreTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path file;
    private JsonPlaytimeStore store;

    @Before
    public void setUp() {
        file = folder.getRoot().toPath().resolve("playtime.json");
        store = new JsonPlaytimeStore(file);
    }

    private PlaytimeData oneSession() {
        PlaytimeData data = new PlaytimeData();
        data.record(new TrackedSession(PLAYER, SERVER, 1000L, 61000L, 60000L, 0L), "Hypixel");
        return data;
    }

    private List<String> filesInFolder() throws IOException {
        List<String> names = new ArrayList<String>();
        DirectoryStream<Path> stream = Files.newDirectoryStream(folder.getRoot().toPath());
        try {
            for (Path path : stream) {
                names.add(path.getFileName().toString());
            }
        } finally {
            stream.close();
        }
        return names;
    }

    @Test
    public void loadsEmptyWhenNothingWasEverSaved() throws IOException {
        assertTrue(store.load().isEmpty());
        assertFalse("loading must not create the file", Files.exists(file));
    }

    @Test
    public void savesAndReloads() throws IOException {
        store.save(oneSession());

        PlaytimeData reloaded = store.load();

        assertEquals(60000L, reloaded.player(PLAYER).getTotalActiveMillis());
        assertEquals("Hypixel", reloaded.player(PLAYER).find(SERVER).getDisplayName());
    }

    @Test
    public void createsMissingDirectories() throws IOException {
        Path nested = folder.getRoot().toPath().resolve("config/playtimetracker/playtime.json");
        JsonPlaytimeStore nestedStore = new JsonPlaytimeStore(nested);

        nestedStore.save(oneSession());

        assertTrue(Files.exists(nested));
    }

    @Test
    public void leavesNoTemporaryFileBehind() throws IOException {
        store.save(oneSession());

        for (String name : filesInFolder()) {
            assertFalse("a leftover .tmp means the write was not finished: " + name, name.endsWith(".tmp"));
        }
        assertEquals(1, filesInFolder().size());
    }

    @Test
    public void writesHumanReadableJson() throws IOException {
        store.save(oneSession());

        String content = new String(Files.readAllBytes(file), UTF_8);

        assertTrue("the file is meant to be readable and hand-editable", content.contains("\n"));
        assertTrue(content.contains("\"schemaVersion\""));
        assertTrue(content.contains("mc.hypixel.net:25565"));
    }

    @Test
    public void overwritingKeepsTheFileValid() throws IOException {
        store.save(oneSession());

        PlaytimeData bigger = oneSession();
        bigger.record(new TrackedSession(PLAYER, SERVER, 70000L, 100000L, 30000L, 0L), "Hypixel");
        store.save(bigger);

        assertEquals(90000L, store.load().player(PLAYER).getTotalActiveMillis());
        assertEquals(1, filesInFolder().size());
    }

    @Test
    public void quarantinesACorruptedFileInsteadOfDeletingIt() throws IOException {
        Files.write(file, "{ this is not json".getBytes(UTF_8));

        PlaytimeData data = store.load();

        assertTrue("the game must still start with a fresh set of data", data.isEmpty());
        Path quarantined = store.getLastQuarantinedFile();
        assertNotNull("the damaged file must be kept for recovery", quarantined);
        assertTrue(Files.exists(quarantined));
        assertTrue(quarantined.getFileName().toString().contains(".corrupt-"));
        assertFalse(Files.exists(file));
    }

    @Test
    public void quarantinesAJsonFileThatIsNotAnObject() throws IOException {
        Files.write(file, "[1, 2, 3]".getBytes(UTF_8));

        assertTrue(store.load().isEmpty());
        assertNotNull(store.getLastQuarantinedFile());
    }

    @Test
    public void refusesAFileFromANewerVersionWithoutTouchingIt() throws IOException {
        String content = "{\"schemaVersion\": 99, \"players\": {}}";
        Files.write(file, content.getBytes(UTF_8));

        try {
            store.load();
            fail("a file from a newer mod version must not be silently accepted");
        } catch (UnsupportedSchemaException expected) {
            assertEquals(99, expected.getFileVersion());
        }

        assertEquals("the file must be left exactly as it was",
                content, new String(Files.readAllBytes(file), UTF_8));
        assertNull(store.getLastQuarantinedFile());
    }

    @Test
    public void reportsNoQuarantineOnAHealthyLoad() throws IOException {
        store.save(oneSession());

        store.load();

        assertNull(store.getLastQuarantinedFile());
    }
}

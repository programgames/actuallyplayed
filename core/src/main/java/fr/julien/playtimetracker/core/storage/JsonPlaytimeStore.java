package fr.julien.playtimetracker.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import fr.julien.playtimetracker.core.model.PlaytimeData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * Stores playtime data as a single human-readable JSON file, written atomically.
 * <p>
 * The file is pretty-printed on purpose: the player can open it, read their history and
 * fix it by hand if they ever need to. That is only worth something if the mod never
 * corrupts it, hence {@link AtomicFileWriter}.
 */
public final class JsonPlaytimeStore implements PlaytimeRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private Path lastQuarantinedFile;

    public JsonPlaytimeStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public Path getFile() {
        return file;
    }

    /**
     * The file a damaged data file was moved to during the last {@link #load()}, or
     * {@code null} if nothing was quarantined. The caller is expected to log it so the
     * player knows their old data is still recoverable.
     */
    public Path getLastQuarantinedFile() {
        return lastQuarantinedFile;
    }

    @Override
    public PlaytimeData load() throws IOException {
        lastQuarantinedFile = null;

        if (!Files.exists(file)) {
            return new PlaytimeData();
        }

        String content = AtomicFileWriter.read(file);
        try {
            JsonObject root = new JsonParser().parse(content).getAsJsonObject();
            return PlaytimeCodec.read(root);
        } catch (UnsupportedSchemaException e) {
            // A file from a newer mod version: never overwrite it, let the caller decide.
            throw e;
        } catch (JsonSyntaxException e) {
            return quarantineAndStartFresh();
        } catch (IllegalStateException e) {
            // Valid JSON, but not an object at the root.
            return quarantineAndStartFresh();
        }
    }

    /**
     * Moves an unreadable file aside instead of deleting it, and starts from scratch.
     * <p>
     * Silently wiping a corrupted file would destroy months of history with no trace;
     * refusing to start would leave the mod permanently broken. Setting it aside keeps
     * both the game playable and the data recoverable.
     */
    private PlaytimeData quarantineAndStartFresh() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path quarantined = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp);
        Files.move(file, quarantined, StandardCopyOption.REPLACE_EXISTING);
        lastQuarantinedFile = quarantined;
        return new PlaytimeData();
    }

    @Override
    public void save(PlaytimeData data) throws IOException {
        Objects.requireNonNull(data, "data");
        AtomicFileWriter.write(file, GSON.toJson(PlaytimeCodec.write(data)));
    }
}

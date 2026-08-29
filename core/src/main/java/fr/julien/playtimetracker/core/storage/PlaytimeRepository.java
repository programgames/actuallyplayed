package fr.julien.playtimetracker.core.storage;

import fr.julien.playtimetracker.core.model.PlaytimeData;

import java.io.IOException;

/**
 * Where playtime data is read from and written to.
 * <p>
 * An interface so the engine and the GUI never depend on the file system — tests use an
 * in-memory implementation, and a future version could store elsewhere without touching
 * anything above.
 */
public interface PlaytimeRepository {

    /** @return the stored data, or an empty set if nothing has been recorded yet */
    PlaytimeData load() throws IOException;

    void save(PlaytimeData data) throws IOException;
}

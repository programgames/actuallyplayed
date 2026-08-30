package fr.julien.actuallyplayed.core.storage;

/**
 * Thrown when the data file was written by a newer version of the mod.
 * <p>
 * Reading it with an older schema would mean guessing at fields we do not know about, and
 * the next save would silently drop them. Refusing is the safe move: the player keeps
 * their file intact and can simply update the mod again.
 */
public class UnsupportedSchemaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int fileVersion;
    private final int supportedVersion;

    public UnsupportedSchemaException(int fileVersion, int supportedVersion) {
        super("Playtime data was written with schema version " + fileVersion
                + ", but this build only understands up to " + supportedVersion
                + ". Update the mod rather than letting it overwrite your history.");
        this.fileVersion = fileVersion;
        this.supportedVersion = supportedVersion;
    }

    public int getFileVersion() {
        return fileVersion;
    }

    public int getSupportedVersion() {
        return supportedVersion;
    }
}

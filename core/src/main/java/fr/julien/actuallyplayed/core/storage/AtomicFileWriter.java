package fr.julien.actuallyplayed.core.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Replaces a file's contents in a way that a crash cannot leave half-written.
 * <p>
 * The content goes to a temporary file next to the target, is flushed all the way to the
 * storage device, and only then replaces the target in a single move. At every instant the
 * target is either the complete old version or the complete new one — never a truncated
 * mix. This is what lets the mod autosave every minute without risking the player's whole
 * history on an unlucky crash.
 */
public final class AtomicFileWriter {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String TEMP_SUFFIX = ".tmp";

    private AtomicFileWriter() {
    }

    public static void write(Path target, String content) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        // Kept in the same directory on purpose: a move across file systems cannot be
        // atomic, and the system temp directory is often on another volume.
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);

        ByteBuffer bytes = ByteBuffer.wrap(content.getBytes(UTF_8));
        FileChannel channel = FileChannel.open(temp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            // Forces the bytes out of the OS cache and onto the device. Without it, a power
            // loss just after the move could leave an atomically-renamed but empty file.
            channel.force(true);
        } finally {
            channel.close();
        }

        try {
            Files.move(temp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some file systems (notably certain network shares) refuse atomic moves.
            // A plain replace is still far better than writing the target in place.
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String read(Path source) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        return new String(bytes, UTF_8);
    }
}

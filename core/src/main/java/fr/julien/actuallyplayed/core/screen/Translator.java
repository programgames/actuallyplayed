package fr.julien.actuallyplayed.core.screen;

/**
 * Resolves a translation key against the running game's language.
 * <p>
 * {@code core} holds the keys but cannot look them up: the language files belong to Minecraft's
 * resource system, and the class that reads them has moved package more than once across the
 * versions this mod ships for. So the lookup is injected, which is the same reason {@code Clock}
 * is injected rather than read from {@code System}.
 */
public interface Translator {

    /**
     * @param key  a key from the mod's {@code .lang} files
     * @param args arguments for the format specifiers in its value, if any
     * @return the translated text, or the key itself if the game has no entry for it
     */
    String translate(String key, String... args);
}

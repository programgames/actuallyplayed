package fr.julien.actuallyplayed.core.screen;

import java.util.Objects;

/**
 * One styled fragment of a line: either a literal already formatted by {@code core}, or a
 * translation key the platform must resolve.
 * <p>
 * The split exists because {@code core} cannot translate — the language files belong to
 * Minecraft's resource system. So the model carries the key and its arguments, and the
 * platform calls its own {@code I18n}. Durations, percentages and dates travel as literals:
 * they are produced by {@code DurationFormatter} and {@code DateFormatter}, read the same in
 * every language, and must never go through a translation table.
 */
public final class TextSpan {

    private static final String[] NO_ARGS = new String[0];

    private final String key;
    private final String text;
    private final String[] args;
    private final TextStyle style;

    private TextSpan(String key, String text, String[] args, TextStyle style) {
        this.key = key;
        this.text = text;
        this.args = args;
        this.style = Objects.requireNonNull(style, "style");
    }

    /** Text to draw as it stands. */
    public static TextSpan literal(String text, TextStyle style) {
        return new TextSpan(null, Objects.requireNonNull(text, "text"), NO_ARGS, style);
    }

    /** A key for the platform to look up, with the arguments its format string expects. */
    public static TextSpan translated(String key, TextStyle style, String... args) {
        return new TextSpan(Objects.requireNonNull(key, "key"), null,
                args == null ? NO_ARGS : args.clone(), style);
    }

    public boolean isTranslated() {
        return key != null;
    }

    /** The translation key, or {@code null} for a literal. */
    public String getKey() {
        return key;
    }

    /** The literal text, or {@code null} for a translated span. */
    public String getText() {
        return text;
    }

    /** Arguments for the translation, never {@code null}. */
    public String[] getArgs() {
        return args.clone();
    }

    public TextStyle getStyle() {
        return style;
    }
}

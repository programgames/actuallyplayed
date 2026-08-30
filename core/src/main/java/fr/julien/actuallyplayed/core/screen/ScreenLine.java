package fr.julien.actuallyplayed.core.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One row of the statistics screen: what it says, where it sits, and how it is anchored.
 * <p>
 * Coordinates are offsets, never absolute pixels — {@link #getY()} from the top of the
 * content block, {@link #getX()} from the horizontal centre of the screen. The platform knows
 * the screen size and adds it in. That keeps the whole layout in {@code core}, where it can
 * be tested, while leaving the platform nothing to do but draw.
 */
public final class ScreenLine {

    /** How the platform should treat the row. */
    public enum Kind {
        /** The screen's heading. */
        TITLE,
        /** A section heading, drawn with a thin rule reaching out either side. */
        SECTION_HEADING,
        /** An ordinary row. */
        BODY
    }

    public enum Align {
        CENTER,
        /** {@link #getX()} is the left edge of the text. */
        LEFT,
        /** {@link #getX()} is the right edge of the text. */
        RIGHT
    }

    private final int x;
    private final int y;
    private final Kind kind;
    private final Align align;
    private final boolean truncated;
    private final List<TextSpan> spans;

    private ScreenLine(int x, int y, Kind kind, Align align, boolean truncated, List<TextSpan> spans) {
        this.x = x;
        this.y = y;
        this.kind = kind;
        this.align = align;
        this.truncated = truncated;
        this.spans = Collections.unmodifiableList(new ArrayList<TextSpan>(spans));
    }

    static ScreenLine centered(int y, Kind kind, TextSpan... spans) {
        return new ScreenLine(0, y, kind, Align.CENTER, false, Arrays.asList(spans));
    }

    /**
     * A centred row whose text has no bound on its length — a server label is whatever the
     * player typed into their server list. The platform trims it to the content width, which
     * is the one layout decision {@code core} cannot make: it depends on the font.
     */
    static ScreenLine centeredTruncated(int y, TextSpan... spans) {
        return new ScreenLine(0, y, Kind.BODY, Align.CENTER, true, Arrays.asList(spans));
    }

    static ScreenLine left(int x, int y, TextSpan... spans) {
        return new ScreenLine(x, y, Kind.BODY, Align.LEFT, false, Arrays.asList(spans));
    }

    static ScreenLine right(int x, int y, TextSpan... spans) {
        return new ScreenLine(x, y, Kind.BODY, Align.RIGHT, false, Arrays.asList(spans));
    }

    /** Offset from the horizontal centre of the screen. */
    public int getX() {
        return x;
    }

    /** Offset from the top of the content block. */
    public int getY() {
        return y;
    }

    public Kind getKind() {
        return kind;
    }

    public Align getAlign() {
        return align;
    }

    /** Whether the platform should trim this row to the content width before drawing it. */
    public boolean isTruncated() {
        return truncated;
    }

    public List<TextSpan> getSpans() {
        return spans;
    }
}

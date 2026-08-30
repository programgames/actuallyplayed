package fr.julien.actuallyplayed.common.client;

/**
 * The handful of drawing primitives the statistics screen needs, named once so the layout can
 * be written once.
 *
 * <h3>Why this exists</h3>
 * Minecraft's drawing API has been rewritten twice across the versions this mod targets: 1.16
 * passes a {@code PoseStack} to static helpers on {@code GuiComponent}, 1.20 replaced both with
 * a {@code GuiGraphics} object, and 1.21 changed what {@code Screen.render} does around it. The
 * signature of {@code render} itself differs, so the {@code Screen} subclass genuinely has to
 * be written per version.
 * <p>
 * What does <em>not</em> have to differ is the loop over the rows: reading a
 * {@link fr.julien.actuallyplayed.core.screen.StatsScreenModel}, trimming an over-long label,
 * anchoring left or right, drawing a section rule. That is
 * {@link StatsScreenRenderer}, written once against this interface, and it keeps each version's
 * screen down to declaring the primitives rather than repeating the layout.
 * <p>
 * Coordinates are absolute screen pixels; the renderer has already resolved the model's offsets.
 */
public interface ScreenPainter {

    /** Draws text with its left edge at {@code x}. */
    void drawLeft(String text, int x, int y);

    /** Draws text with its right edge at {@code x}. */
    void drawRight(String text, int x, int y);

    /** Draws text centred on {@code x}. */
    void drawCentered(String text, int x, int y);

    /** A one-pixel horizontal rule from {@code fromX} to {@code toX}. */
    void horizontalLine(int fromX, int toX, int y, int colour);

    /** Width of {@code text} in pixels, in the font this screen draws with. */
    int width(String text);

    /**
     * Trims {@code text} to at most {@code maxWidth} pixels.
     * <p>
     * The one layout decision {@code core} cannot make, because it depends on the font: a
     * server label is whatever the player typed into their server list, so its length is
     * unbounded.
     */
    String trim(String text, int maxWidth);
}

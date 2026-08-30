package fr.julien.actuallyplayed.common.client;

import fr.julien.actuallyplayed.core.screen.ScreenLine;
import fr.julien.actuallyplayed.core.screen.StatsScreenModel;
import fr.julien.actuallyplayed.core.screen.TextSpan;
import fr.julien.actuallyplayed.core.screen.TextStyle;
import net.minecraft.client.resources.language.I18n;

/**
 * Draws a {@link StatsScreenModel} through a {@link ScreenPainter}, once for every Minecraft
 * version.
 * <p>
 * This is the last piece of the screen that is neither pure data nor pure drawing: it resolves
 * translation keys, maps {@code core}'s palette onto the colour codes, and turns the model's
 * offsets into screen coordinates. It sits here rather than in {@code core} because it needs
 * {@code I18n} (core cannot translate) and and rather than in each version's screen
 * because none of it changes between versions.
 * <p>
 * What is left to each version is declaring the primitives of {@link ScreenPainter}, which is
 * where Minecraft actually differs.
 */
public final class StatsScreenRenderer {

    /** Section rules, and the dim layer's colour, kept with the layout they belong to. */
    private static final int RULE_COLOUR = 0xFF555555;

    /** Gap between a section heading and the rule beside it. */
    private static final int RULE_GAP = 8;

    private StatsScreenRenderer() {
    }

    /**
     * @param model    what to draw
     * @param painter  how to draw it
     * @param centreX  horizontal centre of the screen
     * @param top      vertical offset of the content block
     * @param maxWidth widest a truncated row may be
     */
    public static void render(StatsScreenModel model, ScreenPainter painter,
                              int centreX, int top, int maxWidth) {
        for (ScreenLine line : model.getLines()) {
            String text = resolve(line);
            int x = centreX + line.getX();
            int y = top + line.getY();

            if (line.isTruncated()) {
                text = painter.trim(text, maxWidth);
            }

            switch (line.getAlign()) {
                case LEFT:
                    painter.drawLeft(text, x, y);
                    break;
                case RIGHT:
                    painter.drawRight(text, x, y);
                    break;
                case CENTER:
                default:
                    painter.drawCentered(text, x, y);
                    if (line.getKind() == ScreenLine.Kind.SECTION_HEADING) {
                        drawRules(painter, text, centreX, y);
                    }
                    break;
            }
        }
    }

    /** A thin rule either side of a section heading, to separate blocks without boxing them. */
    private static void drawRules(ScreenPainter painter, String heading, int centreX, int y) {
        int half = StatsScreenModel.RULE_HALF_WIDTH;
        int gap = painter.width(heading) / 2 + RULE_GAP;
        painter.horizontalLine(centreX - half, centreX - gap, y + 3, RULE_COLOUR);
        painter.horizontalLine(centreX + gap, centreX + half, y + 3, RULE_COLOUR);
    }

    private static String resolve(ScreenLine line) {
        StringBuilder text = new StringBuilder();
        for (TextSpan span : line.getSpans()) {
            text.append(code(span.getStyle()));
            text.append(span.isTranslated()
                    ? I18n.get(span.getKey(), (Object[]) span.getArgs())
                    : span.getText());
        }
        return text.toString();
    }

    /**
     * {@code core}'s palette as Minecraft formatting codes.
     * <p>
     * Written as the raw codes rather than through {@code ChatFormatting}, whose package moved
     * between the versions this mod targets. The codes themselves have not changed since
     * Minecraft had colours at all.
     * <p>
     * Escaped rather than written literally: a non-ASCII character in a Java source file is the
     * mojibake trap of {@code CLAUDE.md} section 7, and this file has no reason to risk it.
     */
    private static String code(TextStyle style) {
        switch (style) {
            case GRAY:
                return "\u00a77";
            case DARK_GRAY:
                return "\u00a78";
            case YELLOW:
                return "\u00a7e";
            case GREEN:
                return "\u00a7a";
            case RED:
                return "\u00a7c";
            case WHITE:
            default:
                return "\u00a7f";
        }
    }
}

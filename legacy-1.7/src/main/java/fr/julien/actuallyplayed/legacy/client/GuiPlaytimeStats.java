package fr.julien.actuallyplayed.legacy.client;

import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.screen.RecordedTotals;
import fr.julien.actuallyplayed.core.screen.ScreenLine;
import fr.julien.actuallyplayed.core.screen.StatsScreenModel;
import fr.julien.actuallyplayed.core.screen.TextSpan;
import fr.julien.actuallyplayed.core.screen.TextStyle;
import fr.julien.actuallyplayed.legacy.bridge.TargetResolver;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Draws {@link StatsScreenModel} — playtime for the place the player is right now.
 *
 * <h3>What is left in this class</h3>
 * Nothing but painting. Which rows exist, what they say, where they sit and what colour they
 * take is all decided in {@code core}, where it is tested once and shared by every port. This
 * class resolves translation keys, turns a {@link TextStyle} into a {@code EnumChatFormatting},
 * and puts glyphs on screen — the three things that genuinely differ between Minecraft
 * versions. See {@code PORTING.md} §4.2.
 *
 * <h3>Why only the current destination</h3>
 * An earlier version listed every tracked destination and let the player drill into one. That
 * was a catalogue, and a catalogue answers a question nobody asks mid-game. What a player
 * wants while playing is "how long have I actually been on <em>this</em> server", so the
 * screen answers exactly that, with no list and no navigation.
 * <p>
 * Storage is unchanged: every destination is still recorded separately, and its history is
 * waiting whenever the player comes back to it.
 */
public class GuiPlaytimeStats extends GuiScreen {

    private static final Logger LOGGER = LogManager.getLogger("actuallyplayed");

    private static final int BUTTON_DONE = 200;

    private final GuiScreen parent;
    private final PlaytimeTracker tracker;
    private final ZoneId zone = ZoneId.systemDefault();

    /** Vertical offset of the content block, computed once per layout. */
    private int top;

    /**
     * The stored totals, read once when the screen opens.
     * <p>
     * They are derived by walking every session and aggregate, and nothing can close a
     * session while this screen is up — so recomputing them sixty times a second would
     * traverse the whole history for an answer that cannot have changed.
     */
    private RecordedTotals recorded = RecordedTotals.empty();

    public GuiPlaytimeStats(GuiScreen parent, PlaytimeTracker tracker) {
        this.parent = parent;
        this.tracker = tracker;
    }

    @Override
    public void initGui() {
        try {
            build();
        } catch (Throwable t) {
            LOGGER.error("Actually Played could not open its statistics screen.", t);
            mc.displayGuiScreen(parent);
        }
    }

    private void build() {
        buttonList.clear();

        // Centred in the space above the Done button, but never crowding the top edge on a
        // short screen.
        top = Math.max(6, (height - 36 - StatsScreenModel.CONTENT_HEIGHT) / 2);
        buttonList.add(new GuiButton(BUTTON_DONE, width / 2 - 100, height - 28, I18n.format("gui.done")));

        PlayerPlaytime player = tracker.getData().find(new TargetResolver(mc).resolvePlayerId());
        Optional<SessionSnapshot> snapshot = tracker.snapshot();
        recorded = player == null || !snapshot.isPresent()
                ? RecordedTotals.empty()
                : RecordedTotals.of(player.find(snapshot.get().getTarget()));
    }

    /**
     * Sends ESC back to the Statistics screen rather than straight into the game.
     * <p>
     * {@code GuiScreen} closes to the world by default, which would make ESC and the Done
     * button disagree. Vanilla sub-screens return to their parent.
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_DONE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            render(mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            // Rendering runs every frame, so a failure here would repeat sixty times a
            // second and end as a crash report naming this mod. Close the screen instead:
            // the player loses a statistics panel, not their session.
            LOGGER.error("Actually Played could not draw its statistics screen.", t);
            mc.displayGuiScreen(parent);
        }
    }

    private void render(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        // The screen deliberately does not pause the game, so the HUD keeps rendering
        // behind it. The crosshair sits at the exact centre of the screen — precisely where
        // the middle of a centred line of text lands — and showed through as a stray "+".
        // An extra dim layer hides it and the hotbar, and makes the numbers easier to read.
        drawRect(0, 0, width, height, 0xC0101010);

        Optional<SessionSnapshot> snapshot = tracker.snapshot();
        StatsScreenModel model = snapshot.isPresent()
                ? StatsScreenModel.of(snapshot.get(), recorded, zone)
                : StatsScreenModel.withoutSession();

        for (ScreenLine line : model.getLines()) {
            draw(line);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // --- drawing ----------------------------------------------------------------------

    private void draw(ScreenLine line) {
        String text = resolve(line);
        int y = top + line.getY();

        if (line.isTruncated()) {
            // A server label is whatever the player typed into their server list, so its
            // length is unbounded and a long one would run off both edges. Trimming needs the
            // font, which is why core hands this decision over rather than making it.
            text = fontRendererObj.trimStringToWidth(text,
                    Math.min(width - 20, StatsScreenModel.RULE_HALF_WIDTH * 2));
        }

        switch (line.getAlign()) {
            case LEFT:
                drawString(fontRendererObj, text, width / 2 + line.getX(), y, 0xFFFFFF);
                break;
            case RIGHT:
                drawString(fontRendererObj, text,
                        width / 2 + line.getX() - fontRendererObj.getStringWidth(text), y, 0xFFFFFF);
                break;
            case CENTER:
            default:
                drawCenteredString(fontRendererObj, text, width / 2 + line.getX(), y, 0xFFFFFF);
                if (line.getKind() == ScreenLine.Kind.SECTION_HEADING) {
                    drawRules(text, y);
                }
                break;
        }
    }

    /** A thin rule either side of a section heading, to separate blocks without boxing them. */
    private void drawRules(String heading, int y) {
        int half = StatsScreenModel.RULE_HALF_WIDTH;
        int gap = fontRendererObj.getStringWidth(heading) / 2 + 8;
        drawHorizontalLine(width / 2 - half, width / 2 - gap, y + 3, 0xFF555555);
        drawHorizontalLine(width / 2 + gap, width / 2 + half, y + 3, 0xFF555555);
    }

    private String resolve(ScreenLine line) {
        List<TextSpan> spans = line.getSpans();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < spans.size(); i++) {
            TextSpan span = spans.get(i);
            text.append(format(span.getStyle()));
            text.append(span.isTranslated()
                    ? I18n.format(span.getKey(), (Object[]) span.getArgs())
                    : span.getText());
        }
        return text.toString();
    }

    /** The one mapping a port has to rewrite: core's palette onto this version's codes. */
    private static EnumChatFormatting format(TextStyle style) {
        switch (style) {
            case GRAY:
                return EnumChatFormatting.GRAY;
            case DARK_GRAY:
                return EnumChatFormatting.DARK_GRAY;
            case YELLOW:
                return EnumChatFormatting.YELLOW;
            case GREEN:
                return EnumChatFormatting.GREEN;
            case RED:
                return EnumChatFormatting.RED;
            case WHITE:
            default:
                return EnumChatFormatting.WHITE;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        // Keeps the world running behind the screen, so opening the stats does not itself
        // change what is being measured.
        return false;
    }
}

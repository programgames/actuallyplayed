package fr.julien.actuallyplayed.legacy.client;

import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.ProjectLinks;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.screen.RecordedTotals;
import fr.julien.actuallyplayed.core.screen.ScreenPainter;
import fr.julien.actuallyplayed.core.screen.StatsScreenModel;
import fr.julien.actuallyplayed.core.screen.StatsScreenRenderer;
import fr.julien.actuallyplayed.core.screen.Translator;
import fr.julien.actuallyplayed.core.util.BrowserLauncher;
import fr.julien.actuallyplayed.legacy.bridge.TargetResolver;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Playtime for the place the player is right now, on Minecraft 1.7.10.
 *
 * <h3>What is left in this class</h3>
 * The drawing primitives and the translation lookup, and nothing else. Which rows exist and
 * what they say is decided in {@code core}; laying them out is {@link StatsScreenRenderer},
 * shared by all five Minecraft versions this mod ships for.
 * <p>
 * Its twins on the other versions declare the same six primitives against their own API and are
 * the same length. Before the split each carried its own copy of the layout: the same three
 * hundred lines of decisions, in five places, free to drift apart.
 *
 * <h3>Why only the current destination</h3>
 * An earlier version listed every tracked destination and let the player drill into one. That
 * was a catalogue, and a catalogue answers a question nobody asks mid-game. What a player wants
 * while playing is "how long have I actually been on <em>this</em> server".
 */
public class GuiPlaytimeStats extends GuiScreen
        implements ScreenPainter, Translator, GuiYesNoCallback {

    private static final Logger LOGGER = LogManager.getLogger("actuallyplayed");

    private static final int BUTTON_DONE = 200;
    private static final int BUTTON_REPORT = 201;

    /**
     * The report button is twice the width of Done, and the pair together is as wide as Done used
     * to be on its own.
     * <p>
     * Not two equal halves: "Report a bug or an idea" is twenty-three characters in English and
     * grows by a third in German, Russian and Greek, while "Done" is short in every language the
     * mod ships. Splitting the row evenly would fit the word that does not need the room and clip
     * the one that does.
     */
    private static final int REPORT_WIDTH = 200;
    private static final int DONE_WIDTH = 100;

    private final GuiScreen parent;
    private final PlaytimeTracker tracker;
    private final ZoneId zone = ZoneId.systemDefault();

    /** Vertical offset of the content block, computed once per layout. */
    private int top;

    /**
     * The stored totals, read once when the screen opens.
     * <p>
     * They are derived by walking every session and aggregate, and nothing can close a session
     * while this screen is up, so recomputing them every frame would traverse the whole history
     * for an answer that cannot have changed.
     */
    private RecordedTotals recorded = RecordedTotals.empty();

    public GuiPlaytimeStats(GuiScreen parent, PlaytimeTracker tracker) {
        this.parent = parent;
        this.tracker = tracker;
    }

    @Override
    public void initGui() {
        try {
            buttonList.clear();

            // Centred in the space above the Done button, but never crowding the top edge on a
            // short screen.
            top = Math.max(6, (height - 36 - StatsScreenModel.CONTENT_HEIGHT) / 2);
            int rowLeft = width / 2 - (REPORT_WIDTH + 4 + DONE_WIDTH) / 2;
            buttonList.add(new GuiButton(BUTTON_REPORT, rowLeft, height - 28, REPORT_WIDTH, 20,
                    I18n.format("actuallyplayed.gui.button.report")));
            buttonList.add(new GuiButton(BUTTON_DONE, rowLeft + REPORT_WIDTH + 4, height - 28,
                    DONE_WIDTH, 20, I18n.format("gui.done")));

            PlayerPlaytime player =
                    tracker.getData().find(new TargetResolver(mc).resolvePlayerId());
            Optional<SessionSnapshot> snapshot = tracker.snapshot();
            recorded = player == null || !snapshot.isPresent()
                    ? RecordedTotals.empty()
                    : RecordedTotals.of(player.find(snapshot.get().getTarget()));
        } catch (Throwable t) {
            LOGGER.error("Actually Played could not open its statistics screen.", t);
            mc.displayGuiScreen(parent);
        }
    }

    /**
     * Sends ESC back to the Statistics screen rather than straight into the game.
     * <p>
     * {@code GuiScreen} closes to the world by default, which would make ESC and the Done button
     * disagree. Vanilla sub-screens return to their parent.
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
        } else if (button.id == BUTTON_REPORT) {
            // Never straight to the browser. This is the screen vanilla shows for a link in chat,
            // so the player recognises it, it prints the address before anything opens, and it
            // offers "Copy to clipboard" -- which is the only way out when no browser can be
            // reached, and there are machines where none can.
            mc.displayGuiScreen(
                    new GuiConfirmOpenLink(this, ProjectLinks.ISSUES, BUTTON_REPORT, false));
        }
    }

    /**
     * Answers the confirmation screen above.
     * <p>
     * This class declares {@code GuiYesNoCallback} itself, which its 1.12.2 twin does not have to:
     * on 1.7.10 {@code GuiScreen} carries an empty {@code confirmClicked} without implementing the
     * interface, so passing {@code this} to {@code GuiConfirmOpenLink} would not compile.
     * <p>
     * Nothing is delegated to {@code super} for this id, since 1.7.10 has no link handling of its
     * own to delegate to.
     */
    @Override
    public void confirmClicked(boolean result, int id) {
        if (id != BUTTON_REPORT) {
            super.confirmClicked(result, id);
            return;
        }
        if (result && !BrowserLauncher.open(ProjectLinks.ISSUES)) {
            // The player asked for the browser and did not get it. Say so where a bug report can
            // find it, and leave them on the confirmation screen's copy button.
            LOGGER.warn("Actually Played could not open {} in a browser.", ProjectLinks.ISSUES);
        }
        mc.displayGuiScreen(this);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            drawDefaultBackground();
            // The screen deliberately does not pause the game, so the HUD keeps rendering behind
            // it. The crosshair sits at the exact centre - precisely where the middle of a
            // centred line of text lands - and showed through as a stray "+". An extra dim layer
            // hides it and the hotbar, and makes the numbers easier to read.
            drawRect(0, 0, width, height, 0xC0101010);

            Optional<SessionSnapshot> snapshot = tracker.snapshot();
            StatsScreenModel model = snapshot.isPresent()
                    ? StatsScreenModel.of(snapshot.get(), recorded, zone)
                    : StatsScreenModel.withoutSession();

            StatsScreenRenderer.render(model, this, this, width / 2, top,
                    Math.min(width - 20, StatsScreenModel.RULE_HALF_WIDTH * 2));

            super.drawScreen(mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            // Rendering runs every frame, so a failure here would repeat sixty times a second
            // and end as a crash report naming this mod. Close the screen instead: the player
            // loses a statistics panel, not their session.
            LOGGER.error("Actually Played could not draw its statistics screen.", t);
            mc.displayGuiScreen(parent);
        }
    }

    // --- Translator ------------------------------------------------------------------------

    @Override
    public String translate(String key, String... args) {
        return I18n.format(key, (Object[]) args);
    }

    // --- ScreenPainter ---------------------------------------------------------------------

    @Override
    public void drawLeft(String text, int x, int y) {
        drawString(fontRendererObj, text, x, y, 0xFFFFFF);
    }

    @Override
    public void drawRight(String text, int x, int y) {
        drawString(fontRendererObj, text, x - fontRendererObj.getStringWidth(text), y, 0xFFFFFF);
    }

    @Override
    public void drawCentered(String text, int x, int y) {
        drawCenteredString(fontRendererObj, text, x, y, 0xFFFFFF);
    }

    @Override
    public void horizontalLine(int fromX, int toX, int y, int colour) {
        drawHorizontalLine(fromX, toX, y, colour);
    }

    @Override
    public int width(String text) {
        return fontRendererObj.getStringWidth(text);
    }

    @Override
    public String trim(String text, int maxWidth) {
        return fontRendererObj.trimStringToWidth(text, maxWidth);
    }

    @Override
    public boolean doesGuiPauseGame() {
        // Keeps the world running behind the screen, so opening the statistics does not itself
        // change what is being measured.
        return false;
    }
}

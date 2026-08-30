package fr.julien.actuallyplayed.forge.client.gui;

import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.screen.RecordedTotals;
import fr.julien.actuallyplayed.core.screen.ScreenPainter;
import fr.julien.actuallyplayed.core.screen.StatsScreenModel;
import fr.julien.actuallyplayed.core.screen.StatsScreenRenderer;
import fr.julien.actuallyplayed.core.screen.Translator;
import fr.julien.actuallyplayed.forge.bridge.TargetResolver;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.time.ZoneId;
import java.util.Optional;
import java.io.IOException;

/**
 * Playtime for the place the player is right now, on Minecraft 1.12.2.
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
public class GuiPlaytimeStats extends GuiScreen implements ScreenPainter, Translator {

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
            buttonList.add(new GuiButton(BUTTON_DONE, width / 2 - 100, height - 28,
                    I18n.format("gui.done")));

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
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_DONE) {
            mc.displayGuiScreen(parent);
        }
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
        drawString(fontRenderer, text, x, y, 0xFFFFFF);
    }

    @Override
    public void drawRight(String text, int x, int y) {
        drawString(fontRenderer, text, x - fontRenderer.getStringWidth(text), y, 0xFFFFFF);
    }

    @Override
    public void drawCentered(String text, int x, int y) {
        drawCenteredString(fontRenderer, text, x, y, 0xFFFFFF);
    }

    @Override
    public void horizontalLine(int fromX, int toX, int y, int colour) {
        drawHorizontalLine(fromX, toX, y, colour);
    }

    @Override
    public int width(String text) {
        return fontRenderer.getStringWidth(text);
    }

    @Override
    public String trim(String text, int maxWidth) {
        return fontRenderer.trimStringToWidth(text, maxWidth);
    }

    @Override
    public boolean doesGuiPauseGame() {
        // Keeps the world running behind the screen, so opening the statistics does not itself
        // change what is being measured.
        return false;
    }
}

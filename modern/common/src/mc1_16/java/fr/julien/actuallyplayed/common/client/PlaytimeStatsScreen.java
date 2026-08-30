package fr.julien.actuallyplayed.common.client;

import fr.julien.actuallyplayed.common.bridge.TargetResolver;
import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.screen.RecordedTotals;
import fr.julien.actuallyplayed.core.screen.ScreenPainter;
import fr.julien.actuallyplayed.core.screen.StatsScreenRenderer;
import fr.julien.actuallyplayed.core.screen.Translator;
import fr.julien.actuallyplayed.core.screen.StatsScreenModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Playtime for the place the player is right now, on Minecraft 1.16.
 *
 * <h3>What is left in this class</h3>
 * The drawing primitives, and nothing else. Which rows exist and what they say is decided in
 * {@code core}; resolving their translation keys and laying them out is
 * {@link StatsScreenRenderer}, shared by every version. What is left here is what genuinely
 * differs on this version: a {@code PoseStack} threaded through static helpers on
 * {@code GuiComponent}, a {@code Button} with no builder, and {@code addButton} rather than
 * {@code addRenderableWidget}.
 * <p>
 * Its 1.20 twin declares the same six primitives against the newer API and is the same length.
 * Before the split, the whole screen would have been duplicated per version.
 */
public final class PlaytimeStatsScreen extends Screen implements ScreenPainter, Translator {

    private static final Logger LOGGER = LogManager.getLogger("actuallyplayed");

    private final Screen parent;
    private final PlaytimeTracker tracker;
    private final ZoneId zone = ZoneId.systemDefault();

    /** Vertical offset of the content block, computed once per layout. */
    private int top;

    /**
     * Kept so {@link #render} can draw it without {@code super.render()}, which on 1.21 would
     * redraw the background over everything else. Still registered through
     * {@code addRenderableWidget} so that clicks reach it.
     */
    private Button done;

    /** Set for the duration of one render pass, so the painter methods can reach it. */
    private PoseStack poses;

    /**
     * The stored totals, read once when the screen opens.
     * <p>
     * They are derived by walking every session and aggregate, and nothing can close a session
     * while this screen is up, so recomputing them every frame would traverse the whole history
     * for an answer that cannot have changed.
     */
    private RecordedTotals recorded = RecordedTotals.empty();

    public PlaytimeStatsScreen(Screen parent, PlaytimeTracker tracker) {
        super(new TranslatableComponent("actuallyplayed.gui.title"));
        this.parent = parent;
        this.tracker = tracker;
    }

    @Override
    protected void init() {
        // Centred in the space above the Done button, but never crowding the top edge on a
        // short screen.
        top = Math.max(6, (height - 36 - StatsScreenModel.CONTENT_HEIGHT) / 2);

        done = addButton(new Button(width / 2 - 100, height - 28, 200, 20,
                new TranslatableComponent("gui.done"), button -> onClose()));

        PlayerPlaytime player = tracker.getData().find(new TargetResolver(minecraft).resolvePlayerId());
        Optional<SessionSnapshot> snapshot = tracker.snapshot();
        recorded = player == null || !snapshot.isPresent()
                ? RecordedTotals.empty()
                : RecordedTotals.of(player.find(snapshot.get().getTarget()));
    }

    @Override
    public void onClose() {
        // Back to the Statistics screen rather than straight into the game, so Escape and the
        // Done button agree. Vanilla sub-screens behave the same way.
        minecraft.setScreen(parent);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.poses = poseStack;
        try {
            ScreenBackground.render(this, poseStack, mouseX, mouseY, partialTick);
            // The screen deliberately does not pause the game, so the HUD keeps rendering behind
            // it. The crosshair sits at the exact centre - precisely where the middle of a
            // centred line of text lands - and showed through as a stray "+". An extra dim layer
            // hides it and the hotbar, and makes the numbers easier to read.
            GuiComponent.fill(poseStack, 0, 0, width, height, 0xC0101010);

            Optional<SessionSnapshot> snapshot = tracker.snapshot();
            StatsScreenModel model = snapshot.isPresent()
                    ? StatsScreenModel.of(snapshot.get(), recorded, zone)
                    : StatsScreenModel.withoutSession();

            StatsScreenRenderer.render(model, this, this, width / 2, top,
                    Math.min(width - 20, StatsScreenModel.RULE_HALF_WIDTH * 2));

            // Drawn by hand rather than through super.render(), to keep this file the same
            // shape as its 1.20 twin, where calling it would redraw the background on 1.21.
            if (done != null) {
                done.render(poseStack, mouseX, mouseY, partialTick);
            }
        } catch (Throwable t) {
            // Rendering runs every frame, so a failure here would repeat sixty times a second
            // and end as a crash report naming this mod. Close the screen instead: the player
            // loses a statistics panel, not their session.
            LOGGER.error("Actually Played could not draw its statistics screen.", t);
            minecraft.setScreen(parent);
        } finally {
            this.poses = null;
        }
    }

    // --- Translator ------------------------------------------------------------------------

    @Override
    public String translate(String key, String... args) {
        return I18n.get(key, (Object[]) args);
    }

    // --- ScreenPainter ---------------------------------------------------------------------

    @Override
    public void drawLeft(String text, int x, int y) {
        GuiComponent.drawString(poses, font, text, x, y, 0xFFFFFF);
    }

    @Override
    public void drawRight(String text, int x, int y) {
        GuiComponent.drawString(poses, font, text, x - font.width(text), y, 0xFFFFFF);
    }

    @Override
    public void drawCentered(String text, int x, int y) {
        GuiComponent.drawCenteredString(poses, font, text, x, y, 0xFFFFFF);
    }

    @Override
    public void horizontalLine(int fromX, int toX, int y, int colour) {
        hLine(poses, fromX, toX, y, colour);
    }

    @Override
    public int width(String text) {
        return font.width(text);
    }

    @Override
    public String trim(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, maxWidth);
    }

    @Override
    public boolean isPauseScreen() {
        // Keeps the world running behind the screen, so opening the statistics does not itself
        // change what is being measured.
        return false;
    }
}

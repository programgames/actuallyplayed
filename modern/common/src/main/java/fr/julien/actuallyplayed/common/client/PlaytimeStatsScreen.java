package fr.julien.actuallyplayed.common.client;

import fr.julien.actuallyplayed.common.bridge.TargetResolver;
import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.screen.RecordedTotals;
import fr.julien.actuallyplayed.core.screen.ScreenLine;
import fr.julien.actuallyplayed.core.screen.StatsScreenModel;
import fr.julien.actuallyplayed.core.screen.TextSpan;
import fr.julien.actuallyplayed.core.screen.TextStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Draws {@link StatsScreenModel} — playtime for the place the player is right now.
 * <p>
 * Nothing here decides what the screen says. Which rows exist, what they contain, where they
 * sit and what colour they take is settled in {@code core}, tested once and shared by every
 * port. This class resolves translation keys, maps a {@link TextStyle} onto this version's
 * {@link ChatFormatting}, and puts glyphs on screen — the three things that actually differ
 * between Minecraft versions.
 * <p>
 * Its 1.12 counterpart is the same shape against {@code GuiScreen} and {@code FontRenderer}.
 * That the two are a page each, rather than three hundred lines each, is the whole return on
 * moving the layout into {@code core}.
 */
public final class PlaytimeStatsScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("actuallyplayed");

    private final Screen parent;
    private final PlaytimeTracker tracker;
    private final ZoneId zone = ZoneId.systemDefault();

    /** Vertical offset of the content block, computed once per layout. */
    private int top;

    /**
     * Kept so {@link #render} can draw it without going through {@code super.render()}, which
     * on 1.21 would redraw the background over everything else. Still registered through
     * {@code addRenderableWidget} so that clicks reach it.
     */
    private Button done;

    /**
     * The stored totals, read once when the screen opens.
     * <p>
     * They are derived by walking every session and aggregate, and nothing can close a session
     * while this screen is up — so recomputing them every frame would traverse the whole
     * history for an answer that cannot have changed.
     */
    private RecordedTotals recorded = RecordedTotals.empty();

    public PlaytimeStatsScreen(Screen parent, PlaytimeTracker tracker) {
        super(Component.translatable("actuallyplayed.gui.title"));
        this.parent = parent;
        this.tracker = tracker;
    }

    @Override
    protected void init() {
        // Centred in the space above the Done button, but never crowding the top edge on a
        // short screen.
        top = Math.max(6, (height - 36 - StatsScreenModel.CONTENT_HEIGHT) / 2);

        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20)
                .build());

        PlayerPlaytime player = tracker.getData().find(new TargetResolver(minecraft).resolvePlayerId());
        Optional<SessionSnapshot> snapshot = tracker.snapshot();
        recorded = player == null || snapshot.isEmpty()
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            // Through a per-version class: the signature gained three parameters in 1.21.
            // It is the only call in the whole adapter that differs between the versions this
            // mod targets. See PORTING.md section 3.7.
            ScreenBackground.render(this, graphics, mouseX, mouseY, partialTick);
            // The screen deliberately does not pause the game, so the HUD keeps rendering
            // behind it. The crosshair sits at the exact centre of the screen - precisely where
            // the middle of a centred line of text lands - and showed through as a stray "+".
            // An extra dim layer hides it and the hotbar, and makes the numbers easier to read.
            graphics.fill(0, 0, width, height, 0xC0101010);

            Optional<SessionSnapshot> snapshot = tracker.snapshot();
            StatsScreenModel model = snapshot.isPresent()
                    ? StatsScreenModel.of(snapshot.get(), recorded, zone)
                    : StatsScreenModel.withoutSession();

            for (ScreenLine line : model.getLines()) {
                draw(graphics, line);
            }

            // The widgets, drawn by hand rather than through super.render().
            //
            // On 1.21 Screen.render() begins by calling renderBackground() itself, which 1.20.1
            // did not. Calling it here therefore redrew the background OVER everything above -
            // and 1.21's background is blurred, so the whole screen came out blurred with only
            // the Done button, rendered afterwards, sharp. Seen in game on NeoForge 1.21.1.
            //
            // Drawing the one widget this screen owns is exactly what Screen.render() does
            // once its own background call is set aside, and it behaves the same on both
            // versions - so this removes a version difference rather than adding one.
            if (done != null) {
                done.render(graphics, mouseX, mouseY, partialTick);
            }
        } catch (Throwable t) {
            // Rendering runs every frame, so a failure here would repeat sixty times a second
            // and end as a crash report naming this mod. Close the screen instead: the player
            // loses a statistics panel, not their session.
            LOGGER.error("Actually Played could not draw its statistics screen.", t);
            minecraft.setScreen(parent);
        }
    }

    private void draw(GuiGraphics graphics, ScreenLine line) {
        String text = resolve(line);
        int y = top + line.getY();

        if (line.isTruncated()) {
            // A server label is whatever the player typed into their server list, so its length
            // is unbounded and a long one would run off both edges. Trimming needs the font,
            // which is why core hands this decision over rather than making it.
            text = font.plainSubstrByWidth(text,
                    Math.min(width - 20, StatsScreenModel.RULE_HALF_WIDTH * 2));
        }

        switch (line.getAlign()) {
            case LEFT -> graphics.drawString(font, text, width / 2 + line.getX(), y, 0xFFFFFF);
            case RIGHT -> graphics.drawString(font, text,
                    width / 2 + line.getX() - font.width(text), y, 0xFFFFFF);
            case CENTER -> {
                graphics.drawCenteredString(font, text, width / 2 + line.getX(), y, 0xFFFFFF);
                if (line.getKind() == ScreenLine.Kind.SECTION_HEADING) {
                    drawRules(graphics, text, y);
                }
            }
        }
    }

    /** A thin rule either side of a section heading, to separate blocks without boxing them. */
    private void drawRules(GuiGraphics graphics, String heading, int y) {
        int half = StatsScreenModel.RULE_HALF_WIDTH;
        int gap = font.width(heading) / 2 + 8;
        graphics.hLine(width / 2 - half, width / 2 - gap, y + 3, 0xFF555555);
        graphics.hLine(width / 2 + gap, width / 2 + half, y + 3, 0xFF555555);
    }

    private String resolve(ScreenLine line) {
        List<TextSpan> spans = line.getSpans();
        StringBuilder text = new StringBuilder();
        for (TextSpan span : spans) {
            text.append(format(span.getStyle()));
            text.append(span.isTranslated()
                    ? I18n.get(span.getKey(), (Object[]) span.getArgs())
                    : span.getText());
        }
        return text.toString();
    }

    /** The one mapping a port has to rewrite: core's palette onto this version's codes. */
    private static ChatFormatting format(TextStyle style) {
        return switch (style) {
            case GRAY -> ChatFormatting.GRAY;
            case DARK_GRAY -> ChatFormatting.DARK_GRAY;
            case YELLOW -> ChatFormatting.YELLOW;
            case GREEN -> ChatFormatting.GREEN;
            case RED -> ChatFormatting.RED;
            case WHITE -> ChatFormatting.WHITE;
        };
    }

    @Override
    public boolean isPauseScreen() {
        // Keeps the world running behind the screen, so opening the statistics does not itself
        // change what is being measured.
        return false;
    }
}

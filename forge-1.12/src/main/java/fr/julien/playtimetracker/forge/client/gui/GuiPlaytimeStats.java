package fr.julien.playtimetracker.forge.client.gui;

import fr.julien.playtimetracker.core.PlaytimeTracker;
import fr.julien.playtimetracker.core.engine.ActivityState;
import fr.julien.playtimetracker.core.engine.SessionSnapshot;
import fr.julien.playtimetracker.core.model.PlayerPlaytime;
import fr.julien.playtimetracker.core.model.TargetType;
import fr.julien.playtimetracker.core.model.TrackedTarget;
import fr.julien.playtimetracker.core.util.DateFormatter;
import fr.julien.playtimetracker.core.util.DurationFormatter;
import fr.julien.playtimetracker.forge.bridge.TargetResolver;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Playtime for the place the player is right now — this server, or this world.
 *
 * <h3>Why only the current destination</h3>
 * An earlier version listed every tracked destination and let the player drill into one.
 * That was a catalogue, and a catalogue answers a question nobody asks mid-game. What a
 * player wants while playing is "how long have I actually been on <em>this</em> server",
 * so the screen answers exactly that, with no list and no navigation.
 * <p>
 * Storage is unchanged: every destination is still recorded separately, and its history is
 * waiting whenever the player comes back to it.
 *
 * <h3>The session in progress is counted in</h3>
 * Totals include the running session. A player thinks of "time on this server" as
 * including right now; showing only closed sessions would make the screen look stale the
 * instant they opened it.
 */
public class GuiPlaytimeStats extends GuiScreen {

    private static final Logger LOGGER = LogManager.getLogger("playtimetracker");

    private static final int BUTTON_DONE = 200;
    private static final int RULE_HALF_WIDTH = 150;

    private final GuiScreen parent;
    private final PlaytimeTracker tracker;
    private final ZoneId zone = ZoneId.systemDefault();

    private TrackedTarget target;

    /**
     * Totals of the closed sessions, read once when the screen opens.
     * <p>
     * They are derived by walking every stored session, and nothing can close a session
     * while this screen is up — so recomputing them sixty times a second would traverse the
     * whole history for an answer that cannot have changed.
     */
    private long recordedActive;
    private long recordedAfk;
    private int recordedSessions;
    private long recordedLongest;
    private long recordedFirstSeen;

    public GuiPlaytimeStats(GuiScreen parent, PlaytimeTracker tracker) {
        this.parent = parent;
        this.tracker = tracker;
    }

    @Override
    public void initGui() {
        try {
            build();
        } catch (Throwable t) {
            LOGGER.error("Playtime Tracker could not open its statistics screen.", t);
            mc.displayGuiScreen(parent);
        }
    }

    private void build() {
        buttonList.clear();
        buttonList.add(new GuiButton(BUTTON_DONE, width / 2 - 100, height - 28, I18n.format("gui.done")));

        // Resolved once: the recorded totals only change when a session closes, which
        // cannot happen while this screen is open.
        PlayerPlaytime player = tracker.getData().find(new TargetResolver(mc).resolvePlayerId());
        Optional<SessionSnapshot> snapshot = tracker.snapshot();
        target = player == null || !snapshot.isPresent()
                ? null
                : player.find(snapshot.get().getTarget());

        recordedActive = target == null ? 0L : target.getTotalActiveMillis();
        recordedAfk = target == null ? 0L : target.getTotalAfkMillis();
        recordedSessions = target == null ? 0 : target.getSessionCount();
        recordedLongest = target == null ? 0L : target.getLongestSessionMillis();
        recordedFirstSeen = target == null ? 0L : target.getFirstSeenAt();
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
            render(mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            // Rendering runs every frame, so a failure here would repeat sixty times a
            // second and end as a crash report naming this mod. Close the screen instead:
            // the player loses a statistics panel, not their session.
            LOGGER.error("Playtime Tracker could not draw its statistics screen.", t);
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

        drawCenteredString(fontRenderer, I18n.format("playtimetracker.gui.title"), width / 2, 14, 0xFFFFFF);

        Optional<SessionSnapshot> snapshot = tracker.snapshot();
        if (!snapshot.isPresent()) {
            drawCenteredString(fontRenderer,
                    TextFormatting.GRAY + I18n.format("playtimetracker.gui.noSession"),
                    width / 2, height / 2 - 8, 0xFFFFFF);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        SessionSnapshot session = snapshot.get();
        drawDestination(session);
        drawCurrentSession(session);
        drawTotals(session);
        drawDetails(session);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawDestination(SessionSnapshot session) {
        String name = target != null ? target.getDisplayName() : session.getTarget().getId();
        boolean world = session.getTarget().getType() == TargetType.SINGLEPLAYER;
        String type = I18n.format(world
                ? "playtimetracker.gui.type.world"
                : "playtimetracker.gui.type.server");

        drawCenteredString(fontRenderer, TextFormatting.YELLOW + name, width / 2, 34, 0xFFFFFF);

        // The address is worth showing for a server, where the label is a nickname the
        // player chose; for a world it would merely repeat the name above.
        String subtitle = world ? type : type + " - " + session.getTarget().getId();
        drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY + subtitle, width / 2, 46, 0x808080);
    }

    private void drawCurrentSession(SessionSnapshot session) {
        String state = session.getState() == ActivityState.AFK
                ? TextFormatting.YELLOW + I18n.format("playtimetracker.gui.state.afk",
                        DurationFormatter.format(session.getIdleMillis()))
                : TextFormatting.GREEN + I18n.format("playtimetracker.gui.state.active");

        drawSectionTitle("playtimetracker.gui.section.session", 68);
        drawCenteredString(fontRenderer,
                TextFormatting.GRAY + I18n.format("playtimetracker.gui.status") + " " + state,
                width / 2, 84, 0xFFFFFF);
        drawCenteredString(fontRenderer, pair(session.getActiveMillis(), session.getAfkMillis()),
                width / 2, 96, 0xFFFFFF);
    }

    private void drawTotals(SessionSnapshot session) {
        long active = recordedActive + session.getActiveMillis();
        long afk = recordedAfk + session.getAfkMillis();
        long total = active + afk;
        double ratio = total == 0L ? 0.0d : (double) active / (double) total;

        drawSectionTitle("playtimetracker.gui.section.destination", 118);
        drawCenteredString(fontRenderer, pair(active, afk), width / 2, 134, 0xFFFFFF);
        drawCenteredString(fontRenderer,
                TextFormatting.GRAY + I18n.format("playtimetracker.gui.detail.ratio") + " "
                        + ratioColour(ratio) + DurationFormatter.formatPercent(ratio),
                width / 2, 146, 0xFFFFFF);
    }

    private void drawDetails(SessionSnapshot session) {
        drawSectionTitle("playtimetracker.gui.section.details", 168);

        int sessions = recordedSessions + 1;
        long total = recordedActive + recordedAfk + session.getTotalMillis();
        long longest = Math.max(recordedLongest, session.getTotalMillis());
        long firstSeen = recordedFirstSeen == 0L
                ? session.getStartedAt()
                : Math.min(recordedFirstSeen, session.getStartedAt());

        int left = width / 2 - RULE_HALF_WIDTH + 6;
        int right = width / 2 + RULE_HALF_WIDTH - 6;
        int y = 184;

        drawPair(left, y, "playtimetracker.gui.detail.firstSeen", DateFormatter.formatDate(firstSeen, zone));
        drawPair(left, y + 12, "playtimetracker.gui.detail.sessionCount", String.valueOf(sessions));

        drawPairRight(right, y, "playtimetracker.gui.detail.average",
                DurationFormatter.format(total / sessions));
        drawPairRight(right, y + 12, "playtimetracker.gui.detail.longest",
                DurationFormatter.format(longest));
    }

    // --- helpers ------------------------------------------------------------------

    /** "Played: 5h 12m    AFK: 48m 3s" — the mod's one recurring pair of numbers. */
    private String pair(long active, long afk) {
        return TextFormatting.GRAY + I18n.format("playtimetracker.gui.played") + " "
                + TextFormatting.WHITE + DurationFormatter.format(active)
                + TextFormatting.GRAY + "    " + I18n.format("playtimetracker.gui.afk") + " "
                + TextFormatting.WHITE + DurationFormatter.format(afk);
    }

    /** Green above 80% played, red below 40%: readable at a glance, no legend needed. */
    private TextFormatting ratioColour(double ratio) {
        if (ratio >= 0.8d) {
            return TextFormatting.GREEN;
        }
        if (ratio < 0.4d) {
            return TextFormatting.RED;
        }
        return TextFormatting.YELLOW;
    }

    /** Section heading with a thin rule either side, to separate blocks without boxing them. */
    private void drawSectionTitle(String key, int y) {
        String label = TextFormatting.GRAY + I18n.format(key);
        drawCenteredString(fontRenderer, label, width / 2, y, 0xFFFFFF);

        int gap = fontRenderer.getStringWidth(label) / 2 + 8;
        drawHorizontalLine(width / 2 - RULE_HALF_WIDTH, width / 2 - gap, y + 3, 0xFF555555);
        drawHorizontalLine(width / 2 + gap, width / 2 + RULE_HALF_WIDTH, y + 3, 0xFF555555);
    }

    private void drawPair(int x, int y, String labelKey, String value) {
        drawString(fontRenderer, TextFormatting.GRAY + I18n.format(labelKey) + " "
                + TextFormatting.WHITE + value, x, y, 0xFFFFFF);
    }

    private void drawPairRight(int rightEdge, int y, String labelKey, String value) {
        String label = TextFormatting.GRAY + I18n.format(labelKey) + " " + TextFormatting.WHITE + value;
        drawString(fontRenderer, label, rightEdge - fontRenderer.getStringWidth(label), y, 0xFFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        // Keeps the world running behind the screen, so opening the stats does not itself
        // change what is being measured.
        return false;
    }
}

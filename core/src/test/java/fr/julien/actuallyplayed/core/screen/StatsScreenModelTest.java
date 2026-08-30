package fr.julien.actuallyplayed.core.screen;

import fr.julien.actuallyplayed.core.engine.ActivityState;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.engine.Snapshots;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.model.TrackedSession;
import fr.julien.actuallyplayed.core.model.TrackedTarget;
import org.junit.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers what the statistics screen decides, now that deciding it is no longer entangled with
 * drawing it. None of this was testable while the whole screen lived in the Forge layer.
 */
public class StatsScreenModelTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");
    private static final TargetKey WORLD = TargetKey.singleplayer("New World");
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final ZoneId UTC = ZoneId.of("UTC");

    /** 2026-08-30T12:00:00Z, so the dates below read the same in every run. */
    private static final long NOON = 1788091200000L;

    // --- helpers ---------------------------------------------------------------------

    private static TrackedTarget targetWith(String displayName, long... activeAfkPairs) {
        TrackedTarget target = new TrackedTarget(SERVER);
        target.setDisplayName(displayName);
        long startedAt = NOON - 30L * 24L * HOUR;
        for (int i = 0; i < activeAfkPairs.length; i += 2) {
            long active = activeAfkPairs[i];
            long afk = activeAfkPairs[i + 1];
            target.addSession(new TrackedSession(
                    PLAYER, SERVER, startedAt, startedAt + active + afk, active, afk));
            startedAt += 24L * HOUR;
        }
        return target;
    }

    /**
     * All the text of a row, translation keys included verbatim.
     * <p>
     * The alignment is part of the address, not decoration: the details block puts two rows
     * on each of y=170 and y=182 — first-seen and session count anchored left, average and
     * longest anchored right.
     */
    private static String textAt(StatsScreenModel model, int y, ScreenLine.Align align) {
        StringBuilder text = new StringBuilder();
        for (TextSpan span : lineAt(model, y, align).getSpans()) {
            text.append(span.isTranslated() ? span.getKey() : span.getText());
        }
        return text.toString();
    }

    /** For the rows above the details block, where a vertical offset is unambiguous. */
    private static String textAt(StatsScreenModel model, int y) {
        return textAt(model, y, ScreenLine.Align.CENTER);
    }

    private static TextStyle styleOfLastSpanAt(StatsScreenModel model, int y) {
        List<TextSpan> spans = lineAt(model, y, ScreenLine.Align.CENTER).getSpans();
        return spans.get(spans.size() - 1).getStyle();
    }

    private static ScreenLine lineAt(StatsScreenModel model, int y, ScreenLine.Align align) {
        for (ScreenLine line : model.getLines()) {
            if (line.getY() == y && line.getAlign() == align) {
                return line;
            }
        }
        throw new AssertionError("No " + align + " line at y=" + y);
    }

    // --- totals ----------------------------------------------------------------------

    @Test
    public void totalsIncludeTheSessionInProgress() {
        // Two closed hours, plus half an hour running right now.
        TrackedTarget target = targetWith("Hypixel", HOUR, 0L, HOUR, 0L);
        SessionSnapshot session = Snapshots.active(SERVER, NOON, 30 * MINUTE, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.of(target), UTC);

        // The destination block, at y=120.
        assertTrue("expected 2h 30m in the destination totals, got: " + textAt(model, 120),
                textAt(model, 120).contains("2h 30m"));
    }

    @Test
    public void afkOfTheRunningSessionCountsTowardsTheRatio() {
        TrackedTarget target = targetWith("Hypixel", HOUR, 0L);
        SessionSnapshot session = Snapshots.active(SERVER, NOON, 0L, HOUR);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.of(target), UTC);

        // One hour played against one hour AFK.
        assertTrue(textAt(model, 132).contains("50%"));
    }

    @Test
    public void aDestinationWithNoHistoryShowsOnlyTheRunningSession() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, 45 * MINUTE, 15 * MINUTE);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        assertTrue(textAt(model, 120).contains("45m 0s"));
        assertTrue(textAt(model, 120).contains("15m 0s"));
        assertTrue("the running session is the first and only one",
                textAt(model, 182, ScreenLine.Align.LEFT).contains("1"));
    }

    // --- ratio colour ----------------------------------------------------------------

    @Test
    public void ratioIsGreenAtEightyPercentAndRedBelowForty() {
        assertEquals(TextStyle.GREEN, StatsScreenModel.ratioStyle(0.8d));
        assertEquals(TextStyle.GREEN, StatsScreenModel.ratioStyle(1.0d));
        assertEquals(TextStyle.YELLOW, StatsScreenModel.ratioStyle(0.79d));
        assertEquals(TextStyle.YELLOW, StatsScreenModel.ratioStyle(0.4d));
        assertEquals(TextStyle.RED, StatsScreenModel.ratioStyle(0.39d));
        assertEquals(TextStyle.RED, StatsScreenModel.ratioStyle(0.0d));
    }

    @Test
    public void theRatioRowCarriesThatColour() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 9 * HOUR);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        assertEquals(TextStyle.RED, styleOfLastSpanAt(model, 132));
    }

    @Test
    public void aDestinationWithNoTimeAtAllDoesNotDivideByZero() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, 0L, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        assertTrue(textAt(model, 132).contains("0%"));
    }

    // --- details ---------------------------------------------------------------------

    @Test
    public void theAverageSpreadsEveryDestinationHourOverEverySessionIncludingThisOne() {
        // Two closed sessions of one hour, plus two hours running: 4h over 3 sessions.
        TrackedTarget target = targetWith("Hypixel", HOUR, 0L, HOUR, 0L);
        SessionSnapshot session = Snapshots.active(SERVER, NOON, 2 * HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.of(target), UTC);

        assertTrue("expected an average of 1h 20m, got: " + textAt(model, 170, ScreenLine.Align.RIGHT),
                textAt(model, 170, ScreenLine.Align.RIGHT).contains("1h 20m"));
        assertTrue(textAt(model, 182, ScreenLine.Align.LEFT).contains("3"));
    }

    @Test
    public void theLongestSessionCanBeTheOneStillRunning() {
        TrackedTarget target = targetWith("Hypixel", HOUR, 0L);
        SessionSnapshot session = Snapshots.active(SERVER, NOON, 3 * HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.of(target), UTC);

        assertTrue(textAt(model, 182, ScreenLine.Align.RIGHT).contains("3h 0m"));
    }

    @Test
    public void firstSeenPredatesTheRunningSessionWhenHistoryExists() {
        TrackedTarget target = targetWith("Hypixel", HOUR, 0L);
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.of(target), UTC);

        // The history starts 30 days before noon on 2026-08-30.
        assertTrue("expected the stored first-seen date, got: " + textAt(model, 170, ScreenLine.Align.LEFT),
                textAt(model, 170, ScreenLine.Align.LEFT).contains("2026-07-31"));
    }

    @Test
    public void firstSeenFallsBackToTheRunningSessionWithNoHistory() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        assertTrue(textAt(model, 170, ScreenLine.Align.LEFT).contains("2026-08-30"));
    }

    // --- destination header -----------------------------------------------------------

    @Test
    public void aServerShowsItsLabelAboveItsAddress() {
        TrackedTarget target = targetWith("My favourite server", HOUR, 0L);
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.of(target), UTC);

        assertEquals("My favourite server", textAt(model, 20));
        assertTrue(textAt(model, 32).contains("mc.hypixel.net:25565"));
    }

    @Test
    public void aWorldDoesNotRepeatItsNameUnderneath() {
        SessionSnapshot session = Snapshots.active(WORLD, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        assertEquals("New World", textAt(model, 20));
        assertEquals("actuallyplayed.gui.type.world", textAt(model, 32));
    }

    @Test
    public void anUnlabelledDestinationFallsBackToItsKey() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        assertEquals("mc.hypixel.net:25565", textAt(model, 20));
    }

    @Test
    public void aLabelOfUnboundedLengthIsMarkedForTrimming() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        for (ScreenLine line : model.getLines()) {
            if (line.getY() == 20 || line.getY() == 32) {
                assertTrue("row " + line.getY() + " must be trimmed by the platform",
                        line.isTruncated());
            }
        }
    }

    // --- state -------------------------------------------------------------------------

    @Test
    public void theAfkRowCarriesHowLongTheIdleHasLasted() {
        SessionSnapshot session = Snapshots.of(
                SERVER, NOON, HOUR, 5 * MINUTE, ActivityState.AFK, 7 * MINUTE);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        ScreenLine status = lineAt(model, 70, ScreenLine.Align.CENTER);
        TextSpan state = status.getSpans().get(status.getSpans().size() - 1);
        assertEquals("actuallyplayed.gui.state.afk", state.getKey());
        assertEquals(TextStyle.YELLOW, state.getStyle());
        assertEquals("7m 0s", state.getArgs()[0]);
    }

    @Test
    public void thePlayingRowTakesNoArgument() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        ScreenLine status = lineAt(model, 70, ScreenLine.Align.CENTER);
        TextSpan state = status.getSpans().get(status.getSpans().size() - 1);
        assertEquals("actuallyplayed.gui.state.active", state.getKey());
        assertEquals(TextStyle.GREEN, state.getStyle());
        assertEquals(0, state.getArgs().length);
    }

    // --- no session ---------------------------------------------------------------------

    @Test
    public void withoutASessionTheScreenSaysSoAndNothingElse() {
        StatsScreenModel model = StatsScreenModel.withoutSession();

        assertEquals(2, model.getLines().size());
        assertEquals("actuallyplayed.gui.title", textAt(model, 0));
        assertEquals("actuallyplayed.gui.noSession", textAt(model, 40));
    }

    // --- layout -------------------------------------------------------------------------

    @Test
    public void everyRowFitsInsideTheDeclaredContentHeight() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        for (ScreenLine line : model.getLines()) {
            assertTrue("row at y=" + line.getY() + " overflows CONTENT_HEIGHT",
                    line.getY() < StatsScreenModel.CONTENT_HEIGHT);
        }
    }

    @Test
    public void theDetailColumnsStayInsideTheRules() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);

        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        for (ScreenLine line : model.getLines()) {
            assertTrue("row at y=" + line.getY() + " starts outside the rules",
                    Math.abs(line.getX()) <= StatsScreenModel.RULE_HALF_WIDTH);
        }
    }

    @Test
    public void spansCannotBeMutatedThroughTheirArguments() {
        SessionSnapshot session = Snapshots.of(
                SERVER, NOON, HOUR, 0L, ActivityState.AFK, MINUTE);
        StatsScreenModel model = StatsScreenModel.of(session, RecordedTotals.empty(), UTC);

        ScreenLine status = lineAt(model, 70, ScreenLine.Align.CENTER);
        TextSpan state = status.getSpans().get(status.getSpans().size() - 1);
        state.getArgs()[0] = "tampered";

        assertFalse("tampered".equals(state.getArgs()[0]));
    }
}

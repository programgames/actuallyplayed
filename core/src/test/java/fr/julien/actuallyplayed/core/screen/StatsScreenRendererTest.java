package fr.julien.actuallyplayed.core.screen;

import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.engine.Snapshots;
import fr.julien.actuallyplayed.core.model.TargetKey;
import org.junit.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the layout walk that every Minecraft version shares.
 * <p>
 * Nothing here was reachable while the walk lived in each version's screen: it took a live
 * Minecraft to run, so five copies of it were checked only by looking at them in game. This is
 * the return on moving it into {@code core}.
 */
public class StatsScreenRendererTest {

    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long HOUR = 3_600_000L;

    /** 2026-08-30T12:00:00Z. */
    private static final long NOON = 1788091200000L;

    /** Records what it was asked to draw, so a test can assert on it. */
    private static final class RecordingPainter implements ScreenPainter, Translator {

        final List<String> calls = new ArrayList<String>();
        int trimWidth = -1;

        @Override
        public void drawLeft(String text, int x, int y) {
            calls.add("left@" + x + "," + y + " " + text);
        }

        @Override
        public void drawRight(String text, int x, int y) {
            calls.add("right@" + x + "," + y + " " + text);
        }

        @Override
        public void drawCentered(String text, int x, int y) {
            calls.add("centre@" + x + "," + y + " " + text);
        }

        @Override
        public void horizontalLine(int fromX, int toX, int y, int colour) {
            calls.add("rule@" + fromX + ".." + toX + "," + y);
        }

        @Override
        public int width(String text) {
            // One pixel a character is wrong for a real font and exactly right here: it makes
            // the rule positions arithmetic rather than a matter of which font is loaded.
            return text.length();
        }

        @Override
        public String trim(String text, int maxWidth) {
            trimWidth = maxWidth;
            return text.length() <= maxWidth ? text : text.substring(0, maxWidth);
        }

        @Override
        public String translate(String key, String... args) {
            // The platform's I18n returns the key when it has no entry; doing the same keeps a
            // missing translation visible rather than blank.
            return args.length == 0 ? key : key + "(" + String.join(",", args) + ")";
        }

        String joined() {
            return String.join("\n", calls);
        }
    }

    private static StatsScreenModel model() {
        SessionSnapshot session = Snapshots.active(SERVER, NOON, HOUR, 0L);
        return StatsScreenModel.of(session, RecordedTotals.empty(), UTC);
    }

    @Test
    public void everyRowOfTheModelIsDrawn() {
        RecordingPainter painter = new RecordingPainter();
        StatsScreenModel model = model();

        StatsScreenRenderer.render(model, painter, painter, 200, 10, 300);

        // Every line produces one draw call; section headings add two rules on top.
        int headings = 0;
        for (ScreenLine line : model.getLines()) {
            if (line.getKind() == ScreenLine.Kind.SECTION_HEADING) {
                headings++;
            }
        }
        assertEquals(model.getLines().size() + headings * 2, painter.calls.size());
    }

    @Test
    public void offsetsAreResolvedAgainstTheCentreAndTheTop() {
        RecordingPainter painter = new RecordingPainter();

        StatsScreenRenderer.render(model(), painter, painter, 200, 10, 300);

        // The title sits at the model's origin: dead centre, at the top of the content block.
        assertTrue(painter.joined().contains("centre@200,10 "));
    }

    @Test
    public void aLeftColumnIsAnchoredLeftAndARightColumnRight() {
        RecordingPainter painter = new RecordingPainter();

        StatsScreenRenderer.render(model(), painter, painter, 200, 0, 300);

        // The details block insets its columns six pixels inside the rules.
        int left = 200 - StatsScreenModel.RULE_HALF_WIDTH + 6;
        int right = 200 + StatsScreenModel.RULE_HALF_WIDTH - 6;
        assertTrue(painter.joined().contains("left@" + left + ","));
        assertTrue(painter.joined().contains("right@" + right + ","));
    }

    @Test
    public void aSectionHeadingGetsARuleOnEachSide() {
        RecordingPainter painter = new RecordingPainter();

        StatsScreenRenderer.render(model(), painter, painter, 200, 0, 300);

        long rules = painter.calls.stream().filter(call -> call.startsWith("rule@")).count();
        assertEquals("three sections, two rules each", 6, rules);

        // They stop short of the heading rather than running under it.
        for (String call : painter.calls) {
            if (!call.startsWith("rule@")) {
                continue;
            }
            String span = call.substring("rule@".length(), call.indexOf(','));
            String[] ends = span.split("\\.\\.");
            int from = Integer.parseInt(ends[0]);
            int to = Integer.parseInt(ends[1]);
            assertTrue("a rule must run outwards from the heading", from < to);
            assertTrue(from >= 200 - StatsScreenModel.RULE_HALF_WIDTH);
            assertTrue(to <= 200 + StatsScreenModel.RULE_HALF_WIDTH);
        }
    }

    @Test
    public void onlyTheRowsThatAskForItAreTrimmed() {
        RecordingPainter painter = new RecordingPainter();

        StatsScreenRenderer.render(model(), painter, painter, 200, 0, 42);

        assertEquals("the platform's width, passed through untouched", 42, painter.trimWidth);
    }

    @Test
    public void translationKeysAreResolvedAndLiteralsAreNot() {
        RecordingPainter painter = new RecordingPainter();

        StatsScreenRenderer.render(model(), painter, painter, 200, 0, 300);

        String drawn = painter.joined();
        // The destination's key is a literal produced by core and must survive as it stands.
        assertTrue(drawn.contains("mc.hypixel.net:25565"));
        // Durations are literals too: they read the same in every language.
        assertTrue(drawn.contains("1h 0m"));
    }

    @Test
    public void everyRowCarriesAColourCode() {
        RecordingPainter painter = new RecordingPainter();

        StatsScreenRenderer.render(model(), painter, painter, 200, 0, 300);

        for (String call : painter.calls) {
            if (call.startsWith("rule@")) {
                continue;
            }
            assertTrue("a row drawn with no colour would inherit the previous one: " + call,
                    call.contains("\u00a7"));
        }
    }

    @Test
    public void withoutASessionOnlyTheTitleAndItsNoticeAreDrawn() {
        RecordingPainter painter = new RecordingPainter();

        StatsScreenRenderer.render(StatsScreenModel.withoutSession(), painter, painter, 200, 0, 300);

        assertEquals(2, painter.calls.size());
        assertTrue(painter.joined().contains("actuallyplayed.gui.title"));
        assertTrue(painter.joined().contains("actuallyplayed.gui.noSession"));
        assertFalse(painter.joined().contains("rule@"));
    }
}

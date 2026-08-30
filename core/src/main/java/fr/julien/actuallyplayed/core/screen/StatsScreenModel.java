package fr.julien.actuallyplayed.core.screen;

import fr.julien.actuallyplayed.core.engine.ActivityState;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.TargetType;
import fr.julien.actuallyplayed.core.util.DateFormatter;
import fr.julien.actuallyplayed.core.util.DurationFormatter;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Everything the statistics screen says, as data — with no idea how any of it is painted.
 *
 * <h3>Why this lives in core</h3>
 * The screen used to be one class in the Forge layer, mixing two concerns: <em>what to
 * show</em> — walking the stored sessions, formatting {@code 5h 12m}, deciding that 83&nbsp;%
 * renders green, composing the three blocks — and <em>how to paint it</em>. The first is pure
 * Java, so it belongs here where it is testable and shared by every port; the second is the
 * only part that differs between Minecraft versions, and it is also the part that 1.21.5's
 * render-pipeline rewrite breaks. Keeping it down to a draw loop over these lines is what
 * makes that break cheap. See {@code PORTING.md} §4.2.
 *
 * <h3>The session in progress is counted in</h3>
 * Totals add the running session to the recorded ones. A player thinks of "time on this
 * server" as including right now; showing only closed sessions would make the screen look
 * stale the instant they opened it.
 */
public final class StatsScreenModel {

    private static final String KEY_TITLE = "actuallyplayed.gui.title";
    private static final String KEY_NO_SESSION = "actuallyplayed.gui.noSession";
    private static final String KEY_TYPE_WORLD = "actuallyplayed.gui.type.world";
    private static final String KEY_TYPE_SERVER = "actuallyplayed.gui.type.server";
    private static final String KEY_SECTION_SESSION = "actuallyplayed.gui.section.session";
    private static final String KEY_SECTION_DESTINATION = "actuallyplayed.gui.section.destination";
    private static final String KEY_SECTION_DETAILS = "actuallyplayed.gui.section.details";
    private static final String KEY_STATUS = "actuallyplayed.gui.status";
    private static final String KEY_STATE_ACTIVE = "actuallyplayed.gui.state.active";
    private static final String KEY_STATE_AFK = "actuallyplayed.gui.state.afk";
    private static final String KEY_PLAYED = "actuallyplayed.gui.played";
    private static final String KEY_AFK = "actuallyplayed.gui.afk";
    private static final String KEY_RATIO = "actuallyplayed.gui.detail.ratio";
    private static final String KEY_FIRST_SEEN = "actuallyplayed.gui.detail.firstSeen";
    private static final String KEY_SESSION_COUNT = "actuallyplayed.gui.detail.sessionCount";
    private static final String KEY_AVERAGE = "actuallyplayed.gui.detail.average";
    private static final String KEY_LONGEST = "actuallyplayed.gui.detail.longest";

    /**
     * Height of the whole block of text, so the platform can centre it vertically.
     * <p>
     * Every row used to be placed at a literal distance from the top of the screen. That is
     * fine at GUI scale 4, where the scaled height is barely larger than the content — and
     * absurd at GUI scale 1 on a 1440p monitor, where the content huddles in the top 200
     * pixels and the Done button sits a thousand pixels below it.
     */
    public static final int CONTENT_HEIGHT = 190;

    /** How far a section rule reaches either side of the centre. Also the content width. */
    public static final int RULE_HALF_WIDTH = 150;

    /** Inset of the left and right detail columns from the rule ends. */
    private static final int COLUMN_INSET = 6;

    /** Above this share of time actually played, the ratio renders green. */
    private static final double RATIO_GOOD = 0.8d;

    /** Below this share, red. */
    private static final double RATIO_POOR = 0.4d;

    private final List<ScreenLine> lines;

    private StatsScreenModel(List<ScreenLine> lines) {
        this.lines = Collections.unmodifiableList(lines);
    }

    /** What the screen shows when the player is not in a world or on a server. */
    public static StatsScreenModel withoutSession() {
        List<ScreenLine> lines = new ArrayList<ScreenLine>();
        lines.add(ScreenLine.centered(0, ScreenLine.Kind.TITLE,
                TextSpan.translated(KEY_TITLE, TextStyle.WHITE)));
        lines.add(ScreenLine.centered(40, ScreenLine.Kind.BODY,
                TextSpan.translated(KEY_NO_SESSION, TextStyle.GRAY)));
        return new StatsScreenModel(lines);
    }

    /**
     * Builds the screen for the destination the player is on right now.
     *
     * @param session  the session in progress
     * @param recorded what is already stored for this destination, read once by the caller
     * @param zone     the time zone the first-seen date is rendered in
     */
    public static StatsScreenModel of(SessionSnapshot session, RecordedTotals recorded, ZoneId zone) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(recorded, "recorded");
        Objects.requireNonNull(zone, "zone");

        List<ScreenLine> lines = new ArrayList<ScreenLine>();
        lines.add(ScreenLine.centered(0, ScreenLine.Kind.TITLE,
                TextSpan.translated(KEY_TITLE, TextStyle.WHITE)));

        addDestination(lines, session, recorded);
        addCurrentSession(lines, session);
        addTotals(lines, session, recorded.getActiveMillis(), recorded.getAfkMillis());
        addDetails(lines, session, zone,
                recorded.getActiveMillis(), recorded.getAfkMillis(), recorded.getSessionCount(),
                recorded.getLongestSessionMillis(), recorded.getFirstSeenAt());

        return new StatsScreenModel(lines);
    }

    private static void addDestination(List<ScreenLine> lines,
                                       SessionSnapshot session,
                                       RecordedTotals recorded) {
        String name = recorded.getDisplayName() != null
                ? recorded.getDisplayName()
                : session.getTarget().getId();
        boolean world = session.getTarget().getType() == TargetType.SINGLEPLAYER;

        lines.add(ScreenLine.centeredTruncated(20, TextSpan.literal(name, TextStyle.YELLOW)));

        // The address is worth showing for a server, where the label above is a nickname the
        // player chose; for a world it would merely repeat the name.
        if (world) {
            lines.add(ScreenLine.centeredTruncated(32,
                    TextSpan.translated(KEY_TYPE_WORLD, TextStyle.DARK_GRAY)));
        } else {
            lines.add(ScreenLine.centeredTruncated(32,
                    TextSpan.translated(KEY_TYPE_SERVER, TextStyle.DARK_GRAY),
                    TextSpan.literal(" - " + session.getTarget().getId(), TextStyle.DARK_GRAY)));
        }
    }

    private static void addCurrentSession(List<ScreenLine> lines, SessionSnapshot session) {
        TextSpan state = session.getState() == ActivityState.AFK
                ? TextSpan.translated(KEY_STATE_AFK, TextStyle.YELLOW,
                        DurationFormatter.format(session.getIdleMillis()))
                : TextSpan.translated(KEY_STATE_ACTIVE, TextStyle.GREEN);

        lines.add(ScreenLine.centered(54, ScreenLine.Kind.SECTION_HEADING,
                TextSpan.translated(KEY_SECTION_SESSION, TextStyle.GRAY)));
        lines.add(ScreenLine.centered(70, ScreenLine.Kind.BODY,
                TextSpan.translated(KEY_STATUS, TextStyle.GRAY),
                TextSpan.literal(" ", TextStyle.GRAY),
                state));
        lines.add(playedAndAfk(82, session.getActiveMillis(), session.getAfkMillis()));
    }

    private static void addTotals(List<ScreenLine> lines,
                                  SessionSnapshot session,
                                  long recordedActive,
                                  long recordedAfk) {
        long active = recordedActive + session.getActiveMillis();
        long afk = recordedAfk + session.getAfkMillis();
        long total = active + afk;
        double ratio = total == 0L ? 0.0d : (double) active / (double) total;

        lines.add(ScreenLine.centered(104, ScreenLine.Kind.SECTION_HEADING,
                TextSpan.translated(KEY_SECTION_DESTINATION, TextStyle.GRAY)));
        lines.add(playedAndAfk(120, active, afk));
        lines.add(ScreenLine.centered(132, ScreenLine.Kind.BODY,
                TextSpan.translated(KEY_RATIO, TextStyle.GRAY),
                TextSpan.literal(" ", TextStyle.GRAY),
                TextSpan.literal(DurationFormatter.formatPercent(ratio), ratioStyle(ratio))));
    }

    private static void addDetails(List<ScreenLine> lines,
                                   SessionSnapshot session,
                                   ZoneId zone,
                                   long recordedActive,
                                   long recordedAfk,
                                   int recordedSessions,
                                   long recordedLongest,
                                   long recordedFirstSeen) {
        // The running session counts as one, and its time is part of the average — the same
        // reason totals include it: the screen must not look stale the instant it opens.
        int sessions = recordedSessions + 1;
        long total = recordedActive + recordedAfk + session.getTotalMillis();
        long longest = Math.max(recordedLongest, session.getTotalMillis());
        long firstSeen = recordedFirstSeen == 0L
                ? session.getStartedAt()
                : Math.min(recordedFirstSeen, session.getStartedAt());

        int left = -RULE_HALF_WIDTH + COLUMN_INSET;
        int right = RULE_HALF_WIDTH - COLUMN_INSET;

        lines.add(ScreenLine.centered(154, ScreenLine.Kind.SECTION_HEADING,
                TextSpan.translated(KEY_SECTION_DETAILS, TextStyle.GRAY)));

        lines.add(labelled(ScreenLine.Align.LEFT, left, 170,
                KEY_FIRST_SEEN, DateFormatter.formatDate(firstSeen, zone)));
        lines.add(labelled(ScreenLine.Align.LEFT, left, 182,
                KEY_SESSION_COUNT, String.valueOf(sessions)));
        lines.add(labelled(ScreenLine.Align.RIGHT, right, 170,
                KEY_AVERAGE, DurationFormatter.format(total / sessions)));
        lines.add(labelled(ScreenLine.Align.RIGHT, right, 182,
                KEY_LONGEST, DurationFormatter.format(longest)));
    }

    /** "Played: 5h 12m    AFK: 48m 3s" — the mod's one recurring pair of numbers. */
    private static ScreenLine playedAndAfk(int y, long active, long afk) {
        return ScreenLine.centered(y, ScreenLine.Kind.BODY,
                TextSpan.translated(KEY_PLAYED, TextStyle.GRAY),
                TextSpan.literal(" ", TextStyle.GRAY),
                TextSpan.literal(DurationFormatter.format(active), TextStyle.WHITE),
                TextSpan.literal("    ", TextStyle.GRAY),
                TextSpan.translated(KEY_AFK, TextStyle.GRAY),
                TextSpan.literal(" ", TextStyle.GRAY),
                TextSpan.literal(DurationFormatter.format(afk), TextStyle.WHITE));
    }

    private static ScreenLine labelled(ScreenLine.Align align, int x, int y,
                                       String labelKey, String value) {
        TextSpan[] spans = {
                TextSpan.translated(labelKey, TextStyle.GRAY),
                TextSpan.literal(" ", TextStyle.GRAY),
                TextSpan.literal(value, TextStyle.WHITE)
        };
        return align == ScreenLine.Align.LEFT
                ? ScreenLine.left(x, y, spans)
                : ScreenLine.right(x, y, spans);
    }

    /** Green above 80 % played, red below 40 %: readable at a glance, no legend needed. */
    static TextStyle ratioStyle(double ratio) {
        if (ratio >= RATIO_GOOD) {
            return TextStyle.GREEN;
        }
        if (ratio < RATIO_POOR) {
            return TextStyle.RED;
        }
        return TextStyle.YELLOW;
    }

    /** The rows to draw, in order, top to bottom. */
    public List<ScreenLine> getLines() {
        return lines;
    }
}

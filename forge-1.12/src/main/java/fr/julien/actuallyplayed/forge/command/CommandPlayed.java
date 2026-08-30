package fr.julien.actuallyplayed.forge.command;

import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.engine.ActivityState;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.PlayerPlaytime;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.model.TrackedTarget;
import fr.julien.actuallyplayed.core.util.DurationFormatter;
import fr.julien.actuallyplayed.forge.bridge.TargetResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * {@code /playtime} — the mod's answer in the chat window.
 *
 * <h3>Why a silent mod needs a command</h3>
 * The mod shows no HUD, says nothing on an AFK transition, and lives behind
 * Esc → Statistics → a button in a corner. A player who installs it without reading the
 * description has no way to tell it from a mod that does not work. A command is the
 * discoverable answer, and it stays true to the silent design: it speaks only when asked.
 * <p>
 * It also carries {@code reset}, which is the only way to clear a polluted destination
 * short of hand-editing the JSON file with the game closed.
 * <p>
 * Registered on {@code ClientCommandHandler}: it never reaches the server, so it works on
 * any server and requires no permission.
 */
public class CommandPlayed extends CommandBase {

    private final PlaytimeTracker tracker;
    private final Minecraft minecraft;

    public CommandPlayed(PlaytimeTracker tracker, Minecraft minecraft) {
        this.tracker = tracker;
        this.minecraft = minecraft;
    }

    /**
     * Deliberately not {@code /playtime}.
     * <p>
     * A client command registered on {@code ClientCommandHandler} is handled locally and
     * never forwarded to the server. Several server-side playtime mods own {@code /playtime},
     * so claiming it here would swallow their command and leave the player wondering why the
     * server never answers.
     */
    @Override
    public String getName() {
        return "played";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("ap");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "actuallyplayed.command.usage";
    }

    /** Zero, so the command works on a server where the player is not an operator. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, net.minecraft.util.math.BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "reset") : super
                .getTabCompletions(server, sender, args, pos);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        Optional<SessionSnapshot> snapshot = tracker.snapshot();
        if (!snapshot.isPresent()) {
            reply(sender, TextFormatting.GRAY, "actuallyplayed.gui.noSession");
            return;
        }

        if (args.length > 0 && "reset".equalsIgnoreCase(args[0])) {
            handleReset(sender, args);
            return;
        }
        printSummary(sender, snapshot.get());
    }

    private void printSummary(ICommandSender sender, SessionSnapshot session) {
        TrackedTarget target = findTarget(session.getTarget());
        String label = target != null ? target.getDisplayName() : session.getTarget().getId();

        long active = session.getActiveMillis() + (target == null ? 0L : target.getTotalActiveMillis());
        long afk = session.getAfkMillis() + (target == null ? 0L : target.getTotalAfkMillis());
        long total = active + afk;
        double ratio = total == 0L ? 0.0d : (double) active / (double) total;

        sender.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + label + TextFormatting.RESET));

        String state = session.getState() == ActivityState.AFK
                ? TextFormatting.YELLOW + translate("actuallyplayed.gui.state.afk",
                        DurationFormatter.format(session.getIdleMillis()))
                : TextFormatting.GREEN + translate("actuallyplayed.gui.state.active");
        sender.sendMessage(new TextComponentString(
                TextFormatting.GRAY + translate("actuallyplayed.gui.section.session") + ": " + state));

        sender.sendMessage(line("actuallyplayed.gui.played",
                DurationFormatter.format(session.getActiveMillis()),
                "actuallyplayed.gui.afk", DurationFormatter.format(session.getAfkMillis())));

        sender.sendMessage(new TextComponentString(
                TextFormatting.GRAY + translate("actuallyplayed.gui.section.destination") + ": "
                        + TextFormatting.WHITE + DurationFormatter.format(active)
                        + TextFormatting.GRAY + " / " + TextFormatting.WHITE
                        + DurationFormatter.format(afk)
                        + TextFormatting.GRAY + "  (" + DurationFormatter.formatPercent(ratio) + ")"));
    }

    /**
     * Reset asks for confirmation before it destroys anything. A single command that erases
     * a destination's history is one somebody will run by accident.
     */
    private void handleReset(ICommandSender sender, String[] args) {
        if (args.length < 2 || !"confirm".equalsIgnoreCase(args[1])) {
            reply(sender, TextFormatting.YELLOW, "actuallyplayed.command.reset.confirm");
            return;
        }
        Optional<TargetKey> reset = tracker.resetCurrentTarget();
        if (reset.isPresent()) {
            reply(sender, TextFormatting.GREEN, "actuallyplayed.command.reset.done");
        }
    }

    private TrackedTarget findTarget(TargetKey key) {
        PlayerPlaytime player = tracker.getData().find(new TargetResolver(minecraft).resolvePlayerId());
        return player == null ? null : player.find(key);
    }

    private ITextComponent line(String leftKey, String leftValue, String rightKey, String rightValue) {
        return new TextComponentString(
                TextFormatting.GRAY + translate(leftKey) + " " + TextFormatting.WHITE + leftValue
                        + TextFormatting.GRAY + "   " + translate(rightKey) + " "
                        + TextFormatting.WHITE + rightValue);
    }

    private void reply(ICommandSender sender, TextFormatting colour, String key) {
        sender.sendMessage(new TextComponentString(colour.toString())
                .appendSibling(new TextComponentTranslation(key)));
    }

    /** Client-side translation: the command never runs on a server. */
    private String translate(String key, Object... args) {
        return net.minecraft.client.resources.I18n.format(key, args);
    }
}

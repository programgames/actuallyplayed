package fr.julien.actuallyplayed.forge.event;

import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.engine.ActivityState;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.util.DurationFormatter;
import fr.julien.actuallyplayed.forge.config.ForgeConfig;
import fr.julien.actuallyplayed.forge.bridge.TargetIdentity;
import fr.julien.actuallyplayed.forge.bridge.TargetResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.io.IOException;
import java.util.Optional;

/**
 * Translates Minecraft events into the tracker's vocabulary. Contains no tracking rules
 * of its own — every decision about what counts as playtime lives in {@code core}.
 *
 * <h3>Failure isolation</h3>
 * Every entry point catches {@link Throwable} and, on the first failure, stops the mod for
 * the rest of the session. This mod runs on other machines alongside hundreds of others; an
 * exception escaping here would surface as a Minecraft crash report naming Actually Played.
 * A failure must cost the mod its function, never the player their session.
 *
 * <h3>Threading</h3>
 * Almost every call arrives on the Minecraft client thread, but {@link #shutdown()} runs on
 * the shutdown hook's own thread while the game loop may still be ticking. Both touch the
 * same session state, so every method that reads or writes a field is synchronised on this
 * instance. Without it, a session could be closed twice — or not at all — on exit.
 */
public final class PlaytimeClientHandler {

    /**
     * Camera movement below this many degrees per tick is treated as noise. A controller's
     * dead zone or a barely-nudged mouse should not keep the counter alive on its own.
     */
    private static final float ROTATION_EPSILON = 0.01f;

    /**
     * How long a real input keeps proving the window has focus after {@code Display} claims
     * otherwise. Long enough to cover a false negative, short enough that a genuine alt-tab
     * still registers almost at once - and the idle tail is rolled back anyway.
     */
    private static final long FOCUS_GRACE_MILLIS = 3000L;

    private final PlaytimeTracker tracker;
    private final TargetResolver resolver;
    private final Minecraft minecraft;
    private final Logger logger;

    private TargetKey currentTarget;
    /** Identity of the world the cached target was resolved from; not dereferenced. */
    private Object resolvedFrom;
    private TargetIdentity cachedIdentity;
    private float lastYaw;
    private float lastPitch;
    private boolean hasRotationReference;
    private boolean lastFocused = true;
    private ActivityState lastLoggedState;
    private long lastInputAt;
    private boolean disabled;

    public PlaytimeClientHandler(PlaytimeTracker tracker, Minecraft minecraft, Logger logger) {
        this.tracker = tracker;
        this.minecraft = minecraft;
        this.logger = logger;
        this.resolver = new TargetResolver(minecraft);
    }

    // --- the tick loop --------------------------------------------------------------

    @SubscribeEvent
    public synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (disabled || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            updateSessionLifecycle();
            if (!tracker.isSessionActive()) {
                return;
            }

            updateWindowFocus();
            detectMovementIntent();
            detectCameraRotation();
            tracker.tick();

            logStateTransitions();
            reportSaveFailure();
        } catch (Throwable t) {
            disable(t);
        }
    }

    /**
     * Records a sign of life from a discrete input or interaction event.
     * <p>
     * Receiving one is also proof the window has focus: the operating system only delivers
     * input to a focused window. That matters because {@code Display.isActive()} is not
     * reliable everywhere, and without this a session could stay AFK for its entire life
     * with no way back.
     */
    private synchronized void signalActivity() {
        if (disabled) {
            return;
        }
        try {
            lastInputAt = System.currentTimeMillis();
            if (!lastFocused) {
                tracker.onWindowFocusChanged(true);
                lastFocused = true;
            }
            tracker.onActivity();
        } catch (Throwable t) {
            disable(t);
        }
    }

    private void disable(Throwable failure) {
        disabled = true;
        logger.error("Actually Played hit an unexpected error and has stopped recording for "
                + "this session. Existing data is untouched. Please report this log.", failure);
    }

    /**
     * Reports switches between playing and AFK when debug logging is on.
     * <p>
     * Diagnostics only, and off by default: the mod gives the player no in-game feedback,
     * but a log line is the only practical way to check activity detection against a real
     * game, where no automated test can reach.
     */
    private void logStateTransitions() {
        if (!ForgeConfig.isDebugLogging()) {
            lastLoggedState = null;
            return;
        }
        Optional<SessionSnapshot> current = tracker.snapshot();
        if (!current.isPresent()) {
            return;
        }
        SessionSnapshot snapshot = current.get();
        if (snapshot.getState() == lastLoggedState) {
            return;
        }
        lastLoggedState = snapshot.getState();
        logger.info("[debug] {} -> played {} / afk {}",
                snapshot.getState() == ActivityState.AFK ? "AFK" : "PLAYING",
                DurationFormatter.format(snapshot.getActiveMillis()),
                DurationFormatter.format(snapshot.getAfkMillis()));
    }

    /**
     * Resolves where the player is, reusing the previous answer while the world is the same
     * object.
     * <p>
     * Resolving allocates a normalised address, a key and an identity. Doing that twenty
     * times a second for a value that only changes when the player switches world is pure
     * garbage; the world instance is replaced on every connection, which makes it a reliable
     * and free cache key.
     */
    private TargetIdentity resolveTarget() {
        Object world = minecraft.world;
        if (world == null) {
            resolvedFrom = null;
            cachedIdentity = null;
            return null;
        }
        if (world != resolvedFrom) {
            TargetIdentity identity = resolver.resolve();
            if (identity == null) {
                // Never cache a failure. The player entity or the server data may simply not
                // be set yet on this tick; pinning null here would silence the mod for the
                // whole session, with no error anywhere.
                return null;
            }
            resolvedFrom = world;
            cachedIdentity = identity;
        }
        return cachedIdentity;
    }

    /** Opens, closes or switches the session as the player moves between worlds. */
    private void updateSessionLifecycle() {
        TargetIdentity identity = resolveTarget();
        TargetKey target = identity == null ? null : identity.getKey();

        if (currentTarget != null && !currentTarget.equals(target)) {
            // Covers both leaving for the main menu and hopping straight to another
            // server without passing through it.
            endSession();
        }
        if (target != null && currentTarget == null) {
            tracker.beginSession(resolver.resolvePlayerId(), target, identity.getDisplayName());
            currentTarget = target;
            lastLoggedState = null;
            if (ForgeConfig.isDebugLogging()) {
                logger.info("[debug] session started on {} ({})", target, identity.getDisplayName());
            }
            hasRotationReference = false;
            lastFocused = isWindowFocused();
        }
    }

    private void endSession() {
        java.util.Optional<fr.julien.actuallyplayed.core.model.TrackedSession> finished = tracker.endSession();
        if (ForgeConfig.isDebugLogging()) {
            if (finished.isPresent()) {
                logger.info("[debug] session recorded on {} -> played {} / afk {}",
                        finished.get().getTarget(),
                        DurationFormatter.format(finished.get().getActiveMillis()),
                        DurationFormatter.format(finished.get().getAfkMillis()));
            } else {
                logger.info("[debug] session on {} discarded (below the minimum length)", currentTarget);
            }
        }
        currentTarget = null;
        hasRotationReference = false;
        reportSaveFailure();
    }

    // --- activity signals -------------------------------------------------------------

    /**
     * Reads the player's movement <em>intent</em> rather than their position.
     * <p>
     * Position changes constantly without the player doing anything — gravity, water
     * currents, minecarts, mounts, knockback, server repositioning. AFK farms are built on
     * exactly that passive drift. {@link MovementInput} is zero whenever the player is
     * being carried, so it distinguishes a human from a water canal with no threshold to
     * tune.
     */
    private void detectMovementIntent() {
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            return;
        }
        MovementInput input = player.movementInput;
        if (input == null) {
            return;
        }
        if (input.moveForward != 0.0f || input.moveStrafe != 0.0f || input.jump || input.sneak) {
            tracker.onActivity();
        }
    }

    private void detectCameraRotation() {
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            return;
        }
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;

        if (!hasRotationReference) {
            // First tick of a session: nothing to compare against yet. Recording the
            // reference without signalling activity avoids a spurious hit on join.
            lastYaw = yaw;
            lastPitch = pitch;
            hasRotationReference = true;
            return;
        }

        if (Math.abs(yaw - lastYaw) > ROTATION_EPSILON || Math.abs(pitch - lastPitch) > ROTATION_EPSILON) {
            tracker.onActivity();
        }
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private void updateWindowFocus() {
        boolean focused = isWindowFocused();
        if (focused != lastFocused) {
            tracker.onWindowFocusChanged(focused);
            lastFocused = focused;
        }
    }

    /**
     * Focus in the sense that matters here: the player is at the keyboard and the game is
     * running. Alt-tabbing loses it, and so does the singleplayer pause menu, where the
     * world is genuinely frozen.
     */
    private boolean isWindowFocused() {
        // The pause menu freezes the world, so it outranks any proof of focus.
        if (minecraft.isSingleplayer() && minecraft.currentScreen instanceof GuiIngameMenu) {
            return false;
        }
        if (Display.isActive()) {
            return true;
        }
        // Display.isActive() reports a false negative on several Linux window managers, in
        // borderless fullscreen, and when the window sits on another virtual desktop. Recent
        // input outranks it - see signalActivity.
        return System.currentTimeMillis() - lastInputAt < FOCUS_GRACE_MILLIS;
    }

    // --- discrete input and interaction events ------------------------------------------

    // Every handler below is a pure observer: it must see the event and never interfere.
    // Hence receiveCanceled - a controller mod remapping input, or an inventory mod eating a
    // scroll, cancels these routinely, and without the flag the player would be recorded as
    // AFK while actively playing.

    @SubscribeEvent(receiveCanceled = true)
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        signalActivity();
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        signalActivity();
    }

    /** Keyboard use inside a GUI - an inventory, a chest, a mod screen - is still playing. */
    @SubscribeEvent(receiveCanceled = true)
    public void onGuiKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        signalActivity();
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onGuiMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        signalActivity();
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onChat(ClientChatEvent event) {
        signalActivity();
    }

    /**
     * Interactions count as activity.
     * <p>
     * This event fires on both sides, and on an integrated server the server-side call
     * arrives on the server thread. The side check is explicit and first so the intent is
     * visible; the identity check then keeps only the client's own player.
     */
    @SubscribeEvent(receiveCanceled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getSide().isClient()) {
            return;
        }
        if (event.getEntityPlayer() == minecraft.player) {
            signalActivity();
        }
    }

    // --- shutdown ----------------------------------------------------------------------

    /** Closes the session and flushes to disk. Called when the game exits. */
    public synchronized void shutdown() {
        try {
            if (tracker.isSessionActive()) {
                endSession();
            } else {
                tracker.saveNow();
            }
        } catch (IOException e) {
            logger.error("Could not save playtime data while shutting down", e);
        } catch (RuntimeException e) {
            logger.error("Unexpected failure while shutting down playtime tracking", e);
        }
    }

    private void reportSaveFailure() {
        IOException failure = tracker.consumeSaveFailure();
        if (failure != null) {
            logger.error("Could not write the playtime data file; tracking continues in memory", failure);
        }
    }
}

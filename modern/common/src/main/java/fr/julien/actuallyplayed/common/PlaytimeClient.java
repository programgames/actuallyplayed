package fr.julien.actuallyplayed.common;

import fr.julien.actuallyplayed.common.bridge.InventorySlot;
import fr.julien.actuallyplayed.common.bridge.PlayerAccount;
import fr.julien.actuallyplayed.common.bridge.PlayerRotation;
import fr.julien.actuallyplayed.common.bridge.TargetIdentity;
import fr.julien.actuallyplayed.common.bridge.TargetResolver;
import fr.julien.actuallyplayed.core.PlaytimeTracker;
import fr.julien.actuallyplayed.core.engine.ActivityState;
import fr.julien.actuallyplayed.core.engine.SessionSnapshot;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.model.TrackedSession;
import fr.julien.actuallyplayed.core.util.DurationFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Optional;

/**
 * Turns what the Minecraft client is doing into the tracker's vocabulary.
 *
 * <h3>One event, not eight</h3>
 * Everything here is driven by {@link #onClientTick()}. The 1.12 adapter also subscribes to
 * seven discrete Forge events, and half of them have no Fabric equivalent — they would need
 * Mixins, which is the most expensive and most fragile part of any port. The client tick is
 * the one event that exists identically on every loader and every version, and every activity
 * signal is readable as state on it. See {@code PORTING.md} §4.1.
 *
 * <h3>Failure isolation</h3>
 * The tick catches {@link Throwable} and, on the first failure, stops the mod for the rest of
 * the session. This mod runs alongside hundreds of others; an exception escaping here would
 * surface as a crash report naming Actually Played. A failure must cost the mod its function,
 * never the player their session.
 *
 * <h3>Threading</h3>
 * Every method that touches state is synchronised. Almost every call arrives on the client
 * thread, but {@link #shutdown()} runs on its own thread while the game loop may still be
 * ticking, and both touch the same session.
 */
public final class PlaytimeClient {

    /**
     * Camera movement below this many degrees per tick is treated as noise. A controller's
     * dead zone or a barely-nudged mouse should not keep the counter alive on its own.
     */
    private static final float ROTATION_EPSILON = 0.01f;

    /**
     * How long a real input keeps proving the window has focus after Minecraft claims
     * otherwise. Covers a false negative without letting a genuine alt-tab linger.
     */
    private static final long FOCUS_GRACE_MILLIS = 3000L;

    /**
     * How long the window must stay unfocused before the loss is believed.
     * <p>
     * Other applications steal focus for a fraction of a second and hand it straight back:
     * launchers, chat overlays, notification popups. Measured on 2026-08-30 against the 1.12
     * build, one such application split the session every time it blinked. See
     * {@code CLAUDE.md} §2.3.
     */
    private static final long FOCUS_LOSS_DEBOUNCE_MILLIS = 1500L;

    private final PlaytimeTracker tracker;
    private final Minecraft minecraft;
    private final TargetResolver resolver;
    private final Logger logger;
    private final boolean debugLogging;

    private TargetKey currentTarget;
    /** Identity of the level the cached target was resolved from; not dereferenced. */
    private Object resolvedFrom;
    private TargetIdentity cachedIdentity;
    private float lastYaw;
    private float lastPitch;
    private boolean hasRotationReference;
    private long lastInputFingerprint;
    private boolean hasInputReference;
    private boolean lastFocused = true;
    /** When the window was first seen unfocused, or 0 while it holds focus. */
    private long unfocusedSince;
    private ActivityState lastLoggedState;
    private long lastInputAt;
    private boolean disabled;

    public PlaytimeClient(PlaytimeTracker tracker, Minecraft minecraft, Logger logger, boolean debugLogging) {
        this.tracker = tracker;
        this.minecraft = minecraft;
        this.logger = logger;
        this.debugLogging = debugLogging;
        this.resolver = new TargetResolver(minecraft);
    }

    // --- the tick loop ------------------------------------------------------------------

    public synchronized void onClientTick() {
        if (disabled) {
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
            detectRawInput();
            tracker.tick();

            logStateTransitions();
            reportSaveFailure();
        } catch (Throwable t) {
            disable(t);
        }
    }

    private void disable(Throwable failure) {
        disabled = true;
        logger.error("Actually Played hit an unexpected error and has stopped recording for "
                + "this session. Existing data is untouched. Please report this log.", failure);
    }

    // --- session lifecycle --------------------------------------------------------------

    /**
     * Resolves where the player is, reusing the previous answer while the level is the same
     * object.
     * <p>
     * Resolving allocates a normalised address, a key and an identity. Doing that twenty times
     * a second for a value that only changes when the player switches world is pure garbage;
     * the level instance is replaced on every connection, which makes it a free cache key.
     */
    private TargetIdentity resolveTarget() {
        Object level = minecraft.level;
        if (level == null) {
            resolvedFrom = null;
            cachedIdentity = null;
            return null;
        }
        if (level != resolvedFrom) {
            TargetIdentity identity = resolver.resolve();
            if (identity == null) {
                // Never cache a failure. The player or the server data may simply not be set
                // yet on this tick; pinning null here would silence the mod for the whole
                // session, with no error anywhere.
                return null;
            }
            resolvedFrom = level;
            cachedIdentity = identity;
        }
        return cachedIdentity;
    }

    private void updateSessionLifecycle() {
        TargetIdentity identity = resolveTarget();
        TargetKey target = identity == null ? null : identity.getKey();

        if (currentTarget != null && !currentTarget.equals(target)) {
            // Covers both leaving for the main menu and hopping straight to another server
            // without passing through it.
            endSession();
        }
        if (target != null && currentTarget == null) {
            tracker.beginSession(resolver.resolvePlayerId(), target, identity.getDisplayName());
            currentTarget = target;
            lastLoggedState = null;
            hasRotationReference = false;
            hasInputReference = false;
            unfocusedSince = 0L;
            lastFocused = isWindowFocused();
            if (debugLogging) {
                logger.info("[debug] session started on {} ({})", target, identity.getDisplayName());
            }
        }
    }

    private void endSession() {
        Optional<TrackedSession> finished = tracker.endSession();
        if (debugLogging) {
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
        hasInputReference = false;
        reportSaveFailure();
    }

    // --- activity signals ----------------------------------------------------------------

    /**
     * Reads the player's movement <em>intent</em> rather than their position.
     * <p>
     * Position changes constantly without the player doing anything — gravity, water currents,
     * minecarts, mounts, knockback, server repositioning. AFK farms are built on exactly that
     * passive drift. {@link Input} is zero whenever the player is being carried, so it tells a
     * human from a water canal with no threshold to tune.
     */
    private void detectMovementIntent() {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        Input input = player.input;
        if (input == null) {
            return;
        }
        if (input.forwardImpulse != 0.0f || input.leftImpulse != 0.0f
                || input.jumping || input.shiftKeyDown) {
            tracker.onActivity();
        }
    }

    private void detectCameraRotation() {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        float yaw = PlayerRotation.yaw(player);
        float pitch = PlayerRotation.pitch(player);

        if (!hasRotationReference) {
            // First tick of a session: nothing to compare against yet. Recording the reference
            // without signalling avoids a spurious hit on join.
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

    /**
     * Polls the input devices and signals activity whenever their state <em>changes</em>.
     *
     * <h3>Why a change and not a held state</h3>
     * A key still down when the window loses focus stays down in the window system's state for
     * as long as it stays unfocused; treating "held" as activity would strand the session as
     * permanently active. A change only happens when the OS delivers an input event to a
     * focused window, so it is also proof of focus. Keys genuinely held while playing are
     * covered elsewhere — movement by {@link #detectMovementIntent()}, mining by the swing
     * flag below.
     */
    private void detectRawInput() {
        long fingerprint = readInputFingerprint();
        if (!hasInputReference) {
            lastInputFingerprint = fingerprint;
            hasInputReference = true;
            return;
        }
        if (fingerprint != lastInputFingerprint) {
            lastInputFingerprint = fingerprint;
            signalActivity();
        }
    }

    /**
     * Condenses everything the player can be doing with their hands into one comparable
     * number. Only equality matters, so any collision-resistant mixing will do.
     */
    private long readInputFingerprint() {
        long fingerprint = 1L;

        // Every key binding rather than a raw sweep of the keyboard. GLFW has no "is any key
        // down" query, and one native call per key code every tick is a poor trade for the
        // handful of unbound keys it would add - typing in a screen is already caught by the
        // cursor moving to it and by the buttons below.
        for (net.minecraft.client.KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.isDown()) {
                fingerprint = fingerprint * 31L + mapping.getName().hashCode();
            }
        }

        net.minecraft.client.MouseHandler mouse = minecraft.mouseHandler;
        if (mouse != null) {
            // Left and right only: 1.16 has no isMiddlePressed, and the middle button's usual
            // effect - picking a block, or the wheel - already shows in the selected slot.
            if (mouse.isLeftPressed()) {
                fingerprint = fingerprint * 31L + 1001L;
            }
            if (mouse.isRightPressed()) {
                fingerprint = fingerprint * 31L + 1003L;
            }
            if (minecraft.screen != null) {
                // Only meaningful while a screen is open, where the cursor is released and its
                // position is what the player is actually moving. In play the cursor is
                // grabbed and re-centred every frame, so these coordinates would either sit
                // still or jitter forever - and a permanent jitter would mean the mod never
                // detects AFK at all. Mouse movement in play is caught as camera rotation.
                fingerprint = fingerprint * 31L + Double.hashCode(mouse.xpos());
                fingerprint = fingerprint * 31L + Double.hashCode(mouse.ypos());
            }
        }

        LocalPlayer player = minecraft.player;
        if (player != null) {
            // The wheel leaves no state of its own, but its effect does.
            fingerprint = fingerprint * 31L + InventorySlot.selected(player);
            // Covers mining, attacking and using an item: the swing outlives the click that
            // started it by several ticks, and repeats for as long as the action does.
            fingerprint = fingerprint * 31L + (player.swinging ? 1L : 0L);
        }

        return fingerprint;
    }

    /**
     * Records a sign of life. Also proof the window has focus: the operating system only
     * delivers input to a focused window.
     */
    private void signalActivity() {
        try {
            lastInputAt = System.currentTimeMillis();
            // Input proves where the player's attention is, but it cannot un-pause the game.
            // Restoring focus here while the game is paused made two rules contradict each
            // other and the state flapped at tick rate - see CLAUDE.md §8.
            if (!lastFocused && !minecraft.isPaused()) {
                tracker.onWindowFocusChanged(true);
                lastFocused = true;
            }
            tracker.onActivity();
        } catch (Throwable t) {
            disable(t);
        }
    }

    // --- focus ------------------------------------------------------------------------------

    /**
     * Publishes focus changes, holding a <em>loss</em> for
     * {@link #FOCUS_LOSS_DEBOUNCE_MILLIS} before believing it.
     * <p>
     * The pause menu is exempt and takes effect at once: opening it is deliberate and the world
     * is genuinely frozen, so waiting would charge frozen time as played. Inventing playtime is
     * the one failure this project treats as unforgivable, and a menu cannot produce the
     * sub-second transient the debounce exists for.
     */
    private void updateWindowFocus() {
        if (minecraft.isPaused()) {
            unfocusedSince = 0L;
            setFocused(false);
            return;
        }

        if (minecraft.isWindowActive() || hasRecentInput()) {
            unfocusedSince = 0L;
            setFocused(true);
            return;
        }

        long now = System.currentTimeMillis();
        if (unfocusedSince == 0L) {
            unfocusedSince = now;
            return;
        }
        if (now - unfocusedSince >= FOCUS_LOSS_DEBOUNCE_MILLIS) {
            setFocused(false);
        }
    }

    private void setFocused(boolean focused) {
        if (focused != lastFocused) {
            tracker.onWindowFocusChanged(focused);
            lastFocused = focused;
        }
    }

    private boolean hasRecentInput() {
        return System.currentTimeMillis() - lastInputAt < FOCUS_GRACE_MILLIS;
    }

    private boolean isWindowFocused() {
        return !minecraft.isPaused() && (minecraft.isWindowActive() || hasRecentInput());
    }

    // --- diagnostics --------------------------------------------------------------------------

    /**
     * Reports switches between playing and AFK when debug logging is on.
     * <p>
     * Diagnostics only, and off by default: the mod gives the player no in-game feedback, but a
     * log line is the only practical way to check activity detection against a real game, where
     * no automated test can reach.
     */
    private void logStateTransitions() {
        if (!debugLogging) {
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

    private void reportSaveFailure() {
        IOException failure = tracker.consumeSaveFailure();
        if (failure != null) {
            logger.error("Could not write the playtime data file; tracking continues in memory", failure);
        }
    }

    // --- shutdown -------------------------------------------------------------------------------

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
        } catch (Throwable t) {
            // Throwable, not RuntimeException. This runs on a shutdown hook, where the class
            // loader may already refuse to load anything new: a NoClassDefFoundError here is
            // ordinary, and letting it escape turns a failed save into an unexplained
            // "Exception in thread" with no stack trace, which is what 1.7.10 showed.
            logger.error("Unexpected failure while shutting down playtime tracking", t);
        }
    }

    public PlaytimeTracker getTracker() {
        return tracker;
    }
}

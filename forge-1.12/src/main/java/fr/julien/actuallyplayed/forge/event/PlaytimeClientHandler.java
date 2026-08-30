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
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
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

    /**
     * How many mouse buttons the input fingerprint looks at. Gaming mice report a dozen or
     * more, and the extra ones are almost always bound to a key the scan already covers.
     */
    private static final int TRACKED_MOUSE_BUTTONS = 8;

    /**
     * How long the window must stay unfocused before the loss is believed.
     * <p>
     * Other applications steal focus for a fraction of a second and give it straight back:
     * game launchers, chat overlays, notification popups, updaters. Measured during the
     * 2026-08-30 test session, the League of Legends client did exactly this, and each steal
     * split the session in two. A player at their keyboard the whole time should not be
     * charged AFK for it.
     * <p>
     * A real alt-tab lasts far longer than this, so it still registers almost at once, and
     * the retroactive rollback takes back the idle tail either way.
     */
    private static final long FOCUS_LOSS_DEBOUNCE_MILLIS = 1500L;

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
    private long lastInputFingerprint;
    private boolean hasInputReference;
    private boolean lastFocused = true;
    /** When the window was first seen unfocused, or 0 while it holds focus. */
    private long unfocusedSince;
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
            detectRawInput();
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
            // Input proves the window has focus, but it cannot un-pause the game. Restoring
            // focus here while the pause menu is open made the two rules contradict each
            // other: the tick set AFK because the world was frozen, this line set PLAYING
            // because the cursor had moved, and the state flapped at tick rate for as long as
            // the menu stayed up. Seen in game as six transitions inside one second.
            if (!lastFocused && !isGamePaused()) {
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
            hasInputReference = false;
            unfocusedSince = 0L;
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
        hasInputReference = false;
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

    /**
     * Polls the raw input devices and signals activity whenever their state <em>changes</em>.
     * <p>
     * This duplicates, on purpose, what the event handlers at the bottom of this class
     * already report. It exists for three reasons:
     * <ul>
     *   <li>A state cannot be cancelled by another mod. The {@code receiveCanceled} trap —
     *       controller and inventory-tweak mods eating input events, and the player being
     *       recorded AFK while playing — has no equivalent here.</li>
     *   <li>{@code InputEvent.KeyInputEvent} does not fire while a screen is open, which is
     *       why {@code GuiScreenEvent.KeyboardInputEvent} had to be subscribed beside it.
     *       Polled state has no such asymmetry.</li>
     *   <li>The client tick is the one event that exists identically on every loader and
     *       every Minecraft version. A port only has to carry this method, not seven event
     *       subscriptions with no Fabric equivalent. See {@code PORTING.md} §4.1.</li>
     * </ul>
     *
     * <h3>Why a change and not a held state</h3>
     * Signalling on "any key is down" would be wrong twice over. A key held at the moment of
     * an alt-tab stays down in LWJGL's buffers for as long as the window is unfocused, which
     * would strand the session as permanently active — the exact opposite of what §2.3 asks
     * for. And a key genuinely held while playing is already covered: movement by
     * {@link #detectMovementIntent()}, mining by the swing flag below.
     * <p>
     * A change, conversely, is real proof of focus: the state only moves when the window
     * receives an input event from the OS, so this may safely go through
     * {@link #signalActivity()}.
     */
    private void detectRawInput() {
        long fingerprint = readInputFingerprint();
        if (!hasInputReference) {
            // First tick of a session: nothing to compare against. Recording the reference
            // without signalling avoids a spurious hit on join, exactly as for rotation.
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

        if (Keyboard.isCreated()) {
            // A full sweep rather than the bound keys only: typing in chat or in a mod
            // screen uses keys no key binding claims. 256 buffer reads per tick is noise
            // next to a single frame.
            for (int key = 0; key < Keyboard.KEYBOARD_SIZE; key++) {
                if (Keyboard.isKeyDown(key)) {
                    fingerprint = fingerprint * 31L + key;
                }
            }
        }

        if (Mouse.isCreated()) {
            int buttons = Math.min(Mouse.getButtonCount(), TRACKED_MOUSE_BUTTONS);
            for (int button = 0; button < buttons; button++) {
                if (Mouse.isButtonDown(button)) {
                    fingerprint = fingerprint * 31L + 1000L + button;
                }
            }
            if (minecraft.currentScreen != null) {
                // Only meaningful while a screen is open, where the cursor is released and
                // its position is what the player is actually moving. In play the cursor is
                // grabbed and re-centred every frame, so these coordinates would either sit
                // still or jitter forever — and a permanent jitter would mean the mod never
                // detects AFK at all. Mouse movement in play is caught as camera rotation.
                fingerprint = fingerprint * 31L + Mouse.getX();
                fingerprint = fingerprint * 31L + Mouse.getY();
            }
        }

        EntityPlayerSP player = minecraft.player;
        if (player != null) {
            // The wheel leaves no state of its own, but its effect does.
            fingerprint = fingerprint * 31L + player.inventory.currentItem;
            // Covers mining, attacking and using an item: the arm swing outlives the click
            // that started it by several ticks, and repeats for as long as the action does.
            fingerprint = fingerprint * 31L + (player.isSwingInProgress ? 1L : 0L);
        }

        return fingerprint;
    }

    /**
     * Publishes focus changes, holding a loss for {@link #FOCUS_LOSS_DEBOUNCE_MILLIS} before
     * believing it.
     *
     * <h3>Why the pause menu is exempt</h3>
     * It takes effect immediately, with no debounce. Opening it is a deliberate act and the
     * world is genuinely frozen; waiting a second and a half would charge frozen time as
     * played. Inventing playtime is the one failure this project treats as unforgivable
     * (§2.3), and it is not worth trading for a transient the pause menu cannot produce.
     *
     * <h3>What the debounce costs</h3>
     * On a genuine alt-tab by a player who was active right up to it, the rollback has almost
     * nothing to take back, so up to 1.5 s of the debounce window is counted as played. That
     * is the bounded price of not fragmenting a session every time another application blinks
     * the focus.
     */
    private void updateWindowFocus() {
        if (isGamePaused()) {
            unfocusedSince = 0L;
            setFocused(false);
            return;
        }

        // Input outranks Display.isActive(), which reports a false negative on several Linux
        // window managers, in borderless fullscreen, and across virtual desktops.
        if (Display.isActive() || hasRecentInput()) {
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

    /**
     * Focus in the sense that matters here: the player is at the keyboard and the game is
     * running. Alt-tabbing loses it, and so does the singleplayer pause menu, where the
     * world is genuinely frozen.
     */
    /**
     * Whether the singleplayer pause menu has the world frozen.
     * <p>
     * Kept apart from focus because it outranks every proof of focus: the player may well be
     * at their keyboard, clicking around the menu, but no time is passing in the world. Input
     * proves where the player's attention is; it cannot un-pause the game.
     */
    private boolean isGamePaused() {
        return minecraft.isSingleplayer() && minecraft.currentScreen instanceof GuiIngameMenu;
    }

    private boolean isWindowFocused() {
        if (isGamePaused()) {
            return false;
        }
        // Display.isActive() reports a false negative on several Linux window managers, in
        // borderless fullscreen, and when the window sits on another virtual desktop. Recent
        // input outranks it - see signalActivity.
        return Display.isActive() || hasRecentInput();
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

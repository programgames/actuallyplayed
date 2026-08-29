package fr.julien.playtimetracker.forge.event;

import fr.julien.playtimetracker.core.PlaytimeTracker;
import fr.julien.playtimetracker.core.engine.ActivityState;
import fr.julien.playtimetracker.core.engine.SessionSnapshot;
import fr.julien.playtimetracker.core.model.TargetKey;
import fr.julien.playtimetracker.core.util.DurationFormatter;
import fr.julien.playtimetracker.forge.config.ForgeConfig;
import fr.julien.playtimetracker.forge.bridge.TargetIdentity;
import fr.julien.playtimetracker.forge.bridge.TargetResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
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

    public PlaytimeClientHandler(PlaytimeTracker tracker, Minecraft minecraft, Logger logger) {
        this.tracker = tracker;
        this.minecraft = minecraft;
        this.logger = logger;
        this.resolver = new TargetResolver(minecraft);
    }

    // --- the tick loop --------------------------------------------------------------

    @SubscribeEvent
    public synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

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
            resolvedFrom = world;
            cachedIdentity = resolver.resolve();
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
        java.util.Optional<fr.julien.playtimetracker.core.model.TrackedSession> finished = tracker.endSession();
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
        if (!Display.isActive()) {
            return false;
        }
        return !(minecraft.isSingleplayer() && minecraft.currentScreen instanceof GuiIngameMenu);
    }

    // --- discrete input and interaction events ------------------------------------------

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        tracker.onActivity();
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        tracker.onActivity();
    }

    /** Keyboard use inside a GUI — an inventory, a chest, a mod screen — is still playing. */
    @SubscribeEvent
    public void onGuiKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        tracker.onActivity();
    }

    @SubscribeEvent
    public void onGuiMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        tracker.onActivity();
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        tracker.onActivity();
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        tracker.onActivity();
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.getEntityPlayer() == minecraft.player) {
            tracker.onActivity();
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

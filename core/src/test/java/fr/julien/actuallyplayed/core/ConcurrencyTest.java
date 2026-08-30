package fr.julien.actuallyplayed.core;

import fr.julien.actuallyplayed.core.config.PlaytimeConfig;
import fr.julien.actuallyplayed.core.engine.SystemClock;
import fr.julien.actuallyplayed.core.model.PlaytimeData;
import fr.julien.actuallyplayed.core.model.TargetKey;
import fr.julien.actuallyplayed.core.storage.PlaytimeRepository;
import org.junit.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the two threads that really exist at runtime.
 * <p>
 * Almost every call into {@link PlaytimeTracker} arrives on Minecraft's client thread, but
 * the shutdown hook that flushes the session on exit runs on its own while the game loop may
 * still be ticking. The locking that makes that safe was reasoned about rather than
 * measured; these tests turn "trust the synchronized keyword" into something observed.
 * <p>
 * A green run does not prove the absence of a race — no test can. It does prove that the
 * obvious interleavings neither throw nor corrupt state, and it would catch a future change
 * that dropped the locking altogether.
 */
public class ConcurrencyTest {

    private static final String PLAYER = "0f9c3a10-0000-0000-0000-000000000001";
    private static final TargetKey SERVER = TargetKey.server("mc.hypixel.net:25565");

    /** Walks the data on every save, the way the real JSON codec does. */
    private static final class WalkingRepository implements PlaytimeRepository {

        volatile Throwable failure;

        @Override
        public PlaytimeData load() {
            return new PlaytimeData();
        }

        @Override
        public void save(PlaytimeData data) throws IOException {
            try {
                long total = 0L;
                for (fr.julien.actuallyplayed.core.model.PlayerPlaytime player : data.getPlayers()) {
                    for (fr.julien.actuallyplayed.core.model.TrackedTarget target : player.getTargets()) {
                        total += target.getTotalMillis();
                        for (fr.julien.actuallyplayed.core.model.TrackedSession session : target.getSessions()) {
                            total += session.getTotalMillis();
                        }
                    }
                }
                if (total < 0L) {
                    throw new IllegalStateException("negative total: " + total);
                }
            } catch (Throwable t) {
                failure = t;
                throw new IOException(t);
            }
        }
    }

    @Test
    public void aFlushingShutdownHookDoesNotCorruptATickingGameLoop() throws Exception {
        WalkingRepository repository = new WalkingRepository();
        final PlaytimeTracker tracker = new PlaytimeTracker(
                repository,
                PlaytimeConfig.builder().autosaveIntervalMillis(10L).build(),
                SystemClock.INSTANCE,
                ZoneId.systemDefault());
        tracker.start();
        tracker.beginSession(PLAYER, SERVER, "Hypixel");

        final AtomicReference<Throwable> gameLoopFailure = new AtomicReference<Throwable>();
        final CountDownLatch start = new CountDownLatch(1);

        Thread gameLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < 20_000; i++) {
                        tracker.onActivity();
                        tracker.tick();
                        tracker.snapshot();
                        if ((i & 255) == 0) {
                            tracker.onWindowFocusChanged((i & 512) == 0);
                        }
                    }
                } catch (Throwable t) {
                    gameLoopFailure.set(t);
                }
            }
        }, "game-loop");

        final AtomicReference<Throwable> hookFailure = new AtomicReference<Throwable>();
        Thread shutdownHook = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        tracker.saveNow();
                        tracker.consumeSaveFailure();
                    }
                } catch (Throwable t) {
                    hookFailure.set(t);
                }
            }
        }, "shutdown-hook");

        gameLoop.start();
        shutdownHook.start();
        start.countDown();
        gameLoop.join(30_000L);
        shutdownHook.join(30_000L);

        assertNull("the game loop must not be broken by a concurrent flush", gameLoopFailure.get());
        assertNull("the flush must not be broken by a ticking game loop", hookFailure.get());
        assertNull("the data must never be walked while it is being mutated", repository.failure);
        assertTrue("the session must have survived", tracker.isSessionActive());
    }

    @Test
    public void aConfigSwapIsSeenByTheThreadDoingTheAccounting() throws Exception {
        final PlaytimeTracker tracker = new PlaytimeTracker(
                new WalkingRepository(), PlaytimeConfig.defaults(),
                SystemClock.INSTANCE, ZoneId.systemDefault());
        tracker.start();
        tracker.beginSession(PLAYER, SERVER, "Hypixel");

        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread ticker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 50_000; i++) {
                        tracker.tick();
                    }
                } catch (Throwable t) {
                    failure.set(t);
                }
            }
        }, "ticker");

        ticker.start();
        for (int i = 0; i < 200; i++) {
            // The engine reads the configuration through a supplier on every use, so a swap
            // must be visible immediately and can never be observed half-applied.
            tracker.setConfig(PlaytimeConfig.builder()
                    .afkThresholdMillis(1000L + i)
                    .minSessionMillis(i)
                    .build());
        }
        ticker.join(30_000L);

        assertNull(failure.get());
    }
}

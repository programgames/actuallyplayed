package fr.julien.actuallyplayed.core.util;

import java.lang.reflect.Method;
import java.net.URI;

/**
 * Hands a URL to the system browser, on the Minecraft versions whose own helper is out of reach.
 *
 * <h3>Why this exists at all</h3>
 * Minecraft opens links itself, in {@code GuiScreen.openWebLink(URI)} -- and that method is
 * <strong>private</strong> on 1.12.2, while 1.7.10 has no equivalent: its
 * {@code GuiScreen.confirmClicked} is an empty method and the opening lives in the screens that
 * need it. Neither version leaves anything callable, so the six lines below are vanilla's own
 * approach, copied deliberately rather than reached for.
 *
 * <p>1.16 and later are not concerned: they expose {@code Util.getPlatform().openUri}, which is
 * public, better on Linux than AWT, and what those adapters use.
 *
 * <h3>Why the reflection</h3>
 * Naming {@code java.awt.Desktop} in an import loads AWT as soon as this class is verified.
 * Vanilla avoids that, and so does this: a game that never opens a link never touches the
 * toolkit. The failure modes are real -- a headless JVM, and macOS, where touching AWT from the
 * wrong thread has historically hung the client.
 *
 * <p>This is pure Java. It names no Minecraft type and belongs in {@code core} on that basis.
 */
public final class BrowserLauncher {

    /**
     * Opens {@code url} in the system browser.
     *
     * @return whether the browser was reached. It fails on a headless JVM, on Linux desktops with
     *         no {@code xdg-open}, and inside some sandboxes -- which is exactly why the caller
     *         must have offered "copy the link" first. Callers log the failure; this class
     *         deliberately holds no logger, because {@code core} holds none.
     */
    public static boolean open(String url) {
        try {
            URI uri = URI.create(url);
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Method getDesktop = desktop.getMethod("getDesktop");
            Method browse = desktop.getMethod("browse", URI.class);
            browse.invoke(getDesktop.invoke(null), uri);
            return true;
        } catch (Throwable failure) {
            // Throwable, not Exception: a missing AWT raises NoClassDefFoundError, and a
            // statistics screen must not become a crash report because a browser is absent.
            return false;
        }
    }

    private BrowserLauncher() {
    }
}

package fr.julien.actuallyplayed.core;

/**
 * The addresses the mod sends a player to.
 *
 * <p>Here rather than in each adapter because five Minecraft versions offer the same button, and
 * an address duplicated five times is an address free to drift: a repository that moves would be
 * fixed in four places out of five, and the fifth would keep sending players to a dead page with
 * nothing to report the failure.
 *
 * <p>A bare string is not a Minecraft dependency, so this belongs in {@code core} without
 * troubling the rule that keeps the module free of {@code net.minecraft} imports.
 */
public final class ProjectLinks {

    /**
     * Where a player reports a bug or asks for something.
     *
     * <p>The issue tracker rather than a contact address on purpose: a report filed here is
     * visible to the next player hitting the same problem, and answering it once answers it for
     * everyone.
     */
    public static final String ISSUES = "https://github.com/programgames/actuallyplayed/issues";

    private ProjectLinks() {
    }
}

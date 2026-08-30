package fr.julien.actuallyplayed.forge;

/**
 * Mod-wide constants. The version placeholder is substituted at build time by
 * ForgeGradle, so the version lives in gradle.properties only.
 */
public final class Reference {

    public static final String MOD_ID = "actuallyplayed";
    public static final String MOD_NAME = "Actually Played";
    public static final String VERSION = "@MOD_VERSION@";
    /**
     * Restricted to the exact version the mod is compiled and tested against. Declaring
     * 1.12 and 1.12.1 too would let it load where a differing method signature could crash
     * the game.
     */
    public static final String ACCEPTED_MC_VERSIONS = "[1.12.2]";

    /**
     * Where Forge looks for the version promotions that drive the "update available" marker
     * in the mod list.
     * <p>
     * The {@code updateUrl} field of {@code mcmod.info} looks like it does this and does
     * not: FML only ever reads the {@code updateJSON} parameter of {@link net.minecraftforge.fml.common.Mod}.
     * <p>
     * The versions in that file must be the exact strings this mod reports — {@code 1.12.2-1.0.0},
     * prefix included. Forge compares them with Maven's {@code ComparableVersion}, so a bare
     * {@code 1.0.0} in the file would sort below any {@code 1.12.2-x} and the checker would
     * report "up to date" forever.
     */
    public static final String UPDATE_JSON =
            "https://raw.githubusercontent.com/programgames/actuallyplayed/main/update.json";

    private Reference() {
    }
}

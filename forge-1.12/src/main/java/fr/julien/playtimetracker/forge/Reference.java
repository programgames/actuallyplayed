package fr.julien.playtimetracker.forge;

/**
 * Mod-wide constants. The version placeholder is substituted at build time by
 * ForgeGradle, so the version lives in gradle.properties only.
 */
public final class Reference {

    public static final String MOD_ID = "playtimetracker";
    public static final String MOD_NAME = "Playtime Tracker";
    public static final String VERSION = "@MOD_VERSION@";
    /**
     * Restricted to the exact version the mod is compiled and tested against. Declaring
     * 1.12 and 1.12.1 too would let it load where a differing method signature could crash
     * the game.
     */
    public static final String ACCEPTED_MC_VERSIONS = "[1.12.2]";

    private Reference() {
    }
}

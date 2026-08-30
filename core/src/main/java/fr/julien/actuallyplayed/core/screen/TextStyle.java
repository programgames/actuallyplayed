package fr.julien.actuallyplayed.core.screen;

/**
 * The palette the statistics screen draws from, named by role rather than by value.
 * <p>
 * Deliberately not a colour: {@code core} must not know how a given Minecraft version spells
 * one. 1.12 has {@code TextFormatting}, later versions have {@code ChatFormatting}, and the
 * numeric codes behind them have moved. Each platform maps this enum to whatever it has, and
 * the mapping is the only thing a port has to rewrite.
 */
public enum TextStyle {

    /** Values the eye should land on: durations, counts, dates. */
    WHITE,

    /** Labels sitting beside a value. */
    GRAY,

    /** Secondary information — the address under a server's name. */
    DARK_GRAY,

    /** The destination's name, and the AFK state. */
    YELLOW,

    /** Playing, and a healthy played ratio. */
    GREEN,

    /** A ratio low enough to be worth noticing. */
    RED
}

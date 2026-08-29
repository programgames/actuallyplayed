package fr.julien.playtimetracker.core.engine;

/**
 * Which bucket the engine is currently charging elapsed time to.
 */
public enum ActivityState {

    /** The player is playing; elapsed time accrues to the active counter. */
    ACTIVE,

    /** The counter is paused; elapsed time accrues to the AFK counter instead. */
    AFK
}

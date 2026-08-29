package fr.julien.playtimetracker.core.model;

/**
 * Kind of place playtime is attributed to.
 */
public enum TargetType {

    /** A multiplayer server, identified by its {@code host:port}. */
    SERVER,

    /** A singleplayer world, identified by its save folder name. */
    SINGLEPLAYER
}

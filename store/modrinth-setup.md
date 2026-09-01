# Creating the Modrinth project

Every field of the creation form, filled in. Written down because the two that decide
whether the mod is ever found -- the environment flags and the Fabric API dependency --
are both easy to leave at their default and neither fails loudly.

## Project settings

| Field | Value |
| --- | --- |
| Name | `Actually Played — Playtime & AFK Tracker` |
| Slug / URL | `actually-played` |
| Summary | the contents of `summary.txt` |
| Description | the contents of `modrinth-description.md` |
| Project type | Mod |
| Categories | Utility |
| Client side | **Required** |
| Server side | **Unsupported** |
| Licence | MIT |
| Source code | `https://github.com/programgames/actuallyplayed` |
| Issue tracker | `https://github.com/programgames/actuallyplayed/issues` |

The two environment flags are the point. A player filtering for client-side mods is a
player who wants exactly this and cannot use anything the competition publishes, since
those are server plugins. Setting the flags wrong drops the mod out of the one search
that converts.

## Versions

Eight jars, one Modrinth version each. Release channel `Release` for all of them --
CurseForge only syncs a project to its launcher once a file is marked as a release, and
the same habit reads as finished work here.

| Version number | Title | Loaders | Game version | File |
| --- | --- | --- | --- | --- |
| `1.7.10-1.1.0` | 1.1.0 for 1.7.10 (Forge) | Forge | 1.7.10 | `actuallyplayed-1.7.10-1.1.0.jar` |
| `1.12.2-1.1.0` | 1.1.0 for 1.12.2 (Forge) | Forge | 1.12.2 | `actuallyplayed-1.12.2-1.1.0.jar` |
| `1.16.5-1.1.0+forge` | 1.1.0 for 1.16.5 (Forge) | Forge | 1.16.5 | `actuallyplayed-forge-1.16.5-1.1.0.jar` |
| `1.16.5-1.1.0+fabric` | 1.1.0 for 1.16.5 (Fabric) | Fabric | 1.16.5 | `actuallyplayed-fabric-1.16.5-1.1.0.jar` |
| `1.20.1-1.1.0+forge` | 1.1.0 for 1.20.1 (Forge) | Forge | 1.20.1 | `actuallyplayed-forge-1.20.1-1.1.0.jar` |
| `1.20.1-1.1.0+fabric` | 1.1.0 for 1.20.1 (Fabric) | Fabric | 1.20.1 | `actuallyplayed-fabric-1.20.1-1.1.0.jar` |
| `1.21.1-1.1.0` | 1.1.0 for 1.21.1 (NeoForge) | NeoForge | 1.21.1 | `actuallyplayed-neoforge-1.21.1-1.1.0.jar` |
| `1.21.1-1.1.0+fabric` | 1.1.0 for 1.21.1 (Fabric) | Fabric | 1.21.1 | `actuallyplayed-fabric-1.21.1-1.1.0.jar` |

The version numbers repeat what the jar itself reports, which is what the in-game update
checker compares (see CLAUDE.md section 12). A bare `1.1.0` would sort below every
`1.12.2-x` under Maven's comparison rules.

**The three Fabric versions need a dependency on Fabric API, marked Required.** Without
it a player installs the jar, launches, and gets "requires any version of fabric-api,
which is missing" -- an error that reads as a broken mod rather than a missing library.

Changelog for each: the 1.1.0 entry from `CHANGELOG.md`.

## Before submitting for review

- **An icon.** It decides the click in a grid of search results, well ahead of the text.
  Modrinth bans generative-AI imagery outright since August 2026, so it has to be drawn
  or commissioned.
- **Gallery images.** At least the statistics screen. A GIF of the AFK switch would show
  the one feature a still image cannot.
- **The AI-content disclosure**, if the project uses one: much of this code was written
  with an AI assistant, and the grace period ends 27 September 2026.

## After publishing

Modrinth notifies followers on every update, so the follower count compounds in a way the
download count does not. It is the number worth watching in the first weeks.

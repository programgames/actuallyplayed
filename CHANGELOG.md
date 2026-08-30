# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). One mod version
covers every Minecraft version it is built for: **1.1.0** is released as one set of jars, and
each jar reports `MCVERSION-MODVERSION` (`1.20.1-1.1.0`) because that is what Forge's update
checker compares.

This file feeds three places at once — the GitHub release notes, the CurseForge file
changelog, and the per-version strings in `update.json`. Write it once here.

## [1.1.0] — unreleased

Minecraft 1.7.10, 1.16.5, 1.20.1 and 1.21.1, and two fixes to activity detection that also
reach 1.12.2.

### Added

- **Minecraft 1.7.10** on Forge, **1.16.5** and **1.20.1** on Forge and Fabric, and
  **1.21.1** on NeoForge and Fabric. Every jar is self-contained: no library to install alongside it, on any loader.
- Your history moves with you. The data file has the same format on every version and every
  loader, so switching either one keeps your recorded time.

### Changed

- Activity is now also read by polling the input devices on every client tick, alongside the
  existing events. A state cannot be cancelled by another mod, so a player using a controller
  mod or an inventory-tweak mod is no longer recorded as AFK while actively playing.
- A focus loss is held for 1.5 seconds before it counts. Other applications steal focus for a
  fraction of a second and hand it straight back — launchers, chat overlays, notification
  popups — and each steal used to split the session and charge AFK time to a player sitting at
  their keyboard. The pause menu is exempt and still takes effect at once.

### Fixed

- The state no longer flickers between playing and AFK while the singleplayer pause menu is
  open. Accounting was never affected; only the reported state was unstable.

### Notes for the versions other than 1.12.2

- Settings live in `config/actuallyplayed/actuallyplayed.properties`, edited with the game
  closed. There is no settings screen: providing one on Fabric would mean requiring two more
  mods to change five values.
- `/played` and the key binding are 1.12.2 only. The statistics screen is reachable from
  Esc → Statistics everywhere.

## [1.12.2-1.0.0] — unreleased

First release.

### Added

- Measures time spent on each multiplayer server and each singleplayer world, separating
  time actually played from time spent AFK.
- Retroactive rollback: when inactivity reaches the threshold, the idle minutes already
  counted are taken back out of the played total rather than left there.
- Immediate AFK on alt-tab and on the singleplayer pause menu, without waiting for the
  threshold.
- Activity is read from movement *intent* rather than position, so being carried by a water
  current, a minecart or a mount does not count as playing.
- A statistics screen reachable from Esc → Statistics, showing the current destination only:
  the running session, the totals here, and the details.
- `/played` (alias `/ap`) prints the same figures to chat, and `/played reset` clears the
  current destination after confirmation.
- A key binding to open the screen, registered unbound.
- Five settings, editable in game and applied without a restart: AFK threshold, shortest
  session kept, autosave interval, detailed-history retention, and a diagnostic log.
- Translated into 27 languages, settings screen included: English, French, German, Spanish
  (Spain and Latin America), Portuguese (Brazil and Portugal), Italian, Dutch, Swedish,
  Danish, Finnish, Polish, Czech, Hungarian, Romanian, Greek, Russian, Ukrainian, Turkish,
  Indonesian, Vietnamese, Thai, Japanese, Korean, and Chinese (simplified and traditional).
- Crash resistance: the running session is pre-recorded on every autosave, so a crash costs
  at most the autosave interval rather than the whole session.
- Data is stored as readable, hand-editable JSON, written atomically. A damaged file is set
  aside rather than deleted, and a file from a newer version of the mod is never overwritten.

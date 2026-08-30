# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
uses `MCVERSION-MAJOR.MINOR.PATCH` versioning: `1.12.2-1.0.0` is version 1.0.0 built for
Minecraft 1.12.2.

This file feeds three places at once — the GitHub release notes, the CurseForge file
changelog, and the per-version strings in `update.json`. Write it once here.

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
- `/playtime` (alias `/pt`) prints the same figures to chat, and `/playtime reset` clears the
  current destination after confirmation.
- A key binding to open the screen, registered unbound.
- Five settings, editable in game and applied without a restart: AFK threshold, shortest
  session kept, autosave interval, detailed-history retention, and a diagnostic log.
- French and English translations, including the settings screen.
- Crash resistance: the running session is pre-recorded on every autosave, so a crash costs
  at most the autosave interval rather than the whole session.
- Data is stored as readable, hand-editable JSON, written atomically. A damaged file is set
  aside rather than deleted, and a file from a newer version of the mod is never overwritten.

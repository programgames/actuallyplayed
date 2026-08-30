# Actually Played — Minecraft Forge mod

## What this project is

A **client-side** Minecraft mod that measures time spent on each multiplayer server and in
each singleplayer world, **telling time actually played apart from time spent AFK**.
The figures are shown on a screen grafted onto the vanilla Statistics GUI.

---

## 1. Technical environment

| Item | Value | Note |
|---|---|---|
| Minecraft | **1.12.2** | |
| Forge (compile) | **14.23.5.2847** | See the box below — this is *not* the recommended build |
| Forge (runtime) | 2847 and later, including the recommended **2860** | |
| MCP mappings | **snapshot_20171003** | `stable_39` is declared for 1.12 and warns on 1.12.2 |
| ForgeGradle | **2.3.10** (published, pinned) | Preferred over `2.3-SNAPSHOT` for a reproducible build |
| Gradle | **4.10.3** (wrapper) | ForgeGradle 2.3 does not work beyond this |
| Build JDK | **JDK 8, mandatory** | `C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot` (already in `JAVA_HOME`) |
| `sourceCompatibility` | 1.8 | |

> ⚠️ **Why 2847 and not the recommended build 2860?**
> The `forge-<version>-userdev.jar` artifact, which ForgeGradle 2.3 needs in order to
> compile, is **not published** on `maven.minecraftforge.net` for builds 2848 through 2860.
> Checked in August 2026: 2838 and 2847 return HTTP 200, every build from 2848 to 2860
> returns 404. **2847 is therefore the newest compilable build.** This is not a functional
> limitation: the Forge 1.12.2 API is stable across that range, and a mod compiled against
> 2847 runs unchanged on 2860.
> Do not "fix" `forgeVersion` to 2860 — the build would fail on
> `Could not find forge-userdev.jar`.

> ⚠️ **Never compile with the JDK 17/25 installed on this machine.** ForgeGradle 2.3 and
> Gradle 4.x break on any JDK newer than 8. If the build fails with
> `Unsupported class file major version` or a `NoClassDefFoundError` inside Gradle, check
> `JAVA_HOME` first.

### Mod identity

- **modid**: `actuallyplayed`
- **Mod name**: `Actually Played`
- **Root package**: `fr.julien.actuallyplayed`
- **Version**: SemVer; jar named `actuallyplayed-1.12.2-1.0.0.jar`

---

## 2. Functional decisions (agreed with the user)

### 2.1 Which side runs

**Client only.** The mod does not need to be installed on the server and works on any
vanilla or modded server.
→ Consequence: `clientSideOnly = true` in `@Mod`, no server class, no network packet.

### 2.2 Activity detection

The counter is **active** when at least one of these signals occurs:

1. **Movement intent** — reading `MovementInput` (forward / back / strafe / jump / sneak),
   and **not** the resulting position.
2. **Camera rotation** (change in `yaw` / `pitch`)
3. **Keyboard and mouse input** (any key, click or wheel)
4. **Gameplay interactions** (breaking or placing a block, opening an inventory, sending a
   chat message, crafting)

> **Why intent rather than position?** A player's position changes constantly with no input
> from them: gravity, water currents, minecarts, boats, mounts, knockback, server
> repositioning (rubber-banding), floating-point drift. Classic AFK farms are built on
> exactly that passive movement. Measuring position would classify as "active" precisely the
> situations the mod exists to catch as AFK. `MovementInput` is zero whenever the player is
> being carried: a binary signal, no arbitrary threshold to calibrate, and trivial to port to
> recent Minecraft versions.

**Special states** (death screen awaiting respawn, loading screens, dimension transitions):
**no special rule.** They are treated as ordinary inactivity and subject to the normal
threshold. Fewer special cases means fewer bugs.

### 2.3 The AFK rule

- Threshold: **5 minutes** by default, **configurable**.
- When the threshold is reached, the active counter stops **and the 5 minutes already
  counted are removed from it and moved into the AFK counter** (retroactive rollback —
  this is the heart of the mod).
- Any activity restarts the active counter immediately.
- **Game window not focused (alt-tab) → AFK at once**, without waiting for the threshold,
  with retroactive rollback of the idle time already elapsed. Same for the singleplayer
  pause menu.

> ⚠️ **The amount rolled back is counted as it is charged, never re-derived from a clock
> subtraction.** `PlaytimeEngine.Session.activeSinceLastActivity` is incremented in lock-step
> with `activeMillis` and reset by `markActive`.
> The original implementation computed the rollback as `now - lastActivityAt`. Those two
> quantities agree only while the clock moves forward — and an NTP correction, a manual
> change, or the RTC skew of a dual-boot machine pulls it backwards. When that happened
> mid-idle, the subtraction came out shorter than what had been charged and the difference
> stayed misfiled as playtime: **5 real minutes of play were reported as 6**. Reproduced,
> fixed, and locked in by `rollsBackEverythingChargedEvenWhenTheClockJumpedBackMidIdle`.
> Do not "simplify" this back into a subtraction.

### 2.4 Granularity and data keys

- **Per multiplayer server**, key = `host:port` (one entry per network; no attempt to tell
  BungeeCord sub-servers apart). The name the player gave the server in their server list is
  stored as the display label.
- **Per singleplayer world**, key = save folder name (not the display name, which can be
  renamed without the history having to split in two).
- **Realms is deliberately not tracked.** `getCurrentServerData()` returns `null` for a
  Realms connection: no stable key is available client-side. Agreed with the user — expected
  behaviour, not a bug to fix.
- **Per Minecraft account**, key = the player's **UUID** (survives name changes).

### 2.5 Crash resistance — the provisional session

Two agreed rules contradicted each other: "a session is only committed when it closes" and
"a crash costs at most 60 seconds". With a commit only at close, a crash three hours in lost
all three hours.

**Resolution**: every autosave also writes the running session as a **provisional** entry
(`ProvisionalSession`, the `inProgress` field of the JSON). It is cleared on any clean
close, and is only read back at the next startup **if the game did not exit cleanly**. The
rollback has already been applied by the engine at snapshot time, so a recovered session is
accounted for exactly as a closed one would be. The 30-second rule applies to recovery too.

### 2.6 Session lifecycle

**Short sessions are dropped.** A session **shorter than 30 seconds is discarded entirely**:
it enters neither the history nor the target's totals. A server the player only bounced in
and out of therefore does not appear at all — that is what keeps the screen readable.
→ Implementation consequence: a session is **only committed when it closes**, never
incrementally, otherwise it could not be cancelled after the fact.

**Retention.** Detailed sessions are kept for **90 days**. Beyond that they are **compacted
into monthly aggregates** per target (active time, AFK time, session count). No time is ever
lost from the totals; only the session-by-session detail disappears. The data file therefore
stays bounded over time. Compaction runs at game start.

### 2.7 Persistence

- Format: **a single JSON file**, readable and hand-editable, pretty-printed.
- Location: `.minecraft/config/actuallyplayed/`
- **Periodic autosave** (60 s by default, configurable) + on disconnect + on exit.
- **Atomic write**: a `.tmp` file in the same directory, `FileChannel.force()` to flush the
  OS cache, then `ATOMIC_MOVE`. The target file is always either the complete old version or
  the complete new one — never a truncated mix.
- Maximum loss on a crash: the autosave interval.

**Serialisation is written by hand**, without Gson reflection. Three reasons: the JSON stays
a stable, documented contract the player can read; renaming a Java field cannot silently
break existing files; and nothing depends on reflection surviving obfuscation of a released
jar.

**Tolerance for damaged files.** An unreadable entry (an inconsistent session, an unparsable
target key, a malformed month) is skipped rather than fatal: partial damage should cost the
damaged entries, not the whole history. A wholly unreadable file is **quarantined**
(`.corrupt-<timestamp>`) and the mod starts fresh — it is never deleted.

**A file written by a newer version of the mod** (higher `schemaVersion`): reading is refused
with `UnsupportedSchemaException` and the file is left intact. Overwriting it would destroy
fields this version knows nothing about.

**Totals are derived, never stored.** Every total is recomputed from the sessions and the
aggregates. A stored counter could drift away from what it summarises; deriving makes the
invariant "compaction never changes the totals" true by construction rather than by
discipline.

### 2.8 Interface — the present context, not a catalogue

**A single screen grafted onto the vanilla Statistics GUI**, showing **only the destination
the player is currently in**: this server, or this world. No list, no navigation, no click.

> **Decision revised 2026-08-29.** The first version listed every destination with a detail
> screen behind a click. In use it was a catalogue, and a catalogue answers a question nobody
> asks mid-game. What a player wants to know, controller in hand, is "how long have I been
> playing *here*".

**Storage is unchanged**: each server and each world is still recorded separately, and a
destination's history waits for the player when they return. Only the display narrows to the
current context.

Three blocks separated by thin rules:
- **Current session** — active/AFK state in colour, played time, AFK time, live
- **Total here** — played / AFK / percentage actually played on this destination
- **Details** — first seen, session count, average duration, longest session

**Totals include the running session.** A player thinks of "my time on this server" as
including right now; showing only closed sessions would make the screen look stale the
second they opened it.

**The dated session history is not displayed** (decision of 2026-08-29). The data is still
recorded: it can be shown again without changing anything in storage.

**No in-game feedback** on active ↔ AFK transitions: the mod is entirely silent (no HUD, no
chat message). A diagnostic log exists, disabled by default.

---

## 3. Architecture

Goal: **make porting to other Minecraft versions cheap.**

```
1.12/
├─ core/                        Gradle module — PURE JAVA, ZERO Minecraft import
│  ├─ src/main/java/fr/julien/actuallyplayed/core/
│  │  ├─ PlaytimeTracker.java   Facade: lifecycle, autosave, crash recovery
│  │  ├─ model/                 TargetType, TargetKey, ServerAddress, TrackedSession,
│  │  │                         TrackedTarget, PlayerPlaytime, PlaytimeData,
│  │  │                         MonthlyAggregate, ProvisionalSession
│  │  ├─ engine/                Clock, SystemClock, ActivityState,
│  │  │                         SessionSnapshot, PlaytimeEngine, RetentionPolicy
│  │  ├─ storage/               PlaytimeRepository, JsonPlaytimeStore, PlaytimeCodec,
│  │  │                         AtomicFileWriter, UnsupportedSchemaException
│  │  ├─ util/                  DurationFormatter, DateFormatter
│  │  └─ config/                PlaytimeConfig (immutable, builder)
│  └─ src/test/java/            JUnit tests (injected clock)
│
└─ forge-1.12/                  Gradle module — Forge 1.12.2 adapter layer
   └─ src/main/java/fr/julien/actuallyplayed/forge/
      ├─ ActuallyPlayedMod.java     @Mod, wiring
      ├─ Reference.java              modid, name, version (substituted at build time)
      ├─ bridge/                     TargetResolver, TargetIdentity
      ├─ event/                      PlaytimeClientHandler, StatsGuiHandler
      ├─ client/gui/                 GuiPlaytimeStats
      └─ config/                     ForgeConfig, PlaytimeGuiFactory,
                                     ConfigChangeHandler
```

### Non-negotiable architecture rules

1. **`core` NEVER imports a `net.minecraft.*` or `net.minecraftforge.*` class.** This is what
   guarantees multi-version portability. A Minecraft import in `core` is a bug — and the
   build now fails on it (see §11).
2. Time is **injected** into the engine (the `Clock` interface), never read from
   `System.currentTimeMillis()` inside the business logic → deterministic tests (five
   minutes of AFK can be simulated instantly).
3. The engine works in **milliseconds**, not ticks: ticks stretch with server lag and would
   distort the measurement.
4. The Forge layer only **translates**: it catches Minecraft events and calls the tracker.
   No business rule lives in `forge-1.12`.

---

## 4. Roadmap

Built **MVP first, then iterations**, with in-game validation at each step.

- [x] **Step 1 — Skeleton**: multi-module Gradle layout, ForgeGradle 2.3 `build.gradle`,
      `mcmod.info`, minimal `@Mod` class.
      *Done: `gradlew build` produces the jar with `core` classes bundled and the version
      substituted.*
- [x] **Step 2 — Engine**: `PlaytimeEngine` with the retroactive rollback, immediate AFK on
      focus loss, and short-session dropping.
      *Done: 23 tests green, zero Minecraft import in `core`. Validated by mutation testing —
      disabling the rollback breaks 8 tests.*
- [x] **Step 3 — Persistence**: data model per UUID × target, explicit JSON codec, atomic
      writes, quarantine of corrupted files, monthly compaction.
      *Done: 54 tests green. Validated by mutation testing — a compaction that loses data
      breaks 5 tests.*
- [x] **Step 4 — Forge integration**: activity signal capture, window focus detection, target
      resolution, lifecycle wiring, Forge config.
      *Done: 66 tests green, mod active in game.*
- [x] **Step 5 — GUI**: button grafted onto the vanilla Statistics screen, main screen with
      live session and totals.
- [x] **Step 6 — Detail & polish**: in-game editable config, FR/EN i18n.
      *Note: the per-server detail screen built at this step was later removed — see §2.8.*
- [x] **Step 7 — Hardening pass** (2026-08-29): immutable config, synchronised handler,
      cached target resolution, dead code removed, `ServerAddress` moved into `core` with
      tests, architecture guard task, MIT licence, GitHub Actions CI.
      *Done: 82 tests green.*

---

## 5. Commands

```bash
# Always from the project root, with JAVA_HOME pointing at the JDK 8
./gradlew setupDecompWorkspace   # first time only (long)
./gradlew build                  # compile and produce the jar
./gradlew :forge-1.12:runClient  # launch Minecraft with the mod
./gradlew :core:test             # engine unit tests
./gradlew :core:check            # the tests, plus the no-Minecraft-import guard
```

---

## 6. Conventions

- **Language**: conversation with the user in **French**. Everything written into the
  repository in **English**. See §12.
- **i18n**: no displayed text hard-coded in Java; everything goes through the `.lang` files.
- Never use obfuscated names (`func_xxxxx_x`): always the mapped MCP names.
- This file is updated **as decisions are made**: every new technical or functional decision
  agreed with the user belongs here.

---

## 7. Traps already hit (do not rediscover them)

- **`Could not find forge-userdev.jar`** → `forgeVersion` points at a build with no published
  userdev artifact. See the box in §1: stay on 14.23.5.2847.
- **`Could not find net.minecraftforge:forge:1.12.2-null`** → inside the `minecraft { }`
  block the Groovy delegate is the ForgeGradle extension, which has its own `mcVersion` and
  `forgeVersion` properties. An unqualified reference resolves against the (empty) extension,
  not `gradle.properties`. Always qualify with `project.`, or resolve the value outside the
  block — which is what `forge-1.12/build.gradle` does.
- **Warning `This mapping 'stable_39' was designed for MC 1.12`** → use `snapshot_20171003`,
  the reference mapping for 1.12.2.
- **No Forge dependency floor is needed for `ClientChatEvent`.** A review flagged it as
  possibly absent from early 1.12.2 Forge builds, which would crash on event registration.
  Checked against the universal jars: the class is present in `14.23.0.2486`, the very first
  1.12.2 build. No `dependencies` clause required on that account.
- **List rows not clickable.** `GuiSlot.handleMouseInput()` only handles the wheel and
  scrolling: it does **not** propagate clicks. A `GuiScreen` hosting a `GuiListExtended` must
  forward `mouseClicked` **and** `mouseReleased` explicitly, on top of `handleMouseInput`.
  Without it the list renders and scrolls perfectly but is inert — a silent failure with no
  error anywhere.
- **`Format error: %` in a column header.** Minecraft runs every translation through
  `String.format`, so a lone `%` in a `.lang` file is not a valid specifier. Avoid the
  character in translation values.
- **Mojibake in game.** Gradle was compiling with the platform default encoding
  (windows-1252). Fixed with `options.encoding = 'UTF-8'` on every `JavaCompile` task in the
  root `build.gradle`. Minecraft's default font covers little beyond Latin-1 anyway, so
  decorative glyphs are best avoided in Java string literals.

- **Observers must opt into cancelled events.** `@SubscribeEvent` defaults to
  `receiveCanceled = false`. Every activity handler here is a pure observer, and controller
  mods, inventory-tweak mods and chat-macro mods cancel input events routinely — without the
  flag, those players would be recorded as AFK while actively playing. All activity handlers
  carry `receiveCanceled = true`; keep it that way when adding one.
- **`Display.isActive()` is not trustworthy.** It reports a false negative on several Linux
  window managers, in borderless fullscreen, and across virtual desktops. Since
  `onActivity()` is ignored while unfocused, a false negative would strand a session in AFK
  for its whole life with no way out. A real input event now overrides it for
  `FOCUS_GRACE_MILLIS`: the OS only delivers input to a focused window, so input *is* proof.
- **Never cache a failed target resolution.** `resolveTarget()` caches on the world instance;
  caching a `null` result would pin it for the entire session and silence the mod with no
  error anywhere. Only successful resolutions are cached.
- **`gradlew` must stay mode `100755` in git.** It was committed `100644`, which gives every
  Linux and macOS contributor `Permission denied` on clone. The CI used to paper over it with
  a `chmod` step; that step has been removed so a regression fails the build instead of being
  hidden.

### ⚠️ Development environment trap: the username changes on every launch

`gradlew runClient` used to start Minecraft with a random username (`Player640`,
`Player123`, ...), visible in the log as `Setting user:`. The offline UUID is derived from
the username, so **every launch created a different account** and the previous run's data
vanished from the stats screen.

This was never a bug: on a real installation the Mojang account UUID is stable. It is now
pinned to `PlaytimeDev` via `args '--username'` on the `runClient` task, so test sessions are
comparable across launches.

---

## 8. To validate manually in game

Forge integration cannot be covered by automated tests. Checked during a real session:

- [x] Singleplayer session detected: key `singleplayer:New World` = the save folder name
      (2026-08-29)
- [x] Stats screen verified visually (2026-08-29): button present, live session, totals,
      coloured ratio
- [x] **Inactivity → AFK with retroactive rollback** (2026-08-29, threshold lowered to 60 s
      for the test): 25 s of play then 60 s of stillness → AFK to the second, `played`
      unchanged, `afk` +60 s. Not one second lost or double-counted.
- [x] **Alt-tab → immediate AFK** (2026-08-29): instant in both directions, with zero
      rollback when the player was active right up to the switch. Verified over five
      consecutive round trips, accounting coherent throughout.
- [x] Singleplayer pause menu → immediate AFK (2026-08-29)
- [x] **Inventory → stays active** (2026-08-29): 122 consecutive seconds of inventory
      handling, all counted as played, no AFK transition.
- [x] **Water current → AFK** (2026-08-29) — **the decisive test.** The player was genuinely
      being carried by the current, with no input for over a minute: AFK at the threshold,
      `played` unchanged. Reading `MovementInput` rather than position is validated in real
      conditions — a movement-based counter would have counted that time as played.
- [x] Quitting via the window close button → the shutdown hook runs and writes the file;
      `inProgress` absent afterwards, session closed and recorded (2026-08-29)
- [x] **Crash → recovery** (2026-08-29): process killed with `Stop-Process -Force`, so no
      shutdown hook at all. On restart:
      `Recovered 11 minutes of play from a session the last run did not close.`
      `inProgress` cleared, session moved into `sessions` with the exact values of the last
      autosave. Loss: the autosave interval, nothing more.
- [x] **Joining a real server** → key `server:host:port`, label taken from the server list
      (2026-08-30)
- [x] The extra dim layer hides the crosshair and hotbar behind the stats screen (2026-08-30)

Every item on this list is now verified in game.

Realms is absent from this list on purpose: not tracking it is a deliberate choice (§2.4).

---

## 9. GUI implementation notes

- **The button is added to `GuiStats` through `GuiScreenEvent.InitGuiEvent.Post`**, with an
  id (7913) deliberately far from the small ones vanilla uses, and the action event is
  cancelled so the vanilla screen does not react to an id it does not know.
- **`GuiPlaytimeStats.doesGuiPauseGame()` returns `false`**: opening the statistics must not
  change what the mod is measuring.
- **Because the game is not paused, the HUD keeps rendering behind the screen.** The
  crosshair sits at the exact centre — precisely where the middle of a centred line of text
  lands — and showed through as a stray `+`. An extra dim layer hides it and the hotbar.
- **Totals are computed once when the screen opens, not per frame.** They are derived by
  walking every stored session, and nothing can close a session while the screen is up.
- **Durations use unit letters** (`5h 12m`) rather than words: they read identically in
  French and English, so the numbers need no translation and the columns stay narrow.
- **Ratio colour coding**: green above 80 % played, red below 40 %. One glance is enough to
  spot a destination where the player is mostly AFK.
- **Dates are year-first** (`2026-08-29`) rather than in a locale-specific order:
  `08/09/2026` means two different days depending on the reader.
- **Config editable in game** through `IModGuiFactory` (the "Config" button in the mod list).
  `ForgeConfig` keeps its `Configuration` instance so the screen edits the same file, and
  each edit produces a **fresh immutable `PlaytimeConfig`** that the tracker publishes in one
  assignment. Every setting applies without a restart.

### Thread safety

`PlaytimeTracker` **and** `PlaytimeClientHandler` are **fully synchronised**. Almost every
call arrives on the Minecraft client thread, but the shutdown hook that flushes the session
on exit runs on its own thread while the game loop may still be ticking. Without those locks,
that final save could interleave with an accrual, or close a session twice — or not at all.

`PlaytimeConfig` is **immutable** and **swapped wholesale** (a `volatile` field in
`PlaytimeTracker`). A value that never changes cannot be observed half-written: the race
disappears by construction rather than by discipline. The engine reads the configuration
through a `Supplier` on every use, so a setting changed in game applies immediately.

---

## 10. Documentation and IDE integration

- **`README.md`** (English) is the documentation for players and contributors;
  **`README.fr.md`** is its French translation. Keep both in step when a configuration
  option changes.
- **`gradlew eclipse`** generates the project files **and** the
  `forge-1.12_Client.launch` / `forge-1.12_Server.launch` run configurations.
- **`gradlew genIntellijRuns`** must be run **after** importing into IntelliJ: it writes into
  `.idea/workspace.xml`, which does not exist before. Otherwise it fails with
  "Intellij workspace file could not be found".
- **VS Code cannot launch Minecraft directly**: ForgeGradle starts it in a forked JVM with a
  classpath it builds itself. Use `runClient --debug-jvm` (port 5005) and remote attach;
  `.vscode/` holds ready-made tasks and an attach configuration.
- IDE project files are **generated**, so `.gitignore` excludes them. Only the three
  hand-written `.vscode/` files are versioned.

---

## 11. Automated guard rails

- **`gradlew :core:check` fails if `core` imports Minecraft or Forge.** The
  `checkNoMinecraftImports` task reports the offending file and line. The central
  architectural invariant is therefore enforced by tooling rather than vigilance — verified
  by deliberately injecting a forbidden import.
- **GitHub Actions CI** (`.github/workflows/build.yml`): JDK 8, cached Minecraft decompile,
  engine tests **before** the Forge setup (fail in seconds rather than after twenty minutes
  of decompiling), then build and jar upload.
- **UTF-8 encoding forced** on every `JavaCompile` task.
- **`acceptedMinecraftVersions = "[1.12.2]"`** — strictly the version compiled and tested.
- **`.gitattributes`** pins `gradlew` to LF so the Linux CI runner does not hit
  `bad interpreter`.

---

## 12. Release

- **Version scheme: `MCVERSION-MAJOR.MINOR.PATCH`**, e.g. `1.12.2-1.0.0`. The Forge
  convention adds `MAJORAPI`, which would sit at zero forever here: this mod publishes no
  Java API and touches no world data. A `MAJOR` bump is reserved for a `playtime.json`
  schema break that loses history. When a 1.20 port lands, `1.20.1-1.2.0` slots in beside
  `1.12.2-1.2.0` and sorts correctly in every launcher.
- **Git tags use the full version** (`1.12.2-1.0.0`, never `v1.0.0`) so the tag matches the
  jar name exactly and the release workflow can check one against the other.
- **`update.json` at the repo root** drives Forge's "update available" marker. Two traps
  live here:
  - The `updateUrl` field of `mcmod.info` is **dead metadata** — FML only ever reads the
    `updateJSON` parameter of `@Mod`.
  - The versions inside must be the exact strings the mod reports, prefix included
    (`1.12.2-1.0.0`). Forge compares them with Maven's `ComparableVersion`, so a bare
    `1.0.0` would sort below any `1.12.2-x` and the checker would report "up to date"
    forever.
  - **The URL points at `raw.githubusercontent.com`, so the repository must be public for
    the check to work.** While it is private, Forge silently reports `FAILED`.
- **`CHANGELOG.md` feeds three consumers**: the GitHub release body, the CurseForge file
  changelog, and the per-version strings in `update.json`. Write it once there.
- **`.github/workflows/release.yml`** fires on a version tag, verifies the tag matches
  `gradle.properties`, builds, and opens a **draft** GitHub release with the jar attached.
  Draft on purpose: publishing is a decision, not a side effect of pushing a tag.
- **`gradlew clean` fails while Minecraft is running** — the running game holds
  `core/build/libs/core-*.jar` open. Close the game first. Not a build defect.

### Still to do before the first CurseForge publish

- **A logo.** `mcmod.info` has `logoFile` empty, and there is no project avatar. Modrinth
  bans generative-AI imagery outright since August 2026, and CurseForge rejects misleading
  undisclosed AI images — the icon has to be drawn or commissioned.
- **Make the repository public**, otherwise the update checker cannot reach `update.json`
  and the `url` in the mod list points at a 404.
- **Check the name "Actually Played" is free on CurseForge.** Uniqueness is enforced and
  a rejection is terminal: the project cannot be re-evaluated, it has to be recreated under
  a new name.
- **Mark the first file as `Release`, not beta** — CurseForge only syncs a project to its
  launcher once it has one file marked as a release.
- **Tick the AI-content disclosure on Modrinth.** Much of this code was written with an AI
  assistant; the grace period ends 27 September 2026.

---

## 13. Language

- **Everything committed to the repository is in English**, without exception: code,
  comments, javadoc, build files, `.vscode`, `.gitignore`, `mcmod.info`, `README.md`, and
  this file.
- **Translation files are the sole exception**, because that is their purpose:
  `fr_fr.lang` and `README.fr.md`.
- **`.lang` parity must be maintained** — 18 keys today, none orphaned, none missing.
- Spoken and written exchanges with the user remain in **French**.
- **Licence: MIT** (`LICENSE`). A licence cannot be revoked retroactively: versions released
  under MIT stay MIT.

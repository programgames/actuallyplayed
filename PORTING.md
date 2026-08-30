# Porting plan — Actually Played across loaders and versions

Working document for the multi-version / multi-loader port. It records the decisions that
were argued out before any code was written, and tracks the phases. Update it as the work
proceeds; when a decision here becomes permanent, it moves into `CLAUDE.md`.

Status: **phase 1 complete and verified in game (2026-08-30). Phase 2 not started.**

---

## 1. Goal

Publish Actually Played on the Minecraft versions and mod loaders that still have players,
without maintaining N independent copies of the mod.

The starting position is favourable and should be stated plainly, because it drives every
decision below:

| Module | Lines | Portable? |
|---|---|---|
| `core` | 2 319 | **Yes — pure Java 8, zero Minecraft import, already guarded by tooling** |
| `forge-1.12` | 1 441 | No — this is what a port has to rewrite |

(Figures as of the start of the work. Phase 1 has since moved the screen's derivation into
`core`, which now stands at 2 843 lines.)

The engine, the storage layer, the retention policy and the two-clock accounting are already
loader- and version-agnostic. Java 8 bytecode runs unchanged under Java 21. **`core` needs no
porting work at all.** What a port costs is the adapter, and the plan is built around
shrinking that adapter before duplicating it.

---

## 2. Target matrix

Ranked by real audience (living modpacks, not historical download counts) against toolchain
cost.

| Version | Loader(s) | Java | Wave | Rationale |
|---|---|---|---|---|
| 1.12.2 | Forge | 8 | **shipped** | large legacy base |
| 1.20.1 | Forge | 17 | **1** | the reference Forge modpack version |
| 1.21.1 | NeoForge, Fabric | 21 | **1** | the current modpack base |
| 1.21.x latest | NeoForge, Fabric | 21 | 2 | players who track the newest release |
| 1.16.5 | Forge | 8/11 | 3 | last "classic" Forge, packs still active |
| 1.19.2 | Forge, Fabric | 17 | filler | near-free once the matrix exists |
| 1.18.2 | Forge, Fabric | 17 | filler | near-free, declining audience |
| 1.7.10 | Forge | 8 | deferred | niche but devoted; separate toolchain entirely |
| 1.8.9 | Forge | 8 | dropped | the PvP-client audience does not want this mod |
| 1.20.2 – 1.20.6 | — | — | dropped | transitional, no stable audience |

Two facts shape the matrix:

- **Forge is effectively dead above 1.20.6.** On modern versions the pair is
  NeoForge / Fabric, not Forge / Fabric.
- **1.21.5 rewrote the GUI render pipeline.** Screen-drawing code from before it does not
  compile after it. That is the real technical boundary on the modern side — not 1.21.

---

## 3. Architecture decisions

### 3.1 1.12.2 stays a standalone module — it does not join the modern tree

The 1.12 / 1.13+ boundary cannot be crossed at reasonable cost: obfuscated MCP mappings vs
Mojang mappings, LWJGL2 vs LWJGL3, `GuiScreen` vs `Screen`, ForgeGradle 2.3 + Gradle 4 +
JDK 8 vs Gradle 8 + JDK 21. A preprocessor spanning that line produces unreadable code for no
gain. Sharing happens through `core` and nowhere else. The same reasoning will apply to
1.7.10 if it is ever done.

### 3.2 Mojang mappings (Mojmap) on every modern target, Fabric included

Minecraft ships obfuscated; a mapping table is required to write against it. Mojang has
published official deobfuscation maps since 1.14.4, and NeoForge uses them by default. Yarn
(the Fabric community mapping) gives *different* names to the same classes.

**Shared source across loaders is only possible if all of them compile against the same
mapping.** Loom can be configured to use Mojmap on Fabric; that configuration is a
prerequisite for the whole plan, not a preference.

This is also the second reason 1.12 cannot join: Mojmap does not exist before 1.14.4.

### 3.3 MultiLoader source-set layout, **not** Architectury API

Both solve the loader axis. The difference that matters here is the runtime dependency.

| | Cost | Benefit *for this mod* |
|---|---|---|
| Architectury API | the player must install a second mod alongside this one | close to nothing |
| MultiLoader | slightly more hand-written plumbing | self-contained jar |

Architectury API exists to smooth over registries, network packets and menus across loaders.
This mod is client-only, registers nothing, and sends no packet — none of the three apply.
Paying a required dependency, and the install failures that come with it, buys nothing.

Architectury **Loom** (the build tool) is a different thing from Architectury **API** (the
runtime library) and remains an acceptable fallback if the MultiLoader Gradle setup proves
painful. Only the API is ruled out.

`common/` must not compile if a loader import appears in it — the same class of tooling guard
as `checkNoMinecraftImports` on `core` today, one level up.

### 3.4 Stonecutter for the version axis

MultiLoader handles loaders at a fixed version. Between 1.20.1 and 1.21.5 the same drawing
call is spelled differently, and a git branch per version means backporting every fix five
times.

Stonecutter is a Gradle comment-preprocessor: one source tree, N jars. The inactive branch is
block-commented, so the file stays valid Java at all times and the IDE stays quiet.

```java
//? if >=1.20.2 {
graphics.drawString(font, line.text(), x, y, line.color());
//?} else {
/*font.draw(matrices, line.text(), x, y, line.color());
*///?}
```

### 3.5 Target layout

```
actually-played/
├─ core/                     shared by everything, unchanged
├─ forge-1.12/               ForgeGradle 2.3, JDK 8, isolated
└─ modern/                   Stonecutter x MultiLoader, Mojmap throughout
   ├─ common/                most of the adapter, vanilla-only
   ├─ fabric/                entry point + loader glue
   └─ neoforge/ (+ forge/)   entry point + loader glue
```

---

## 4. Phase 1 — shrink the adapter before duplicating it

**All of phase 1 lands on 1.12.2 first**, where the behaviour can be checked against the
manual test list already validated in `CLAUDE.md` §8. Doing it later would mean revalidating
it on three versions instead of one. Every item is worth doing on its own merit even if no
port ever happens.

### 4.1 Add tick polling as an activity source

The adapter currently subscribes to 7 activity events plus the tick. Half of them have no
equivalent on Fabric and would need Mixins — the most expensive and most fragile part of any
port. The client tick is the one event that exists identically on every loader and every
version, and every signal in `CLAUDE.md` §2.2 is readable as *state* on each tick:

| Signal | Polled state |
|---|---|
| Movement intent | `MovementInput` (forward/back/strafe/jump/sneak) |
| Camera rotation | yaw/pitch delta against the previous tick |
| Keyboard | every key's down-state, swept raw rather than through key bindings — typing in chat uses keys no binding claims |
| Mouse buttons | button down-state |
| Mouse movement inside a screen | cursor position, **read only while a screen is open** |
| Break / attack / use | arm swing, which outlives the click by several ticks |
| Mouse wheel | selected hotbar slot — the wheel leaves no state, its effect does |

Two corrections to an earlier draft of this table, both of which would have been bugs:

- **An open screen is not activity.** Only input *inside* it is. Polling "a screen is open"
  would count a player who leaves their inventory up and walks away as active forever.
- **Cursor position must not be read while the cursor is grabbed.** In play, Minecraft
  re-centres it every frame, so it would either sit still or jitter permanently — and a
  permanent jitter means the mod never detects AFK at all. Mouse movement in play is already
  caught as camera rotation.

**Activity is signalled on a *change* of that state, never on a state being held.** A key
still down at the moment of an alt-tab stays down in the input buffers for as long as the
window is unfocused; treating "held" as activity would strand the session as permanently
active, the exact opposite of §2.3. A change, conversely, is real proof of focus — the state
only moves when the OS delivers an input event to a focused window — so it can safely restore
focus as the discrete events do. Keys genuinely held while playing are covered elsewhere:
movement by `MovementInput`, mining by the swing flag.

**This is additive, not a replacement.** `onActivity()` simply gains an eighth caller. 1.12
keeps all 7 of its event subscriptions and gains polling on top; Fabric and NeoForge
implement polling only. The measurement rules — intent rather than position, the retroactive
rollback, the two clocks — are untouched.

Precision, stated honestly: a tick is 50 ms against a 300 000 ms threshold, so detection
latency is 0.017 % of the threshold and irrelevant. The only genuine gap is a mouse click
shorter than 50 ms, which in practice never arrives alone — it comes with mouse movement
(rotation) or an arm swing, both of which are state.

Polling also closes two weaknesses the event path has today:

- `InputEvent.KeyInputEvent` does not fire while a screen is open, which is why
  `GuiScreenEvent.KeyboardInputEvent` had to be subscribed alongside it. Polled state has no
  such asymmetry.
- The `receiveCanceled` trap (`CLAUDE.md` §7): controller and inventory-tweak mods cancel
  input events, and without the flag those players are recorded as AFK while playing.
  **A state cannot be cancelled by another mod.** The bug class disappears by construction.

- [x] Poll every signal above on the client tick, feeding the existing `onActivity()`
      — `PlaytimeClientHandler.detectRawInput()` / `readInputFingerprint()`
- [x] Re-run the `CLAUDE.md` §8 manual checks and confirm they pass unchanged (2026-08-30)
- [x] Verify specifically that carried motion still classifies as AFK — re-run with a minecart
      on a powered-rail loop, rolling for the full minute with no input: AFK at 60 s to the
      second, `played` frozen. The polled fingerprint does not read carried motion as intent.

### 4.2 Move screen layout into `core` as `StatsScreenModel`

`GuiPlaytimeStats` is 300 lines mixing two concerns: *what to show* (walking the sessions,
formatting `5h 12m`, deciding 83 % renders green, composing the three blocks) and *how to
paint it* (`drawString`, coordinates). The first is pure Java, portable and testable; the
second is version-specific and is exactly what 1.21.5 breaks.

`core` produces a `StatsScreenModel` — a list of lines, each carrying text, colour and style.
Each platform keeps a ~60-line draw loop.

- [x] Extract the model into `core`, with unit tests over the derivation and the colour rules
      — `core/screen/`: `StatsScreenModel`, `ScreenLine`, `TextSpan`, `TextStyle`,
      `RecordedTotals`; 20 new tests, 146 green in total
- [x] Reduce `GuiPlaytimeStats` to a draw loop over the model — 300 lines to 229, of which
      the genuinely version-specific part is the ~80-line `draw`/`resolve`/`format` block
- [x] Confirm in game that the screen is visually identical to today's (2026-08-30)

### 4.3 Generate `.json` translations from the `.lang` sources

Minecraft wants JSON from 1.13 onward. 27 locales across 5 versions cannot be maintained by
hand. `.lang` stays the single source of truth and `checkLangParity` keeps running on it
unchanged; a Gradle task emits the JSON at build time.

- [x] Conversion task — `generateLangJson` in the root `build.gradle`, beside
      `checkLangParity` and for the same reason: it only reads and writes text files, so it
      answers in seconds and needs neither Minecraft nor ForgeGradle. It `dependsOn
      checkLangParity`, so a divergence can never be baked into 27 files at once. Output goes
      to `build/generated-lang/`; nothing is committed.
- [x] Verified by deliberate fault injection, as the other guard rails were: a quote and a
      backslash in a value come out correctly escaped and parse back to the original string;
      a control character fails the build naming the codepoint rather than emitting
      unparsable JSON. Across the 27 locales, all 37 keys round-trip byte for byte, including
      7 178 non-ASCII characters, with no BOM.
- [ ] Confirm in game against a version that actually loads the JSON (phase 2)

### 4.4 Phase 1 exit criteria

- [x] 1.12.2 behaves identically to today, with the §8 manual list re-verified in game
      (2026-08-30). The session also turned up one defect of its own — the pause-menu focus
      flap — which is now fixed and documented in `CLAUDE.md` §8.
- [x] `core` test count up — 126 to 146, the screen model now covered

On the third criterion an earlier draft of this file promised the adapter would get shorter.
It has not, and the promise was the wrong measure. Adding polling costs about a hundred lines
in `PlaytimeClientHandler`, which roughly cancels the seventy the screen gave up, so
`forge-1.12` sits near where it started. What actually changed is what a *port* has to carry:
seven event subscriptions with no Fabric equivalent became one polled method, and the screen's
derivation became shared, tested code instead of something each version reimplements. The
figure to watch is the size of `modern/common` once phase 2 lands, not the size of the module
that keeps 1.12 working.

---

## 5. Phase 2 — stand up the modern tree

- [ ] `modern/` with MultiLoader source sets, Mojmap on all targets including Fabric
- [ ] Stonecutter wired, a single active version to begin with
- [ ] A guard that fails the build on a loader import inside `common/`
- [ ] First target: **1.20.1 Forge**, feature-complete against 1.12
- [ ] Re-run the §8 manual list on 1.20.1 — none of it is covered by automated tests

## 6. Phase 3 — fan out

- [ ] NeoForge 1.21.1
- [ ] Fabric 1.21.1
- [ ] Latest 1.21.x — expect the 1.21.5 render-pipeline break to hit the draw loop, and
      nothing else
- [ ] 1.16.5 Forge
- [ ] 1.19.2 / 1.18.2 as filler
- [ ] Release workflow extended to build and attach the whole matrix

---

## 7. Per-loader surface that stays irreducible

After phase 1, this is what genuinely cannot be shared:

| | Fabric | NeoForge |
|---|---|---|
| Entry point | `ClientModInitializer` | `@Mod` |
| Client tick | `ClientTickEvents` | `ClientTickEvent` |
| Button on the stats screen | `ScreenEvents.AFTER_INIT` | `ScreenEvent.Init.Post` |
| Config + config screen | ModMenu / Cloth Config | `IConfigScreenFactory` |
| Metadata | `fabric.mod.json` | `neoforge.mods.toml` |

Roughly 120 lines per loader.

---

## 8. Open questions

- **Config screen on Fabric.** Cloth Config is the usual answer but is another required
  dependency — the same objection raised against Architectury API in §3.3. Falling back to a
  hand-editable file with no in-game screen may be the better trade on Fabric. Decide before
  phase 3.
- **Jar naming across the matrix.** `CLAUDE.md` §12 specifies
  `MCVERSION-MAJOR.MINOR.PATCH`, which sorts correctly per version. Confirm how the loader is
  distinguished — `actuallyplayed-1.21.1-neoforge-1.1.0.jar` or similar.
- **1.7.10** stays out of scope until the modern matrix ships.

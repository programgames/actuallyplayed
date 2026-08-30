# Playtime Tracker

**How long have you *actually* played?**

Minecraft counts the minutes the game was open. It cannot tell an hour spent building apart
from an hour spent running a farm while you watched a video. Playtime Tracker can.

The mod measures time spent on each server and in each singleplayer world, strictly
separating **time actually played** from **time spent AFK**.

- Minecraft 1.12.2 · Forge 14.23.5.2847+
- **Client-side only** — works on any server, without that server having the mod
- No network traffic, no telemetry; everything stays on your machine

*[Version française](README.fr.md)*

---

## What it does

The counter runs while you play. As soon as it sees no activity for 5 minutes it stops —
**and takes back the 5 minutes it had just counted**. They move into the AFK counter. You do
not earn playtime by walking away.

What counts as activity:

| Signal | Detail |
|---|---|
| Movement intent | Forward, back, strafe, jump, sneak |
| Camera rotation | Moving the view |
| Keyboard and mouse | Any key, click or wheel, including inside an inventory |
| Interactions | Breaking or placing a block, opening a container, typing in chat |

Two cases stop the counter **immediately**, without waiting for the 5 minutes:

- **Alt-tab** — you have left the game window
- **Singleplayer pause menu** — the world is frozen, you are not playing

### Why AFK farms do not fool it

The mod reads your movement **intent**, not your position.

That is the distinction that matters. Your position changes constantly without you doing
anything: gravity, water currents, minecarts, mounts, a mob shoving you, the server
correcting you. Classic AFK setups are built on exactly that — a water canal, a minecart
loop. A mod that measured movement would count you "active" all night long.

Playtime Tracker reads the commands you send. They are zero while you are being carried.
**A player drifting in a water current is marked AFK once the threshold passes, as they
should be.**

---

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/) for 1.12.2
2. Drop `playtimetracker-1.12.2-x.y.z.jar` into `.minecraft/mods/`
3. Start the game

No dependencies. Nothing to install server-side.

---

## Usage

Three ways in:

- **Esc → Statistics → the "Playtime" button** in the top right
- **`/playtime`** (or `/pt`) prints the same figures to chat
- **A key of your choice** — the binding is registered unbound, under
  *Options → Controls → Playtime Tracker*

The screen shows the destination you are in, and only that one:

- **Current session** — your state (playing / AFK for X), played time, AFK time
- **Total here** — the running total on this server or world, with the percentage actually
  played (green above 80 %, red below 40 %)
- **Details** — first seen, session count, average duration, longest session

Every server and every world keeps its own history. It is waiting for you when you return.

**`/playtime reset`** clears the destination you are on, after asking for confirmation. It
never touches the others; to wipe everything, delete the data file with the game closed.

---

## Configuration

Two ways to change the settings:

- **In game**: mod list → *Playtime Tracker* → **Config**. Changes apply immediately, with
  no restart.
- **By file**: `.minecraft/config/playtimetracker/playtimetracker.cfg`

| Option | Default | What it does |
|---|---|---|
| `afkThresholdSeconds` | `300` | Inactivity after which the counter stops. The elapsed idle time is removed from your played total and moved to AFK. |
| `minSessionSeconds` | `30` | Shorter sessions are discarded entirely, so brief visits do not clutter your statistics. `0` keeps everything. |
| `autosaveIntervalSeconds` | `60` | How often the data file is written. This also bounds how much of a running session a crash can cost you. |
| `retentionDays` | `90` | How long each session is kept in full detail. Older ones are merged into monthly summaries — **no playtime is ever lost**, only the detail. |
| `debugLogging` | `false` | Logs every played ↔ AFK transition. Useful to check the detection; the mod is silent by default. |

---

## Your data

Everything lives in `.minecraft/config/playtimetracker/playtime.json`, as readable,
hand-editable JSON.

```
playtime.json
└── account (UUID)
    └── server or world
        ├── detailed sessions (last 90 days)
        └── monthly summaries (older)
```

A few guarantees, because losing months of statistics would be absurd:

- **Atomic writes.** The file is written beside the old one and then swapped in as a whole.
  It is never half-written, even if the game dies mid-save.
- **Crash resistance.** The running session is pre-recorded on every autosave. If the game
  crashes after three hours, you get everything back except the last minute.
- **A damaged file is set aside, never deleted.** It is renamed to `.corrupt-<timestamp>` and
  the mod starts cleanly, so you can still attempt a recovery.
- **Separation by account.** Identity is the Mojang UUID, not the username: changing your
  name does not cut your history in two.

**Realms is not tracked.** The client receives no stable identifier for a Realms connection —
there is nothing to attach the data to.

---

## FAQ

**Does it work on servers?**
Yes, on all of them. The mod is purely client-side: it never talks to the server and does not
need to be installed there. It can never get you rejected at login.

**Does browsing my inventory count as playing?**
Yes. Navigating an inventory, a chest or a mod GUI is playing.

**What if I die and leave the death screen up?**
No special rule: the normal idle threshold applies. Respawn within a second and it is played
time; go and eat and it turns into AFK.

**Is a network like Hypixel one entry or several?**
One, keyed by `host:port`. Minecraft 1.12.2 gives the client no reliable way to tell
BungeeCord sub-servers apart.

**It says I actually played 30 % of the time. Is that normal?**
That is precisely the information the mod exists to give you. What you do with it is your
call.

---

## Developing on the project

### Prerequisites

**JDK 8 is mandatory.** ForgeGradle 2.3 and Gradle 4.10.3 do not run on any newer JDK. The
build fails on purpose, with a clear message, if `JAVA_HOME` points elsewhere.

```bash
# Check
java -version   # must report 1.8

# Otherwise, for the current session (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot"
```

### First setup

```bash
./gradlew setupDecompWorkspace   # once only — downloads and decompiles Minecraft (10-20 min)
./gradlew build                  # compile and produce the jar
./gradlew :core:test             # engine unit tests
./gradlew :forge-1.12:runClient  # launch Minecraft with the mod
```

The jar lands in `forge-1.12/build/libs/`.

### IntelliJ IDEA

1. **File → Open** and select the project folder. IntelliJ detects Gradle on its own.
2. In the import dialog, pick the **JDK 8** as the Gradle JVM.
3. Once the import finishes, run:
   ```bash
   ./gradlew genIntellijRuns
   ```
   > This task **must** run after the import: it writes into `.idea/workspace.xml`, which
   > does not exist before. If it reports
   > *"Intellij workspace file could not be found"*, the project has not been imported yet.
4. Restart IntelliJ. The **Minecraft Client** and **Minecraft Server** configurations appear
   in the run dropdown, ready to launch or debug.

### Eclipse

```bash
./gradlew eclipse
```

Then **File → Import → Existing Projects into Workspace** and select the folder.

The launch configurations are generated automatically in `forge-1.12/`:

- `forge-1.12_Client.launch`
- `forge-1.12_Server.launch`

Right-click either one → **Run As** or **Debug As**. Breakpoints work straight away.

### Visual Studio Code

Install the *Extension Pack for Java*, then open the folder. `.vscode/settings.json` already
points VS Code at the JDK 8.

The tasks are ready (**Ctrl+Shift+P → Run Task**):

| Task | Effect |
|---|---|
| Build | `gradlew build` |
| Test (core) | `gradlew :core:test` |
| Run Minecraft | Starts the game |
| Run Minecraft (wait for debugger) | Starts the game waiting for a debugger on port 5005 |

**To debug**: launch the **"Attach to Minecraft"** configuration (F5). It starts the game in
waiting mode and then connects to it.

> ForgeGradle runs Minecraft in a separate JVM, with a classpath and arguments it builds
> itself. No IDE can launch it directly — hence the remote attach. The same approach works in
> all three IDEs: `./gradlew :forge-1.12:runClient --debug-jvm` opens port 5005.

### Quality checks

```bash
./gradlew :core:check   # tests plus the architecture check
```

`checkNoMinecraftImports` **fails the build** if any class in `core` imports
`net.minecraft.*` or `net.minecraftforge.*`, pointing at the file and line. The architectural
rule is enforced by tooling, not by vigilance.

The GitHub Actions CI runs the engine tests **before** setting up the Forge workspace: if the
logic is broken you know within seconds instead of after twenty minutes of decompiling.

### Project structure

```
core/          Business logic — pure Java, NO Minecraft dependency, covered by 87 tests
forge-1.12/    Forge 1.12.2 adapter layer — translates game events, nothing more
```

This separation is not decorative: **`core` never imports a `net.minecraft.*` class**. That is
what will make porting to recent Minecraft versions cheap — only the `forge-*` layer will
need rewriting.

Two rules follow from it:

- Time is **injected** into the engine through a `Clock` interface, never read directly. A
  test can therefore simulate five minutes of inactivity instantly.
- The engine counts in **milliseconds**, not ticks — ticks stretch with server lag and would
  distort the measurement.

---

## Licence

[MIT](LICENSE). You may use, modify, bundle it in a modpack and redistribute it, including
commercially, as long as you keep the copyright notice.

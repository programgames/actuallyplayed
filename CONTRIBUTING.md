# Contributing to Actually Played

Thanks for looking. This is a small mod; the bar for a useful contribution is low.

**The quickest way to help is a translation.** The mod ships in 27 languages, but only
English and French were written by a native speaker — a correction to any of the other 25 is
as welcome as a new language.

Copy `forge-1.12/src/main/resources/assets/actuallyplayed/lang/en_us.lang`, rename it to your
locale code — the exact one Minecraft 1.12.2 uses, lowercase, or the game will never load the
file — and translate the right-hand side of each line. Keep the keys as they are, and
never put a bare `%` in a value: Minecraft runs every translation through a formatter, and a
lone percent sign turns the line into `Format error: %` in game. This has already happened
once here. The `%s` in `actuallyplayed.gui.state.afk` is a duration; put it wherever your
language wants it.

Every locale carries the same 37 keys as `en_us.lang`. A missing key falls back to English
silently, so run the check before opening the pull request:

```bash
./gradlew checkLangParity
```

It needs no Minecraft and answers in a few seconds. It names the file and line for a missing
or unknown key, a duplicate, an empty value, a bare `%`, a `%s` that went astray, a UTF-8 BOM
(Windows editors add one by default), a file saved as anything other than UTF-8, and a file
name that is not a lowercase locale code. The CI runs it on every pull request.

For anything touching the tracking rules, read `CLAUDE.md` first. It records every design
decision and, more usefully, the traps already hit — several of them cost hours to find.

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

---

## Two rules that are not negotiable

**`core` must never import Minecraft.** `gradlew :core:check` fails the build if it does,
naming the file and the line. That boundary is what will make a port to a newer Minecraft
version cheap, and it only survives if it is enforced rather than remembered.

**Durations are measured with `Clock.elapsedMillis()`, never with `currentTimeMillis()`.**
The wall clock can jump — an NTP correction, a manual change, a dual-boot machine's RTC skew
— and a forward jump once credited a player with an hour they never played. The wall clock
is for dates only. `CLAUDE.md` §2.3 has the full story.

# Actually Played

**How long have you *actually* played?**

Minecraft counts the minutes the game was open. It cannot tell an hour spent building apart
from an hour spent running a farm while you watched a video. Actually Played can.

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

Actually Played reads the commands you send. They are zero while you are being carried.
**A player drifting in a water current is marked AFK once the threshold passes, as they
should be.**

---

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/) for 1.12.2
2. Drop `actuallyplayed-1.12.2-x.y.z.jar` into `.minecraft/mods/`
3. Start the game

No dependencies. Nothing to install server-side.

---

## Usage

Three ways in:

- **Esc → Statistics → the "Playtime" button** in the top right
- **`/played`** (or `/ap`) prints the same figures to chat
- **A key of your choice** — the binding is registered unbound, under
  *Options → Controls → Actually Played*

The screen shows the destination you are in, and only that one:

- **Current session** — your state (playing / AFK for X), played time, AFK time
- **Total here** — the running total on this server or world, with the percentage actually
  played (green above 80 %, red below 40 %)
- **Details** — first seen, session count, average duration, longest session

Every server and every world keeps its own history. It is waiting for you when you return.

**`/played reset`** clears the destination you are on, after asking for confirmation. It
never touches the others; to wipe everything, delete the data file with the game closed.

---

## Configuration

Two ways to change the settings:

- **In game**: mod list → *Actually Played* → **Config**. Changes apply immediately, with
  no restart.
- **By file**: `.minecraft/config/actuallyplayed/actuallyplayed.cfg`

| Option | Default | What it does |
|---|---|---|
| `afkThresholdSeconds` | `300` | Inactivity after which the counter stops. The elapsed idle time is removed from your played total and moved to AFK. |
| `minSessionSeconds` | `30` | Shorter sessions are discarded entirely, so brief visits do not clutter your statistics. `0` keeps everything. |
| `autosaveIntervalSeconds` | `60` | How often the data file is written. This also bounds how much of a running session a crash can cost you. |
| `retentionDays` | `90` | How long each session is kept in full detail. Older ones are merged into monthly summaries — **no playtime is ever lost**, only the detail. |
| `debugLogging` | `false` | Logs every played ↔ AFK transition. Useful to check the detection; the mod is silent by default. |

---

## Your data

Everything lives in `.minecraft/config/actuallyplayed/playtime.json`, as readable,
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

## Contributing

Bug reports, translations and pull requests are welcome. Everything you need to build the
mod, set up an IDE and run the tests is in **[CONTRIBUTING.md](CONTRIBUTING.md)**.

**Translating the mod** is the easiest way to help: copy
`forge-1.12/src/main/resources/assets/actuallyplayed/lang/en_us.lang`, rename it to your
locale (`de_de.lang`, `es_es.lang`, ...), and translate the right-hand side of each line.
Two rules: keep the keys untouched, and never put a bare `%` in a value — Minecraft runs
every translation through a formatter and a lone percent sign breaks it.

---

## Licence

[MIT](LICENSE). You may use, modify, bundle it in a modpack and redistribute it, including
commercially, as long as you keep the copyright notice.

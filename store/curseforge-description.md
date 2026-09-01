# Actually Played

Minecraft tells you how long your game has been open. It cannot tell you how much of that time you were really playing.

This mod can.

Actually Played is a client-side playtime tracker that separates real play time from AFK time, on every server you join and every world you open.

![Actually Played](https://i.imgur.com/SySbM82.png)

## The two numbers

For every server you join and every world you play, the mod keeps two numbers.

**Played** is the time you were really there, doing things. **AFK** is the time your game was open but you had stopped.

It also works out how much of your time was real playing. Spend 10 hours on a server with 4 of them AFK, and it tells you 60%.

## How it knows you are playing

Twenty times a second, the mod looks at your keyboard and your mouse. Are you walking? Turning your head? Clicking? Typing in chat? Sorting a chest?

If yes, you are playing.

Touch nothing at all for 5 minutes and the mod decides you have gone. Then it does something you might not expect: it goes back, takes those 5 minutes off your played time, and moves them to AFK instead. You were not really playing during them, so they should not count.

Click over to another window and it switches to AFK right away.

## Moving is not the same as playing

The mod never looks at where you are standing. Only at what you press.

That matters, because plenty of things move you while you do nothing. Water pushes you. Minecarts carry you. Horses walk. You fall.

So you can leave yourself going round a rail loop all night. In the morning the mod will tell you the truth: you were not playing.

## What you get

*   **Played and AFK, side by side** — for the server or world you are in right now
*   **A live session counter** — counting up while you watch it
*   **Your real ratio** — green when it is high, red when it is low
*   **The history of the place** — the day you first came, how many times you have been, your longest stay
*   **One entry per destination** — every server and every world keeps its own record, still there when you come back
*   **Nothing to learn** — no command to remember, no HUD in the way, no message in chat

## Where to look

Press **Esc**, click **Statistics**, then click the **Playtime** button in the top right corner.

You can also bind a key for it in **Options → Controls** (every version except 1.7.10), or type **/played** (1.12.2).

## Compatibility

| Minecraft | Loader | Side |
| --- | --- | --- |
| 1.7.10 | Forge | Client |
| 1.12.2 | Forge | Client |
| 1.16.5 | Forge, Fabric | Client |
| 1.20.1 | Forge, Fabric | Client |
| 1.21.1 | NeoForge, Fabric | Client |

**Client-side only.** The server does not need the mod, and does not need to know it exists — it works on vanilla servers, on modded servers, and on servers you have no control over. The Fabric builds need Fabric API; nothing else is required anywhere.

Available in 27 languages.

Realms is not tracked: a Realms connection gives the client no stable address to file the time under.

## Settings

*   **AFK threshold** — 5 minutes by default. Set it to whatever fits how you play.
*   **Shortest session kept** — 30 seconds by default. Join a server, change your mind, leave: it is not recorded, so brief visits never clutter your list.
*   **Autosave interval** — 60 seconds by default. It is also the most a crash can cost you: the session in progress is written down as it runs, and picked back up next time you launch.
*   **Detailed history** — 90 days by default. Older sessions are merged into monthly summaries. No playtime is ever lost, only the day-by-day detail.
*   **Transition logging** — off by default. Turn it on to watch the mod decide.

On 1.12.2 the settings have a screen in the mod list, and every change applies immediately. On the other versions they live in `config/actuallyplayed.properties`.

## Your data

Everything stays on your computer, in one readable JSON file under `config/actuallyplayed/`. Nothing is uploaded, nothing is shared, no account, no website, no telemetry. Open the file yourself if you want to.

It is written atomically, so a crash mid-save leaves you with the old file rather than a broken one — and a file it cannot read is set aside, never deleted.

## Links

*   **Source code:** https://github.com/programgames/actuallyplayed
*   **Report a bug or ask for a feature:** https://github.com/programgames/actuallyplayed/issues
*   **Licence:** MIT
*   **Modpacks:** yes, go ahead. No permission needed, no credit required (though it is always welcome).

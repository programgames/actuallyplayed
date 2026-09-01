# Releasing

From a finished change to eight jars on CurseForge. Two of these steps are yours, the rest
is machinery.

The reasoning behind each rule is in `CLAUDE.md` section 12; this file is the procedure.

---

## 1. Write the changelog entry

```bash
python tools/release.py 1.3.0 --skeleton
```

That puts empty `Added` / `Changed` / `Fixed` headings at the top of `CHANGELOG.md`. Fill in
what applies, delete the rest.

Write it for a player, not for a reviewer: what changed for them, and what it fixes. This is
the one part of a release nothing can generate, which is why the script refuses to go on
without it. It ends up in three places at once — the GitHub release body, the changelog of
each of the eight CurseForge files, and the note in `update.json` that the in-game update
checker shows.

## 2. Prepare the version

```bash
python tools/release.py 1.3.0 --check   # says what it would do, writes nothing
python tools/release.py 1.3.0           # does it
```

It sets **all three** version files, adds a block and both `promos` entries to `update.json`
for every Minecraft version, derives the update note from what you wrote in step 1, and then
checks its own work.

Both of these have gone wrong by hand:

- the 1.2.0 tag went out with `legacy-1.7/gradle.properties` left at 1.1.0, and the release
  workflow refused it;
- a missing `promos` pair fails silently — the update checker just reports "up to date"
  forever, for everyone on that Minecraft version.

Read the diff, then commit.

## 3. Tag

```bash
git tag -a 1.3.0 -m "Actually Played 1.3.0" && git push origin 1.3.0
```

`release.yml` builds every Minecraft version in parallel, each on the JDK it needs, and
gathers the jars into a **draft** GitHub release. `fail-fast` is on: a release quietly
missing a version is worse than no release.

The tag carries the mod version alone. It cannot match the jar names, because one tag
produces eight of them.

## 4. Publish the GitHub release

Read the draft, then press **Publish release**.

Draft on purpose. Publishing is a decision, not a side effect of pushing a tag — and past
this point nothing is quietly reversible.

## 5. Upload to CurseForge

**Actions → Publish to CurseForge → Run workflow**, with the tag (`1.3.0`) as the input.

Eight jobs, one per jar, each carrying its own game version, loader and Java version. A file
uploaded to CurseForge is public the moment it lands: there is no draft state to take it back
from, which is why this is a separate, deliberate click.

It cannot be made to run on its own from step 4. An event attributed to `GITHUB_TOKEN` never
starts another workflow, and `release.yml` creates the draft with that token, so
`on: release: published` never fires however the button is pressed. Fixing that would mean
creating the draft with a personal access token — one more secret to store and rotate, to
save one click a few times a year.

`fail-fast` is **off** here, unlike step 3. Uploading is not atomic: when one file fails the
earlier ones are already public, and stopping would leave the project half-published.

---

## Afterwards

- Check the eight files appear on the CurseForge page. They go through a scan first, so give
  it a few minutes. **The manual upload of 1.1.0 dropped a jar without saying so** — Fabric
  1.20.1 players had nothing to download for two days.
- The mod list shows "update available" to players on the previous version at their next
  launch, through `update.json`. That file is read from `raw.githubusercontent.com`, so the
  repository has to stay public for it to work at all.

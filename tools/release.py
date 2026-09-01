#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prepares a release: the version files, and update.json, from the changelog you wrote.

    python tools/release.py 1.3.0            # do it
    python tools/release.py 1.3.0 --check    # say what would change, touch nothing
    python tools/release.py 1.3.0 --skeleton # write an empty changelog entry to fill in

It does the mechanical half of section 12 of CLAUDE.md, and only that half. What goes in
the changelog is yours to write; everything downstream of it is copying, and copying by
hand is what went wrong twice:

  - **1.2.0 was tagged with two of the three version files bumped.** legacy-1.7 kept 1.1.0,
    the release workflow refused the tag, and the tag had to be deleted and pushed again.
    Three files exist because three separate Gradle builds do, and nothing but attention
    kept them in step.
  - **The manual CurseForge upload of 1.1.0 silently dropped a jar.** Fabric 1.20.1 players
    had nothing to download for two days. That half is now the publish workflow's matrix;
    this script covers the other half.

update.json is the quietest of the three. A missing block or promos pair does not fail: the
in-game update checker simply reports "up to date" forever, for everyone on that Minecraft
version, with no error anywhere.

The changelog comes first because it is the one part a script cannot write. Run with
--skeleton to get the headings, fill them in, then run for real.
"""

from __future__ import print_function

import collections
import datetime
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# The three builds, and the key each one spells the version with. They are separate Gradle
# builds -- ForgeGradle 2.3 on JDK 8, RetroFuturaGradle, and the modern multi-loader one --
# which is why one number lives in three files.
VERSION_FILES = (
    ("gradle.properties", "modVersion"),
    ("legacy-1.7/gradle.properties", "modVersion"),
    ("modern/gradle.properties", "mod_version"),
)

SEMVER = re.compile(r"^\d+\.\d+\.\d+$")


class Abort(Exception):
    pass


def read(path):
    return io.open(os.path.join(ROOT, path), encoding="utf-8").read()


def write(path, text):
    io.open(os.path.join(ROOT, path), "w", encoding="utf-8", newline="\n").write(text)


# --- the changelog ------------------------------------------------------------------------

def changelog_section(version):
    """The body of the `## [version]` section, or None when there is no such section."""
    text = read("CHANGELOG.md")
    start = re.search(r"^## \[%s\][^\n]*$" % re.escape(version), text, re.M)
    if not start:
        return None
    rest = text[start.end():]
    nxt = re.search(r"^## \[", rest, re.M)
    return (rest[:nxt.start()] if nxt else rest).strip("\n")


def release_note(body):
    """Turns the changelog section into the plain text update.json carries.

    Minecraft renders that string in a tooltip with no markdown, so the bullets, the bold
    markers and the headings all have to go. Sub-bullets are folded into their parent's
    sentence rather than dropped: they usually carry the part that explains the change.
    """
    lines = []
    for raw in body.split("\n"):
        line = raw.rstrip()
        if not line or line.startswith("#"):
            continue
        stripped = line.lstrip()
        indented = len(line) - len(stripped)
        if not stripped.startswith(("- ", "* ")):
            # A continuation of the bullet above, wrapped by the author.
            if lines:
                lines[-1] += " " + stripped
            continue
        item = re.sub(r"\*\*|`|_", "", stripped[2:]).strip()
        if indented and lines:
            lines[-1] += " " + item
        else:
            lines.append(item)
    return "\n".join(lines)


def write_skeleton(version):
    if changelog_section(version) is not None:
        raise Abort("CHANGELOG.md already has an entry for %s." % version)
    text = read("CHANGELOG.md")
    anchor = re.search(r"^## \[", text, re.M)
    if not anchor:
        raise Abort("CHANGELOG.md has no version sections to insert before.")
    entry = (
        "## [%s] - %s\n\n"
        "One sentence on what this release is for.\n\n"
        "### Added\n\n- \n\n"
        "### Changed\n\n- \n\n"
        "### Fixed\n\n- \n\n" % (version, datetime.date.today().isoformat()))
    write("CHANGELOG.md", text[:anchor.start()] + entry + text[anchor.start():])
    print("Wrote a skeleton entry for %s at the top of CHANGELOG.md." % version)
    print("Fill it in -- delete the headings you do not need -- then run this again without")
    print("--skeleton.")


# --- the version files --------------------------------------------------------------------

def current_versions():
    found = collections.OrderedDict()
    for path, key in VERSION_FILES:
        match = re.search(r"^%s=(.+)$" % re.escape(key), read(path), re.M)
        if not match:
            raise Abort("%s has no %s= line." % (path, key))
        found[path] = match.group(1).strip()
    return found


def bump_versions(version, dry_run):
    before = current_versions()
    distinct = set(before.values())
    if len(distinct) > 1 and distinct != {version}:
        # Not fatal on its own -- this may be the very run that repairs it -- but it means
        # the tree was left in the state that broke the 1.2.0 tag, so say so out loud.
        print("Note: the three files did not agree before this run:")
        for path, was in before.items():
            print("  %-32s %s" % (path, was))
    for path, key in VERSION_FILES:
        if before[path] == version:
            continue
        print("  %-32s %s -> %s" % (path, before[path], version))
        if not dry_run:
            text = read(path)
            write(path, re.sub(r"^%s=.+$" % re.escape(key), "%s=%s" % (key, version),
                               text, count=1, flags=re.M))


# --- update.json --------------------------------------------------------------------------

def update_json(version, note, dry_run):
    data = json.loads(read("update.json"), object_pairs_hook=collections.OrderedDict)
    minecraft = [k for k in data if k not in ("homepage", "promos")]
    if not minecraft:
        raise Abort("update.json lists no Minecraft versions.")
    for mc in minecraft:
        key = "%s-%s" % (mc, version)
        state = "updated" if key in data[mc] else "added"
        data[mc][key] = note
        data["promos"]["%s-latest" % mc] = key
        data["promos"]["%s-recommended" % mc] = key
        print("  %-8s %s (%s, and both promos)" % (mc, key, state))
    if not dry_run:
        write("update.json", json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    return minecraft


# --- checks that run whatever the mode ------------------------------------------------------

def verify(version, minecraft):
    problems = []
    for path, was in current_versions().items():
        if was != version:
            problems.append("%s still says %s" % (path, was))
    data = json.loads(read("update.json"), object_pairs_hook=collections.OrderedDict)
    for mc in minecraft:
        key = "%s-%s" % (mc, version)
        if key not in data.get(mc, {}):
            problems.append("update.json has no %s block for %s" % (mc, key))
        for promo in ("latest", "recommended"):
            if data["promos"].get("%s-%s" % (mc, promo)) != key:
                problems.append("update.json promos %s-%s does not point at %s"
                                % (mc, promo, key))
    return problems


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    flags = set(a for a in argv[1:] if a.startswith("--"))
    unknown = flags - {"--check", "--skeleton"}
    if unknown:
        raise Abort("Unknown option: %s" % ", ".join(sorted(unknown)))
    if len(args) != 1 or not SEMVER.match(args[0]):
        raise Abort("Usage: python tools/release.py <version>  (for example 1.3.0)")
    version = args[0]

    if "--skeleton" in flags:
        write_skeleton(version)
        return 0

    body = changelog_section(version)
    if body is None:
        raise Abort(
            "CHANGELOG.md has no entry for %s.\n"
            "It is the one part of a release a script cannot write, and update.json is\n"
            "derived from it. Run with --skeleton for the headings, fill them in, then run\n"
            "this again." % version)
    note = release_note(body)
    if not note:
        raise Abort("The %s entry in CHANGELOG.md has no content yet." % version)

    dry_run = "--check" in flags
    print("Release %s%s\n" % (version, "  (--check: nothing will be written)" if dry_run else ""))
    print("Version files:")
    bump_versions(version, dry_run)
    print("\nupdate.json:")
    minecraft = update_json(version, note, dry_run)
    print("\nThe note players will see:\n")
    for line in note.split("\n"):
        print("    " + line)

    if dry_run:
        return 0

    problems = verify(version, minecraft)
    if problems:
        print("\nSomething is still wrong:")
        for problem in problems:
            print("  - " + problem)
        return 1

    print("\nAll three version files and all %d Minecraft blocks agree on %s."
          % (len(minecraft), version))
    print("Next: read the diff, commit, then")
    print("  git tag -a %s -m \"Actually Played %s\" && git push origin %s"
          % (version, version, version))
    print("See RELEASING.md for what happens after that.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv))
    except Abort as abort:
        print(abort, file=sys.stderr)
        sys.exit(1)

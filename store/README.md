# Store listings

The text of the CurseForge and Modrinth project pages, kept here so that a change to what
the mod claims travels with the change to what it does.

| File | Where it goes |
| --- | --- |
| `summary.txt` | The short blurb, on both platforms. 150 characters. CurseForge truncates this field at 160; Modrinth allows 256. |
| `curseforge-description.md` | The CurseForge description tab. |
| `modrinth-description.md` | The Modrinth description tab. |

The two descriptions are the same text with three deliberate differences: Modrinth drops
the H1, because the page prints the project title directly above the description, and it
drops the links block, because the sidebar already carries the source, the issue tracker
and the licence -- only the modpack permission survives, which has no field of its own.
CurseForge keeps the full links block, since the platform asks for outside links to sit at
the bottom of the description.

`summary.txt` must stay in step with `description` in `modern/gradle.properties` and with
the `description` field of both `mcmod.info` files, which is what launchers display in the
installed-mod list.

CurseForge's editor is not plain markdown. The compatibility table is the part most likely
to survive badly; check the rendering after pasting.

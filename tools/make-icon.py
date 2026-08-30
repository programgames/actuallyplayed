#!/usr/bin/env python3
"""Renders the mod's icon from the pixel grid below.

The grid *is* the artwork. Edit it, run this, look at the result, edit again -- the way pixel
art is drawn anywhere else, with the script standing in for the editor's canvas.

    python tools/make-icon.py

Writes icon-512.png, icon-400.png and icon-128.png beside itself, which are the sizes Modrinth,
CurseForge and the in-game mod list ask for. Nearest-neighbour scaling throughout: anything
smoother turns pixel art into mush.

Requires Pillow (`pip install pillow`).
"""

from PIL import Image

# --- the palette ---------------------------------------------------------------------------
#
# One character per colour, so the grid below stays readable as a picture. RGBA, and the
# background is fully transparent: a store page may put the icon on any colour.

PALETTE = {
    '.': (0, 0, 0, 0),          # transparent
    'o': (38, 38, 38, 255),     # outline
    'k': (70, 70, 70, 255),     # the frame's caps, a shade lighter than the outline
    'w': (245, 245, 245, 255),  # the glass
    'y': (230, 190, 60, 255),   # sand still to fall
    'G': (120, 190, 95, 255),   # sand already through, tinted with the mod's "playing" green
}

# --- the artwork ---------------------------------------------------------------------------
#
# An hourglass, 16 x 16 -- the size Minecraft draws everything at. Each character is one pixel.
#
#   rows  1-2   the top cap
#   rows  3-7   the upper chamber: mostly empty glass, a little sand left to fall
#   rows  8-9   the neck, with a grain just below it
#   rows 10-14  the lower chamber, filling with sand that has already run through
#   rows 15-16  the bottom cap
#
# Three deliberate choices, and each is one line of the grid away from being undone:
#
#   - There is more sand below than above. A balanced hourglass says "time"; a bottom-heavy one
#     says "time already spent", which is what the mod measures.
#   - The fallen sand is green, the same green the screen uses for "playing". The two halves of
#     the icon are then the two numbers the mod reports.
#   - A single grain sits below the neck, so the picture reads as running rather than stopped.

GRID = """
..oooooooooooo..
..okkkkkkkkkko..
..owwwwwwwwwwo..
...owwwwwwwwo...
....owyyyywo....
.....oyyyyo.....
......oyyo......
.......oo.......
.......oo.......
......oy.o......
.....oGGGGo.....
....oGGGGGGo....
...oGGGGGGGGo...
..oGGGGGGGGGGo..
..okkkkkkkkkko..
..oooooooooooo..
"""


def load_grid():
    rows = GRID.strip().splitlines()
    width = len(rows[0])
    for index, row in enumerate(rows):
        if len(row) != width:
            raise SystemExit(
                "Row %d is %d characters wide, the first is %d. Every row must match."
                % (index + 1, len(row), width))
        unknown = set(row) - set(PALETTE)
        if unknown:
            raise SystemExit(
                "Row %d uses %s, which the palette does not define."
                % (index + 1, ", ".join(sorted(unknown))))
    return rows


def render(rows):
    image = Image.new('RGBA', (len(rows[0]), len(rows)))
    image.putdata([PALETTE[c] for row in rows for c in row])
    return image


def main():
    rows = load_grid()
    art = render(rows)
    print("Grid: %d x %d" % (art.width, art.height))

    for size in (512, 400, 128):
        # NEAREST keeps the pixels square. Any interpolation blurs the edges, which is exactly
        # what pixel art must not have.
        art.resize((size, size), Image.NEAREST).save('icon-%d.png' % size)
        print("Wrote icon-%d.png" % size)


if __name__ == '__main__':
    main()

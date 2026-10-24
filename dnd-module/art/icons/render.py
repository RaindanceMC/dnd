"""
Renders every icon of Rain's Adventurers at 32x32 from the hand-drawn silhouettes in glyphs.py.

    python3 render.py [contact-sheet.png]

  1. Scale2x doubles each silhouette, rounding its diagonals the way a pixel artist would.
  2. Every region gets a height: how far a pixel is from the region's edge. That makes a dome, and its slope is
     lit from the top left — the shapes read as sculpted, not flat.
  3. Light picks a tone from a hue-shifted ramp: cool, saturated shadows, warm highlights.
  4. Outlines take the colour of what they outline, a little lighter where the light hits them.
  5. Spells, and anything magical, get a soft glow in their own colour.
"""
import colorsys
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

from glyphs import G
from recipes import PALETTES, RECIPES

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules", "dnd", "pack",
                    "assets", "rainsadventurers", "textures", "item")
SIZE = 32
GLOWING = ("spell/", "subclass/wild_magic", "subclass/evocation", "subclass/draconic", "subclass/life",
           "metamagic/", "feature/wild_magic", "feature/tides", "feature/overchannel", "feature/font_of_magic",
           "feature/arcane_recovery", "feature/preserve_life", "feature/turn_undead", "feature/dragon")
LIGHT = (-0.55, -0.7, 0.9)


def hexrgb(text):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4))


def ramp(palette, steps=8):
    """Eight tones from the palette's darkest to its highlight, shadows cooler, highlights warmer."""
    dark, mid, light, high = (hexrgb(c) for c in palette)
    anchors = [tuple(int(v * 0.55) for v in dark), dark, mid, light, high]
    tones = []
    for i in range(steps):
        t = i / (steps - 1) * (len(anchors) - 1)
        a, b = anchors[int(math.floor(t))], anchors[min(len(anchors) - 1, int(math.floor(t)) + 1)]
        f = t - math.floor(t)
        rgb = [a[k] + (b[k] - a[k]) * f for k in range(3)]
        h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in rgb))
        shift = (0.5 - i / (steps - 1)) * 0.05
        h = (h + shift) % 1.0
        s = min(1.0, s * (1.15 if i < steps / 2 else 0.95))
        tones.append(tuple(int(v * 255) for v in colorsys.hls_to_rgb(h, l, s)))
    return tones


def parse(glyph):
    rows = glyph.strip("\n").split("\n")
    width = max(len(row) for row in rows)
    grid = [["."] * 16 for _ in range(16)]
    top, left = (16 - len(rows)) // 2, (16 - width) // 2
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if 0 <= top + y < 16 and 0 <= left + x < 16:
                grid[top + y][left + x] = char
    return grid


def scale2x(grid):
    n = len(grid)
    out = [["."] * (n * 2) for _ in range(n * 2)]
    get = lambda x, y: grid[y][x] if 0 <= x < n and 0 <= y < n else "."
    for y in range(n):
        for x in range(n):
            p = get(x, y)
            a, b, c, d = get(x, y - 1), get(x + 1, y), get(x - 1, y), get(x, y + 1)
            e0 = a if (c == a and c != d and a != b) else p
            e1 = b if (a == b and a != c and b != d) else p
            e2 = c if (d == c and d != b and c != a) else p
            e3 = d if (b == d and b != a and d != c) else p
            out[2 * y][2 * x], out[2 * y][2 * x + 1] = e0, e1
            out[2 * y + 1][2 * x], out[2 * y + 1][2 * x + 1] = e2, e3
    return out


def region(char):
    return {"#": 1, "x": 1, "+": 2, "o": 3, "*": 3}.get(char, 0)


def heights(grid):
    """Distance of each pixel from the edge of its own region, capped: a rounded dome per region."""
    n = len(grid)
    big = 99
    dist = [[0 if grid[y][x] == "." else big for x in range(n)] for y in range(n)]
    for y in range(n):
        for x in range(n):
            if grid[y][x] == ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < n and 0 <= ny < n) or region(grid[ny][nx]) != region(grid[y][x]):
                    dist[y][x] = 1
    for _ in range(4):
        for y in range(n):
            for x in range(n):
                if dist[y][x] > 1:
                    best = min(dist[ny][nx] for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))
                               if 0 <= nx < n and 0 <= ny < n and region(grid[ny][nx]) == region(grid[y][x]))
                    dist[y][x] = min(dist[y][x], best + 1)
    return [[min(d, 4) for d in row] for row in dist]


def render(name):
    glyph, main, second = RECIPES[name]
    grid = scale2x(parse(G[glyph]))
    height = heights(grid)
    tones = ramp(PALETTES[main])
    tones2 = ramp(PALETTES[second])
    lx, ly, lz = LIGHT
    norm = math.sqrt(lx * lx + ly * ly + lz * lz)
    lx, ly, lz = lx / norm, ly / norm, lz / norm
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()
    h = lambda x, y: height[y][x] if 0 <= x < SIZE and 0 <= y < SIZE else 0
    for y in range(SIZE):
        for x in range(SIZE):
            char = grid[y][x]
            if char == ".":
                continue
            gx = (h(x + 1, y) - h(x - 1, y)) * 0.55
            gy = (h(x, y + 1) - h(x, y - 1)) * 0.55
            nx, ny, nz = -gx, -gy, 1.0
            nn = math.sqrt(nx * nx + ny * ny + nz * nz)
            light = max(0.0, (nx * lx + ny * ly + nz * lz) / nn)
            light = light * 0.8 + 0.12 + (0.06 if height[y][x] >= 3 else 0)
            index = max(1, min(7, int(round(light * 7))))
            if char == "#":
                colour = tones[index]
            elif char == "+":
                colour = tones2[index]
            elif char == "o":
                colour = (tones2 if second != main else tones)[min(7, index + 2)]
            elif char == "*":
                colour = (tones2 if second != main else tones)[7]
            else:
                colour = tones[0]
            px[x, y] = colour + (255,)
    outline = [[None] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if grid[y][x] != ".":
                continue
            neighbours = [(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                          if 0 <= x + dx < SIZE and 0 <= y + dy < SIZE and grid[y + dy][x + dx] != "."]
            if neighbours:
                lit = any(nx > x or ny > y for nx, ny in neighbours)
                source = grid[neighbours[0][1]][neighbours[0][0]]
                ramp_of = tones2 if source == "+" else tones
                base = ramp_of[1] if lit else ramp_of[0]
                outline[y][x] = tuple(int(v * (0.8 if lit else 0.55)) for v in base)
    for y in range(SIZE):
        for x in range(SIZE):
            if outline[y][x]:
                px[x, y] = outline[y][x] + (255,)
    if name.startswith(GLOWING):
        glow = tones[6]
        for y in range(SIZE):
            for x in range(SIZE):
                if px[x, y][3]:
                    continue
                near = min((abs(dx) + abs(dy) for dx in range(-3, 4) for dy in range(-3, 4)
                            if 0 <= x + dx < SIZE and 0 <= y + dy < SIZE and outline[y + dy][x + dx]), default=9)
                if near <= 3:
                    px[x, y] = glow + ({1: 90, 2: 48, 3: 20}[near],)
    return image


def main():
    os.makedirs(PACK, exist_ok=True)
    names = sorted(RECIPES)
    for name in names:
        target = os.path.join(PACK, name + ".png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        render(name).save(target)
    columns, cell = 10, 112
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * cell, rows * (cell + 16)), (30, 32, 38, 255))
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        x, y = (index % columns) * cell, (index // columns) * (cell + 16)
        sheet.alpha_composite(render(name).resize((96, 96), Image.NEAREST), (x + 8, y + 4))
        draw.text((x + 4, y + 100), name.split("/")[-1].replace("_", " ")[:18], fill=(220, 220, 220, 255),
                  font=ImageFont.load_default())
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "contact-sheet.png")
    sheet.save(out)
    print(len(names), "icons at 32px ->", out)


if __name__ == "__main__":
    main()

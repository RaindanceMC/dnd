"""
Paints the character sheet's background: a 176x222 image the size of a six-row chest window, drawn by the window
title through a font glyph. Every frame sits exactly under a slot SheetMenu puts a button in.

    python3 sheet.py
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules", "dnd", "pack",
                   "assets", "rainsadventurers", "textures", "font", "sheet.png")
W, H = 176, 222
SLOT = 18
TOP = (7, 17)          # the first chest slot's outer corner
INVENTORY_TOP = 138    # the player's inventory, three rows, then the hotbar at 196 (measured from generic_54)

rng = random.Random(5)
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()


def put(x, y, rgb, a=255):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = tuple(int(max(0, min(255, c))) for c in rgb) + (a,)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def noise(x, y, scale=1.0):
    return (math.sin(x * 0.31 + y * 0.17) * 0.5 + math.sin(x * 0.07 - y * 0.23) * 0.5) * scale + rng.uniform(-1, 1) * scale


PARCHMENT = (226, 205, 160)
PARCHMENT_DARK = (190, 160, 110)
INK = (74, 50, 32)
GOLD = (214, 168, 64)
GOLD_LIGHT = (250, 220, 130)
GOLD_DARK = (130, 92, 28)
LEATHER = (92, 56, 34)
LEATHER_DARK = (58, 34, 20)
WAX = (150, 30, 36)

# ---- the leather binding everything sits on
for y in range(H):
    for x in range(W):
        n = noise(x, y, 6)
        put(x, y, mix(LEATHER, LEATHER_DARK, 0.3 + (y / H) * 0.3 + n / 40))

# ---- the parchment page, rows 0 to 5 of the chest
page = (3, 3, W - 4, 133)
for y in range(page[1], page[3]):
    for x in range(page[0], page[2]):
        edge = min(x - page[0], y - page[1], page[2] - 1 - x, page[3] - 1 - y)
        burn = max(0.0, 1.0 - edge / 6.0) ** 1.6
        t = 0.12 + noise(x, y, 0.06) + burn * 0.75
        colour = mix(PARCHMENT, PARCHMENT_DARK, max(0, min(1, t)))
        if edge == 0 and rng.random() < 0.55:
            colour = mix(colour, (60, 36, 18), 0.7)
        put(x, y, colour)
# a faint compass rose behind the ledger, as if pressed into the page
rx, ry = 88, 78
for y in range(page[1], page[3]):
    for x in range(page[0], page[2]):
        dx, dy = x - rx, y - ry
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        star = d < 34 * (0.35 + 0.65 * abs(math.cos(a * 2)) ** 6)
        ring = abs(d - 30) < 0.6 or abs(d - 26) < 0.5
        if star or ring:
            r, g, b, _ = px[x, y]
            put(x, y, mix((r, g, b), INK, 0.09 if star else 0.14))
# coffee-ring stains
for sx, sy, sr in ((150, 112, 9), (26, 30, 6)):
    for y in range(sy - sr - 1, sy + sr + 2):
        for x in range(sx - sr - 1, sx + sr + 2):
            if abs(math.hypot(x - sx, y - sy) - sr) < 0.8 and rng.random() < 0.8:
                r, g, b, _ = px[x, y]
                put(x, y, mix((r, g, b), (120, 80, 40), 0.18))
# faint ruled lines, as on a real sheet
for y in range(page[1] + 16, page[3] - 3, 6):
    for x in range(page[0] + 6, page[2] - 6):
        if rng.random() < 0.8:
            r, g, b, _ = px[x, y]
            put(x, y, mix((r, g, b), INK, 0.06))

# ---- the title band
for y in range(4, 15):
    for x in range(6, W - 7):
        r, g, b, _ = px[x, y]
        put(x, y, mix((r, g, b), PARCHMENT_DARK, 0.35))
for x in range(6, W - 7):
    put(x, 15, INK, 200)
    put(x, 16, GOLD_DARK, 160)


def slot_origin(column, row):
    return TOP[0] + column * SLOT, TOP[1] + row * SLOT


def frame(column, row, style):
    """The frame under one slot: a recessed, round-cornered inset, lit from the top left."""
    x0, y0 = slot_origin(column, row)
    for y in range(y0, y0 + SLOT):
        for x in range(x0, x0 + SLOT):
            lx, ly = x - x0, y - y0
            corner = (lx in (0, SLOT - 1)) and (ly in (0, SLOT - 1))
            if corner:
                continue
            border = lx in (0, SLOT - 1) or ly in (0, SLOT - 1)
            shade_in = lx == 1 or ly == 1
            light_in = lx == SLOT - 2 or ly == SLOT - 2
            if style == "gold":
                if border:
                    put(x, y, GOLD_LIGHT if (lx == 0 or ly == 0) else GOLD_DARK)
                elif shade_in:
                    put(x, y, mix(PARCHMENT_DARK, INK, 0.55))
                elif light_in:
                    put(x, y, mix(PARCHMENT, (255, 245, 220), 0.3))
                else:
                    put(x, y, mix(PARCHMENT_DARK, PARCHMENT, 0.35 + noise(x, y, 0.04)))
            elif style == "ink":
                if border:
                    put(x, y, mix(INK, PARCHMENT_DARK, 0.25), 235)
                elif shade_in:
                    put(x, y, mix(PARCHMENT_DARK, INK, 0.35))
                elif light_in:
                    put(x, y, mix(PARCHMENT, (255, 248, 230), 0.4))
                else:
                    put(x, y, mix(PARCHMENT, PARCHMENT_DARK, 0.45 + noise(x, y, 0.04)))
            elif style == "chrome":
                if border:
                    put(x, y, LEATHER_DARK)
                elif shade_in:
                    put(x, y, mix(LEATHER_DARK, (0, 0, 0), 0.3))
                else:
                    put(x, y, mix(LEATHER, LEATHER_DARK, 0.25 + noise(x, y, 0.05)))
    if style == "gold":
        for cx, cy in ((x0, y0), (x0 + SLOT - 1, y0), (x0, y0 + SLOT - 1), (x0 + SLOT - 1, y0 + SLOT - 1)):
            put(cx, cy, GOLD)


def seal(column, row):
    """A round wax seal under a door button, with a ridged rim."""
    x0, y0 = slot_origin(column, row)
    cx, cy = x0 + 8.5, y0 + 8.5
    for y in range(y0 - 1, y0 + SLOT + 1):
        for x in range(x0 - 1, x0 + SLOT + 1):
            d = math.hypot(x - cx, y - cy)
            wobble = 9.6 + math.sin(math.atan2(y - cy, x - cx) * 9) * 0.6
            if d > wobble:
                continue
            light = ((cx - x) + (cy - y)) / 18
            if d > wobble - 1.6:
                colour = mix(WAX, (40, 8, 10), 0.45 - light * 0.4)
            else:
                colour = mix(WAX, (255, 140, 130), 0.1 + light * 0.25 + noise(x, y, 0.03))
            put(x, y, colour)


# Row 0: level, portrait, hit dice
frame(2, 0, "gold")
frame(4, 0, "gold")
frame(6, 0, "gold")
# a laurel under the portrait
x0, y0 = slot_origin(4, 0)
for i in range(7):
    put(x0 - 3 - i, y0 + 9 + i // 2, (70, 110, 50))
    put(x0 + SLOT + 2 + i, y0 + 9 + i // 2, (70, 110, 50))
# Row 1: two triads of abilities, the d20 between them
for column in (1, 2, 3, 5, 6, 7):
    frame(column, 1, "ink")
cx, cy = slot_origin(4, 1)
cx, cy = cx + 9, cy + 9
for y in range(-8, 9):
    for x in range(-8, 9):
        if abs(x) + abs(y) * 0.85 <= 8:
            facet = (x + y) > 0
            edge = abs(x) + abs(y) * 0.85 > 7
            put(cx + x, cy + y, GOLD_DARK if edge else (GOLD if facet else GOLD_LIGHT))
for x, y in ((0, -3), (-2, 2), (2, 2), (0, 0)):
    put(cx + x, cy + y, INK)
put(cx - 1, cy - 1, INK)
put(cx + 1, cy - 1, INK)
# Rows 2 and 3: the ledger of resources and features
lx0, ly0 = slot_origin(1, 2)
lx1, ly1 = slot_origin(8, 4)
for y in range(ly0 - 2, ly1 + 1):
    for x in range(lx0 - 2, lx1 + 1):
        if x in (lx0 - 2, lx1) or y in (ly0 - 2, ly1):
            put(x, y, GOLD_DARK)
for row in (2, 3):
    for column in range(1, 8):
        frame(column, row, "ink")
# Row 4: four wax seals, the doors
for column in (1, 3, 5, 7):
    seal(column, 4)
# Row 5: Core's chrome
for column in range(9):
    frame(column, 5, "chrome")

# ---- a red ribbon bookmark hanging over the top right
for y in range(0, 30):
    for x in range(156, 162):
        if y > 26 and abs(x - 158.5) < (y - 26) * 1.2:
            continue
        shade = 0.25 if x in (156, 161) else 0.0
        put(x, y, mix((168, 30, 40), (60, 8, 12), shade + (0.1 if x == 157 else 0)))

# ---- gold corners on the page
for (x, y), (dx, dy) in (((3, 3), (1, 1)), ((W - 5, 3), (-1, 1)), ((3, 132), (1, -1)), ((W - 5, 132), (-1, -1))):
    for i in range(9):
        put(x + dx * i, y, GOLD if i < 8 else GOLD_DARK)
        put(x, y + dy * i, GOLD if i < 8 else GOLD_DARK)
    for i in range(1, 4):
        put(x + dx * i, y + dy * i, GOLD_LIGHT)

# ---- the inventory: a stitched leather pocket, with a light strip for the "Inventory" label
for y in range(127, 138):
    for x in range(6, W - 7):
        put(x, y, mix(PARCHMENT, PARCHMENT_DARK, 0.35 + noise(x, y, 0.05)))
for row in range(4):
    y0 = INVENTORY_TOP + row * SLOT + (4 if row == 3 else 0)
    for column in range(9):
        x0 = 7 + column * SLOT
        for y in range(y0, y0 + SLOT):
            for x in range(x0, x0 + SLOT):
                border = x in (x0, x0 + SLOT - 1) or y in (y0, y0 + SLOT - 1)
                stitch = (x in (x0 + 1, x0 + SLOT - 2) or y in (y0 + 1, y0 + SLOT - 2)) and (x + y) % 3 == 0
                if border:
                    put(x, y, LEATHER_DARK)
                elif stitch:
                    put(x, y, (200, 170, 120))
                else:
                    put(x, y, mix(LEATHER, LEATHER_DARK, 0.45 + noise(x, y, 0.05)))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
img.resize((W * 3, H * 3), Image.NEAREST).save(os.path.join(HERE, "sheet-preview.png"))
print("sheet ->", OUT)

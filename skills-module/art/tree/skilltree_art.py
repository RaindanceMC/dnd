"""
The adventurer's skill tree looks: rune-ringed iron medallions for the nodes, chain between them (Core's tree.py writes
the font rainsadventurers:tree from them), and a painting per branch behind the page (font rainsadventurers:tree_scene,
U+E000 + the branch's place in DndPack.SCENES). ../pack.py runs paint(); preview.py lays the pages out.

    python3 ../pack.py
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CORE_ART = os.path.join(HERE, "..", "..", "..", "..", "..", "RainsCore", "art", "menus")
sys.path.insert(0, CORE_ART)

from canvas import (Canvas, W, chest_well, inventory_well, hotbar_well, label_y, height, mix, wood, well,  # noqa: E402
                    vignette, shade)
import tree as core_tree  # noqa: E402
import nature_scenes  # noqa: E402

PACK = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules", "skills", "pack")
NS = "rainsadventurers"
ASSETS = os.path.join(PACK, "assets", NS)
ROWS = 6
SCENES = ["vitality", "might", "marksman", "shadow", "arcana", "guardian",
          "warden", "arms", "fury", "trickshot", "ranger", "acrobatics",
          "rogue", "mysticism", "battlemage", "prospector", "delver", "veinbreaker",
          "digger", "treasure_hunter", "archaeologist", "lumberjack", "forester", "feller",
          "anvil_work", "salvager", "forgemaster"] + nature_scenes.SCENES

GOLD = (222, 176, 64)
GOLD_DARK = (122, 82, 22)
GOLD_LIGHT = (255, 236, 160)


# ------------------------------------------------------------------------------------------------ frames and lines

class AdventurerLook(core_tree.Style):
    """Iron medallions with a ring of runes, and chain."""

    def line(self, along, across, lit):
        link = int(along // 5)
        u = (along % 5) - 2.5
        d = abs(across)
        face, dark, light = (GOLD, GOLD_DARK, GOLD_LIGHT) if lit else ((96, 92, 88), (40, 38, 36), (150, 146, 140))
        if link % 2 == 0:
            e = (u / 2.9) ** 2 + (across / 2.3) ** 2
            if 0.42 < e <= 1.0:
                return mix(dark, light, 0.5 - across / 4.6), 255
        elif d <= 0.8 and abs(u) <= 3.0:
            return mix(face, light, 0.4 - across / 3), 255
        if lit and d <= 4.0:
            return (150, 220, 255), int(60 * (1 - d / 4.0))
        return None

    def frame(self, state, x, y):
        r = math.hypot(x, y)
        a = math.atan2(y, x)
        outer, inner = 11.4, 8.2
        rims = {
            "locked": ((88, 88, 92), (40, 40, 44), (130, 130, 136), (26, 26, 30), None),
            "open": ((120, 124, 132), (54, 56, 62), (190, 194, 204), (22, 30, 40), (120, 255, 170)),
            "owned": (GOLD, GOLD_DARK, GOLD_LIGHT, (40, 26, 70), (190, 150, 255)),
            "maxed": ((250, 206, 92), (150, 96, 26), (255, 248, 210), (70, 18, 22), (255, 220, 120)),
            "barred": ((118, 68, 52), (58, 30, 22), (164, 100, 78), (30, 12, 12), None),
        }
        face, dark, light, field, rune = rims[state]
        if r > outer:
            if state == "open" and r <= 12.9:
                return (120, 255, 170), int(200 * (1 - (r - outer) / 1.5))
            if state == "maxed" and r <= 12.9:
                ray = max(0.0, math.cos(a * 8)) ** 6
                if ray > 0.3:
                    return (255, 236, 150), int(255 * ray)
            return None
        if r > inner:
            lit = 0.5 - 0.5 * (x + y) / max(1.0, r) / 1.2
            colour = mix(face, mix(dark, light, lit), 0.7)
            # a ring of rune notches, glowing on a node that is open or taken
            if rune is not None and abs(r - (inner + outer) / 2) < 0.7 and math.sin(a * 12) > 0.6:
                return rune, 255
            return colour, 255
        colour = mix(field, shade(field, 0.55), r / inner)
        if state == "barred" and (abs(x - y) <= 1.0 or abs(x + y) <= 1.0):
            return (200, 60, 48), 255
        return colour, 210 if state == "locked" else 235


# ------------------------------------------------------------------------------------------------ the paintings

def noise(rng, x, y, s):
    return (math.sin(x * 0.29 + y * 0.13) * 0.5 + math.sin(x * 0.071 - y * 0.19 + 1.3) * 0.5) * s \
        + rng.uniform(-1, 1) * s * 0.6


def chrome_and_inventory(c, rng, plank, plank_dark, plank_light, well_inner):
    """The bottom: a plank bar for the chrome row, the player's inventory on planks, and its label's patch."""
    h = height(ROWS)
    y0 = 17 + 18 * 5 - 2
    wood(c, 0, y0, W, h, plank, plank_dark, plank_light, seed=rng.randint(0, 999), plank=10)
    vignette(c, 0, y0, W, h, (10, 8, 6), strength=0.35, reach=8)
    for col in range(9):
        x, y = chest_well(5, col)
        well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    ly = label_y(ROWS)
    for y in range(ly - 2, ly + 9):
        for x in range(5, 70):
            c.put(x, y, mix((222, 206, 168), (194, 174, 132), (y - ly + 2) / 11 + rng.uniform(-0.04, 0.04)))
    c.hline(5, 70, ly - 3, plank_dark)
    c.hline(5, 70, ly + 9, plank_dark)
    for x in (5, 69):
        c.vline(x, ly - 2, ly + 9, plank_dark)
    for row in range(3):
        for col in range(9):
            x, y = inventory_well(ROWS, row, col)
            well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    for col in range(9):
        x, y = hotbar_well(ROWS, col)
        well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    c.round_corners(3)


def title_band(c, top, bottom, edge):
    for y in range(0, 16):
        for x in range(W):
            c.put(x, y, mix(top, bottom, y / 15))
    c.hline(0, W, 15, edge)
    c.hline(0, W, 16, shade(edge, 0.6), 160)


T0, T1 = 17, 17 + 18 * 5 - 2


def glow(c, gx, gy, colour, reach, strength):
    for y in range(T0, T1):
        for x in range(W):
            d = math.hypot(x - gx, y - gy)
            if d < reach:
                c.tint(x, y, colour, strength * (1 - d / reach) ** 2)


def banner(c, x0, top, width, length, cloth, charge):
    """A hanging banner with a swallow-tail and a simple charge (a diamond) in the middle."""
    for y in range(top, top + length):
        for x in range(x0, x0 + width):
            tail = y > top + length - 6 and abs(x - (x0 + width / 2)) < (y - (top + length - 6)) * 0.9
            if tail:
                continue
            fold = 0.85 + 0.15 * math.sin((x - x0) * 1.3)
            colour = shade(cloth, fold)
            mx, my = x0 + width / 2, top + length * 0.42
            if abs(x - mx) / (width * 0.28) + abs(y - my) / (length * 0.18) <= 1:
                colour = shade(charge, fold)
            c.put(x, y, colour)
    c.hline(x0 - 1, x0 + width + 1, top - 1, (90, 60, 30))


def scene_vitality(seed=1):
    """A healer's garden at dawn: warm light, rows of herbs, a well of red."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((240, 190, 140), (120, 60, 60), min(1, v * 1.4)))
    for row in range(5):
        top = T0 + 40 + row * 10
        for x in range(W):
            for y in range(top, min(T1, top + 6)):
                leaf = math.sin(x * 0.9 + row) > 0.2
                c.put(x, y, (60 + row * 6, 110 - row * 6, 50) if leaf else (70, 50, 36))
            if rng.random() < 0.08:
                c.put(x, top - 1, (220, 40, 50))
    glow(c, 140, T0 + 14, (255, 230, 170), 70, 0.5)
    vignette(c, 0, T0, W, T1, (40, 10, 10), strength=0.5, reach=18)
    title_band(c, (70, 22, 26), (96, 32, 36), (34, 10, 12))
    chrome_and_inventory(c, rng, (104, 78, 52), (54, 38, 24), (150, 116, 80), (34, 28, 24))
    return c


def scene_might(seed=2):
    """An arena's sand under torches, weapon racks along a stone wall."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    wall = T0 + 34
    for y in range(T0, wall):
        row = (y - T0) // 8
        for x in range(W):
            mortar = (y - T0) % 8 == 0 or (x + (9 if row % 2 else 0)) % 18 == 0
            c.put(x, y, (52, 48, 46) if mortar else shade((100, 92, 84), 0.9 + ((x // 18 + row) % 3) * 0.04))
    for y in range(wall, T1):
        for x in range(W):
            c.put(x, y, mix((200, 170, 120), (150, 120, 80), (y - wall) / (T1 - wall) + rng.uniform(-0.04, 0.04)))
    for rx in range(14, W, 30):
        for y in range(T0 + 6, wall):
            c.put(rx, y, (140, 140, 150))
            c.put(rx + 1, y, (90, 90, 100))
        for i in range(-3, 4):
            c.put(rx + i, T0 + 9, (120, 80, 40))
    for tx in (44, 132):
        glow(c, tx, T0 + 10, (255, 170, 80), 50, 0.4)
    vignette(c, 0, T0, W, T1, (20, 10, 4), strength=0.5, reach=18)
    title_band(c, (50, 34, 20), (72, 50, 30), (24, 16, 8))
    chrome_and_inventory(c, rng, (96, 70, 46), (48, 34, 20), (140, 106, 70), (34, 26, 18))
    return c


def scene_marksman(seed=3):
    """A forest clearing: tall trunks, green light through leaves, a straw target."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            ray = max(0.0, math.sin(x * 0.05 + y * 0.03)) ** 10
            c.put(x, y, mix(mix((120, 170, 110), (40, 70, 40), v), (230, 250, 200), ray * 0.25))
    for tx in (8, 40, 70, 150, 168):
        w = rng.randint(5, 8)
        for y in range(T0, T1):
            for x in range(tx, min(W, tx + w)):
                c.put(x, y, shade((80, 56, 36), 0.8 + 0.2 * ((x - tx) / w)))
    cx, cy = 118, T0 + 46
    for y in range(T0, T1):
        for x in range(W):
            d = math.hypot(x - cx, y - cy)
            if d < 12:
                ring = int(d // 3) % 2
                c.put(x, y, (220, 40, 40) if ring == 0 else (240, 230, 210))
    vignette(c, 0, T0, W, T1, (8, 20, 8), strength=0.55, reach=18)
    title_band(c, (24, 46, 24), (36, 66, 34), (10, 22, 10))
    chrome_and_inventory(c, rng, (100, 76, 50), (52, 38, 24), (146, 114, 78), (34, 28, 22))
    return c


def scene_shadow(seed=4):
    """Rooftops at night under a pale moon, a lit window here and there."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((24, 26, 56), (8, 8, 20), v))
    mx, my = 40, T0 + 18
    for y in range(T0, T1):
        for x in range(W):
            d = math.hypot(x - mx, y - my)
            if d < 9:
                c.put(x, y, mix((240, 240, 220), (200, 200, 190), d / 9))
            elif d < 22:
                c.tint(x, y, (180, 190, 220), 0.15 * (1 - (d - 9) / 13))
    x = 0
    while x < W:
        w = rng.randint(16, 30)
        h = rng.randint(28, 52)
        top = T1 - h
        for yy in range(top, T1):
            for xx in range(x, min(W, x + w)):
                roof = yy < top + (w // 2 - abs(xx - x - w // 2)) // 2
                if not roof:
                    c.put(xx, yy, (14, 14, 24))
        if rng.random() < 0.7:
            wx, wy = x + rng.randint(3, max(4, w - 6)), top + rng.randint(10, max(11, h - 8))
            for yy in range(wy, wy + 4):
                for xx in range(wx, wx + 3):
                    c.put(xx, yy, (255, 200, 110))
        x += w + rng.randint(0, 3)
    vignette(c, 0, T0, W, T1, (2, 2, 8), strength=0.5, reach=18)
    title_band(c, (16, 16, 34), (26, 26, 52), (6, 6, 16))
    chrome_and_inventory(c, rng, (70, 58, 60), (34, 26, 28), (110, 96, 100), (24, 18, 22))
    return c


def scene_arcana(seed=5):
    """An arcane circle on a dark floor, stars and runes in violet light."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    stars = {(rng.randint(0, W - 1), rng.randint(T0, T1 - 1)) for _ in range(60)}
    for y in range(T0, T1):
        for x in range(W):
            colour = mix((30, 14, 50), (10, 6, 22), (y - T0) / (T1 - T0))
            if (x, y) in stars:
                colour = (220, 200, 255)
            c.put(x, y, colour)
    cx, cy = W // 2, T0 + 44
    for y in range(T0, T1):
        for x in range(W):
            d = math.hypot(x - cx, (y - cy) * 1.6)
            a = math.atan2(y - cy, x - cx)
            if abs(d - 38) < 0.8 or abs(d - 30) < 0.6:
                c.tint(x, y, (190, 140, 255), 0.7)
            elif 30 < d < 38 and math.sin(a * 16) > 0.85:
                c.tint(x, y, (220, 180, 255), 0.6)
    glow(c, cx, cy, (160, 100, 255), 60, 0.35)
    vignette(c, 0, T0, W, T1, (6, 2, 12), strength=0.55, reach=18)
    title_band(c, (36, 16, 56), (54, 24, 80), (16, 6, 26))
    chrome_and_inventory(c, rng, (80, 64, 70), (40, 30, 34), (120, 100, 110), (28, 20, 26))
    return c


def scene_guardian(seed=6):
    """A castle wall at noon, a row of round shields hung along its battlements."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        row = (y - T0) // 9
        for x in range(W):
            mortar = (y - T0) % 9 == 0 or (x + (11 if row % 2 else 0)) % 22 == 0
            c.put(x, y, (70, 70, 74) if mortar else shade((150, 146, 140), 0.85 + noise(rng, x, y, 0.05)))
    for x in range(W):
        if (x // 14) % 2 == 0:
            for y in range(T0, T0 + 8):
                c.put(x, y, mix((150, 190, 230), (200, 220, 240), (y - T0) / 8))
    colours = [(170, 40, 40), (40, 70, 150), (200, 160, 50), (40, 110, 60)]
    for i, sx in enumerate(range(18, W, 36)):
        sy = T0 + 34
        for y in range(sy - 13, sy + 14):
            for x in range(sx - 13, sx + 14):
                d = math.hypot(x - sx, y - sy)
                if d < 13:
                    rim = d > 11
                    face = colours[i % len(colours)]
                    c.put(x, y, (110, 110, 118) if rim else shade(face, 0.8 + 0.2 * (1 - d / 13)))
                    if d < 3:
                        c.put(x, y, (200, 200, 210))
    vignette(c, 0, T0, W, T1, (16, 16, 20), strength=0.5, reach=18)
    title_band(c, (44, 52, 70), (62, 72, 96), (20, 24, 34))
    chrome_and_inventory(c, rng, (110, 106, 100), (56, 54, 50), (156, 152, 146), (34, 34, 36))
    return c


def scene_warden(seed=7):
    """Black rock split by rivers of lava, embers rising in the heat."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((60, 24, 16), (22, 16, 16), v) if v < 0.35 else shade((40, 34, 34), 0.8 + noise(rng, x, y, 0.1)))
    for k in range(3):
        base = T0 + 40 + k * 14
        for x in range(W):
            mid = base + 4 * math.sin(x * 0.05 + k * 2.1)
            for y in range(int(mid) - 2, int(mid) + 3):
                if T0 <= y < T1:
                    c.put(x, y, mix((255, 210, 80), (230, 80, 20), abs(y - mid) / 2.5))
    for _ in range(70):
        x, y = rng.randint(0, W - 1), rng.randint(T0, T1 - 1)
        c.put(x, y, (255, rng.randint(120, 200), 40))
    glow(c, W // 2, T1 - 10, (255, 120, 40), 90, 0.35)
    vignette(c, 0, T0, W, T1, (10, 2, 0), strength=0.55, reach=18)
    title_band(c, (70, 24, 10), (100, 36, 14), (30, 10, 4))
    chrome_and_inventory(c, rng, (80, 66, 62), (40, 32, 30), (120, 104, 98), (26, 20, 18))
    return c


def scene_arms(seed=8):
    """A smithy: a forge's glow on the left, swords and axes racked on timber."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        for x in range(W):
            c.put(x, y, shade((92, 64, 42), 0.75 + 0.15 * math.sin(x * 0.4) + noise(rng, x, y, 0.04)))
    for y in range(T0 + 50, T1):
        for x in range(0, 46):
            c.put(x, y, (60, 56, 54) if (x + y) % 7 else (40, 38, 36))
    glow(c, 22, T0 + 52, (255, 140, 40), 60, 0.6)
    for i, rx in enumerate(range(60, W - 6, 16)):
        axe = i % 2 == 1
        for y in range(T0 + 8, T0 + 62):
            c.put(rx, y, (120, 86, 52) if axe else (190, 196, 206))
            if not axe:
                c.put(rx + 1, y, (130, 136, 150))
        if axe:
            for y in range(T0 + 8, T0 + 20):
                for x in range(rx + 1, rx + 1 + max(1, 7 - abs(y - T0 - 14))):
                    c.put(x, y, (170, 176, 186))
        else:
            for x in range(rx - 3, rx + 5):
                c.put(x, T0 + 50, (150, 120, 60))
    vignette(c, 0, T0, W, T1, (16, 8, 2), strength=0.5, reach=18)
    title_band(c, (60, 36, 20), (84, 52, 30), (28, 16, 8))
    chrome_and_inventory(c, rng, (96, 70, 46), (48, 34, 20), (140, 106, 70), (34, 26, 18))
    return c


def scene_fury(seed=9):
    """A battlefield under a red sky: broken spears, banners in tatters, mist low on the ground."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((200, 60, 40), (60, 14, 14), v) if v < 0.6 else mix((70, 30, 24), (36, 18, 16), v))
    for _ in range(14):
        sx = rng.randint(0, W - 1)
        lean = rng.uniform(-0.5, 0.5)
        top = T0 + rng.randint(20, 46)
        for y in range(top, T1):
            x = int(sx + (y - top) * lean)
            if 0 <= x < W:
                c.put(x, y, (30, 16, 14))
    banner(c, 30, T0 + 14, 10, 26, (120, 20, 20), (20, 10, 10))
    banner(c, 140, T0 + 20, 10, 22, (60, 20, 20), (180, 140, 60))
    for y in range(T1 - 22, T1):
        for x in range(W):
            c.tint(x, y, (230, 180, 170), 0.25 * (y - T1 + 22) / 22)
    vignette(c, 0, T0, W, T1, (20, 0, 0), strength=0.55, reach=18)
    title_band(c, (80, 14, 14), (110, 22, 22), (36, 6, 6))
    chrome_and_inventory(c, rng, (90, 60, 50), (46, 28, 22), (134, 96, 80), (30, 20, 18))
    return c


def scene_trickshot(seed=10):
    """A night range: straw targets in a row, flaming arrows streaking at them."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((18, 22, 50), (40, 34, 30), v))
    for tx in (40, 96, 152):
        ty = T0 + 50
        for y in range(ty - 11, ty + 12):
            for x in range(tx - 11, tx + 12):
                d = math.hypot(x - tx, y - ty)
                if d < 11:
                    c.put(x, y, (220, 50, 40) if int(d // 2.5) % 2 == 0 else (230, 210, 160))
    for k in range(5):
        y0 = T0 + 8 + k * 7
        x0 = rng.randint(0, 40)
        for i in range(70):
            x, y = x0 + i, int(y0 + i * 0.35)
            if 0 <= x < W and T0 <= y < T1:
                c.put(x, y, mix((255, 230, 120), (180, 60, 20), i / 70))
        glow(c, x0 + 70, int(y0 + 24), (255, 160, 60), 16, 0.6)
    vignette(c, 0, T0, W, T1, (4, 4, 12), strength=0.55, reach=18)
    title_band(c, (24, 24, 50), (36, 36, 72), (10, 10, 24))
    chrome_and_inventory(c, rng, (100, 76, 50), (52, 38, 24), (146, 114, 78), (34, 28, 22))
    return c


def scene_ranger(seed=11):
    """Plains at sunset: rolling hills, a line of pines, a long light."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((250, 180, 100), (150, 90, 120), v))
    glow(c, 130, T0 + 30, (255, 220, 140), 50, 0.6)
    for k, (base, colour) in enumerate(((T0 + 46, (90, 110, 70)), (T0 + 58, (70, 96, 52)), (T0 + 70, (52, 76, 40)))):
        for x in range(W):
            top = int(base + 6 * math.sin(x * 0.03 + k * 1.7))
            for y in range(top, T1):
                c.put(x, y, shade(colour, 0.9 + noise(rng, x, y, 0.04)))
    for px in range(4, W, 11):
        h = rng.randint(10, 18)
        base = int(T0 + 46 + 6 * math.sin(px * 0.03))
        for y in range(base - h, base):
            half = (y - base + h) // 3
            for x in range(px - half, px + half + 1):
                if 0 <= x < W:
                    c.put(x, y, (36, 50, 34))
    vignette(c, 0, T0, W, T1, (30, 14, 10), strength=0.5, reach=18)
    title_band(c, (80, 50, 30), (110, 70, 40), (36, 22, 12))
    chrome_and_inventory(c, rng, (104, 80, 52), (54, 40, 26), (150, 118, 80), (34, 28, 22))
    return c


def scene_acrobatics(seed=12):
    """Sea cliffs: a sheer rock face on the left, waves breaking far below, gulls in the wind."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((150, 200, 240), (220, 236, 250), v) if v < 0.55
                  else mix((40, 110, 160), (20, 60, 110), v))
    for y in range(T0 + 44, T1):
        for x in range(W):
            if math.sin(x * 0.2 + y * 0.9) > 0.92:
                c.put(x, y, (230, 240, 250))
    for y in range(T0, T1):
        edge = 40 + int(8 * math.sin(y * 0.15)) + (y - T0) // 6
        for x in range(0, edge):
            c.put(x, y, shade((120, 110, 100), 0.8 + noise(rng, x, y, 0.08)))
    for _ in range(6):
        gx, gy = rng.randint(70, W - 10), rng.randint(T0 + 6, T0 + 30)
        for i in range(-3, 4):
            c.put(gx + i, gy - (3 - abs(i)) // 2, (60, 60, 70))
    vignette(c, 0, T0, W, T1, (6, 20, 34), strength=0.45, reach=18)
    title_band(c, (30, 70, 110), (44, 96, 140), (12, 30, 50))
    chrome_and_inventory(c, rng, (110, 100, 86), (56, 50, 42), (156, 146, 130), (30, 30, 32))
    return c


def scene_rogue(seed=13):
    """A narrow alley at night: wet cobbles, a green glow of something brewing behind a door."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        for x in range(W):
            wall = x < 40 or x > W - 40
            c.put(x, y, shade((34, 30, 36) if wall else (20, 22, 30), 0.8 + noise(rng, x, y, 0.06)))
    for y in range(T0 + 56, T1):
        for x in range(40, W - 40):
            stone = ((x // 6) + (y // 4)) % 2
            c.put(x, y, (46, 48, 56) if stone else (34, 36, 44))
    for y in range(T0 + 22, T0 + 56):
        for x in range(84, 100):
            c.put(x, y, (40, 26, 18))
    glow(c, 92, T0 + 40, (90, 255, 110), 46, 0.45)
    for _ in range(18):
        x, y = rng.randint(70, 115), rng.randint(T0 + 10, T0 + 50)
        c.put(x, y, (150, 255, 160))
    vignette(c, 0, T0, W, T1, (0, 4, 2), strength=0.6, reach=18)
    title_band(c, (20, 34, 24), (30, 50, 34), (8, 14, 10))
    chrome_and_inventory(c, rng, (70, 60, 58), (34, 28, 26), (110, 98, 94), (22, 20, 20))
    return c


def scene_mysticism(seed=14):
    """An alchemist's shelves in candlelight, bottles in every colour."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        for x in range(W):
            c.put(x, y, shade((70, 50, 40), 0.8 + noise(rng, x, y, 0.05)))
    colours = [(200, 60, 200), (60, 160, 240), (240, 80, 60), (80, 220, 120), (240, 200, 60), (160, 100, 255)]
    for shelf in range(3):
        sy = T0 + 22 + shelf * 22
        c.hline(0, W, sy, (120, 84, 50))
        c.hline(0, W, sy + 1, (80, 54, 30))
        x = rng.randint(2, 8)
        while x < W - 8:
            colour = rng.choice(colours)
            h = rng.randint(8, 13)
            for y in range(sy - h, sy):
                neck = y < sy - h + 3
                for xx in range(x + (2 if neck else 0), x + (4 if neck else 6)):
                    c.put(xx, y, shade(colour, 0.7 + 0.3 * (xx - x) / 6) if not neck else (200, 210, 220))
            x += rng.randint(9, 15)
    glow(c, 150, T0 + 30, (255, 200, 120), 60, 0.35)
    vignette(c, 0, T0, W, T1, (12, 6, 4), strength=0.55, reach=18)
    title_band(c, (50, 30, 60), (70, 44, 84), (22, 12, 28))
    chrome_and_inventory(c, rng, (96, 72, 54), (48, 34, 24), (140, 108, 82), (30, 24, 20))
    return c


def scene_battlemage(seed=15):
    """A storm over a tower: dark clouds, a fork of lightning, runes burning blue in the air."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            cloud = 0.5 + 0.5 * math.sin(x * 0.07 + math.sin(y * 0.2) * 2)
            c.put(x, y, mix(mix((40, 44, 70), (70, 76, 100), cloud * (1 - v)), (16, 18, 30), v))
    x = 120.0
    for y in range(T0, T0 + 60):
        x += rng.uniform(-1.6, 1.6)
        for dx in (-1, 0, 1):
            c.put(int(x) + dx, y, (230, 240, 255) if dx == 0 else (140, 170, 255))
    glow(c, 120, T0 + 30, (150, 180, 255), 40, 0.35)
    for y in range(T0 + 30, T1):
        for tx in range(30, 46):
            c.put(tx, y, shade((60, 58, 66), 0.8 + 0.2 * ((tx - 30) % 4 == 0)))
    for _ in range(9):
        rx, ry = rng.randint(60, W - 10), rng.randint(T0 + 40, T1 - 8)
        for i in range(5):
            c.put(rx + i, ry, (120, 200, 255))
            c.put(rx + 2, ry - 2 + i, (120, 200, 255))
    vignette(c, 0, T0, W, T1, (4, 6, 16), strength=0.55, reach=18)
    title_band(c, (26, 30, 60), (40, 46, 86), (10, 12, 26))
    chrome_and_inventory(c, rng, (80, 76, 86), (40, 36, 44), (120, 116, 130), (26, 24, 30))
    return c


def rock(c, rng, top, bottom, light, dark, block=9):
    """A wall of stone in blocks, each a shade of its own."""
    for y in range(T0, T1):
        for x in range(W):
            bx, by = x // block, (y - T0) // block
            f = 0.86 + ((bx * 7 + by * 13) % 5) * 0.035
            v = (y - T0) / (T1 - T0)
            c.put(x, y, shade(mix(mix(top, bottom, v), light, 0.0), f + noise(rng, x, y, 0.03)))
            if (y - T0) % block == 0 or x % block == 0:
                c.tint(x, y, dark, 0.35)


def speckles(c, rng, colour, count, x0=0, x1=W, size=2):
    for _ in range(count):
        x, y = rng.randint(x0, x1 - size - 1), rng.randint(T0 + 2, T1 - size - 2)
        for dy in range(size):
            for dx in range(size):
                c.put(x + dx, y + dy, shade(colour, rng.uniform(0.85, 1.1)))


def trunk(c, x0, width, bark, top=T0, bottom=T1):
    for y in range(top, bottom):
        for x in range(max(0, x0), min(W, x0 + width)):
            ring = 0.78 + 0.22 * math.sin((x - x0) / max(1, width) * math.pi)
            c.put(x, y, shade(bark, ring * (0.92 if (y // 3 + x) % 5 == 0 else 1.0)))


def finish(c, rng, edge, top, bottom, plank, plank_dark, plank_light, well_inner):
    vignette(c, 0, T0, W, T1, edge, strength=0.5, reach=18)
    title_band(c, top, bottom, shade(top, 0.5))
    chrome_and_inventory(c, rng, plank, plank_dark, plank_light, well_inner)
    return c


def scene_prospector(seed=6):
    """A cave wall flecked with ore, a lantern's warm light."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    rock(c, rng, (118, 118, 122), (82, 82, 88), (130, 130, 136), (40, 40, 46))
    speckles(c, rng, (216, 175, 147), 26)
    speckles(c, rng, (250, 214, 60), 14)
    speckles(c, rng, (96, 230, 220), 9)
    speckles(c, rng, (40, 40, 40), 18)
    glow(c, 132, T0 + 22, (255, 196, 110), 70, 0.45)
    return finish(c, rng, (12, 10, 8), (58, 58, 64), (82, 82, 90), (96, 80, 60), (48, 38, 28), (140, 118, 90),
                  (30, 28, 26))


def scene_delver(seed=7):
    """Deep down: a deepslate tunnel held up by beams, a torch far along it."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    rock(c, rng, (74, 74, 80), (36, 36, 44), (90, 90, 96), (20, 20, 26), block=7)
    for bx in (20, 92, 164):
        for y in range(T0, T1):
            for x in range(bx, bx + 5):
                c.put(x, y, shade((112, 80, 46), 0.85 + 0.15 * ((x - bx) / 4)))
        for x in range(bx - 14, bx + 19):
            for y in range(T0 + 4, T0 + 8):
                c.put(max(0, min(W - 1, x)), y, (98, 70, 40))
    for x in range(W):
        c.put(x, T1 - 6, (120, 120, 126))
        c.put(x, T1 - 9, (120, 120, 126))
        if x % 6 == 0:
            for y in range(T1 - 9, T1 - 5):
                c.put(x, y, (90, 64, 36))
    glow(c, 110, T0 + 40, (255, 170, 80), 40, 0.5)
    return finish(c, rng, (4, 4, 8), (30, 30, 36), (46, 46, 54), (78, 70, 64), (38, 32, 28), (118, 106, 96),
                  (24, 22, 22))


def scene_veinbreaker(seed=8):
    """A red vein winding through dark stone, glowing where it is broken open."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    rock(c, rng, (96, 94, 98), (60, 58, 64), (110, 108, 112), (30, 28, 32))
    for x in range(W):
        cy = T0 + 44 + math.sin(x * 0.045) * 20 + math.sin(x * 0.13) * 5
        width = 5 + 3 * math.sin(x * 0.07 + 1)
        for y in range(int(cy - width), int(cy + width)):
            if T0 <= y < T1 and rng.random() < 0.8:
                c.put(x, y, shade((200, 30, 30), rng.uniform(0.75, 1.15)))
    glow(c, 60, T0 + 40, (255, 70, 60), 40, 0.4)
    glow(c, 150, T0 + 52, (255, 120, 60), 36, 0.35)
    return finish(c, rng, (14, 6, 6), (60, 22, 22), (86, 32, 30), (92, 76, 62), (46, 36, 28), (134, 114, 92),
                  (30, 24, 22))


def scene_digger(seed=9):
    """The ground cut open: grass on top, dirt, a band of gravel, clay at the bottom."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    bands = [(T0, T0 + 8, (98, 156, 64)), (T0 + 8, T0 + 46, (134, 96, 66)), (T0 + 46, T0 + 62, (132, 126, 124)),
             (T0 + 62, T1, (160, 166, 178))]
    for top, bottom, colour in bands:
        for y in range(top, bottom):
            for x in range(W):
                wave = math.sin(x * 0.08 + top) * 2
                c.put(x, y, shade(colour, rng.uniform(0.86, 1.08) if y > top + wave else 0.9))
    speckles(c, rng, (90, 88, 86), 40, size=2)
    for x in range(60, 120):
        depth = int(30 - abs(x - 90) * 0.9)
        for y in range(T0, T0 + max(0, depth)):
            c.put(x, y, shade((80, 58, 40), 0.7))
    glow(c, 40, T0 + 6, (255, 240, 190), 80, 0.3)
    return finish(c, rng, (16, 10, 6), (64, 46, 30), (90, 66, 44), (104, 80, 54), (52, 38, 24), (150, 120, 84),
                  (34, 26, 20))


def scene_treasure_hunter(seed=10):
    """A sandy dig: a chest half buried, gold glinting in the sand."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            dune = math.sin(x * 0.05) * 6
            c.put(x, y, mix((236, 206, 150), (196, 160, 104), min(1, max(0, v + dune / 90 + rng.uniform(-0.03, 0.03)))))
    cx, cy = 96, T0 + 50
    for y in range(cy - 10, cy + 8):
        for x in range(cx - 16, cx + 16):
            lid = y < cy - 2
            c.put(x, y, shade((136, 92, 48), 0.8 if lid else 1.0))
            if abs(x - cx) <= 1 and cy - 4 < y < cy + 2:
                c.put(x, y, (250, 214, 60))
    for x in range(cx - 18, cx + 18):
        for y in range(cy + 4, cy + 9):
            c.put(x, y, shade((220, 188, 128), rng.uniform(0.9, 1.05)))
    speckles(c, rng, (255, 222, 80), 22, size=1)
    glow(c, cx, cy - 6, (255, 230, 140), 40, 0.4)
    return finish(c, rng, (30, 20, 6), (96, 70, 30), (130, 96, 44), (120, 92, 56), (60, 44, 24), (170, 134, 86),
                  (40, 30, 20))


def scene_archaeologist(seed=11):
    """Ruins at dusk: sandstone pillars, a patterned pot, the sky going violet."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((214, 140, 120), (96, 60, 110), v * 0.9))
    ground = T0 + 62
    for y in range(ground, T1):
        for x in range(W):
            c.put(x, y, shade((210, 182, 128), rng.uniform(0.86, 1.04)))
    for px in (22, 140):
        for y in range(T0 + 12, ground):
            for x in range(px, px + 16):
                c.put(x, y, shade((218, 196, 140), 0.82 + 0.18 * ((x - px) / 15) + (0.06 if (y // 6) % 2 else 0)))
    cx, cy = 96, ground - 10
    for y in range(cy - 14, cy + 10):
        for x in range(cx - 12, cx + 12):
            r = abs(x - cx) / (12 * (0.6 + 0.4 * math.sin((y - cy + 14) / 24 * math.pi)))
            if r <= 1:
                c.put(x, y, shade((176, 92, 60), 0.85 + 0.15 * (1 - r)))
                if (y - cy) % 6 == 0:
                    c.put(x, y, (92, 46, 30))
    return finish(c, rng, (20, 10, 24), (70, 40, 56), (96, 56, 76), (112, 90, 70), (56, 42, 32), (160, 132, 104),
                  (36, 28, 26))


def scene_lumberjack(seed=12):
    """A clearing of stumps, a pile of logs, warm daylight."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((170, 210, 150), (70, 120, 50), min(1, v * 1.3)))
    for tx in (4, 30, 156, 178):
        trunk(c, tx, 9, (96, 70, 44))
    for row in range(3):
        for i in range(4 - row):
            lx, ly = 62 + i * 18 + row * 9, T1 - 14 - row * 14
            for y in range(ly - 7, ly + 7):
                for x in range(lx - 8, lx + 8):
                    d = math.hypot(x - lx, y - ly)
                    if d <= 7:
                        ring = int(d) % 3 == 0
                        c.put(x, y, (150, 110, 64) if d > 6 else ((176, 136, 84) if ring else (204, 166, 108)))
    glow(c, 100, T0 + 4, (255, 240, 190), 90, 0.35)
    return finish(c, rng, (10, 18, 6), (40, 60, 30), (58, 86, 42), (110, 82, 52), (56, 40, 24), (156, 122, 82),
                  (36, 28, 20))


def scene_forester(seed=13):
    """An orchard: round green crowns with red apples, saplings in a row."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((190, 224, 236), (120, 170, 90), min(1, v * 1.6)))
    for cx in (28, 96, 164):
        trunk(c, cx - 3, 6, (110, 80, 50), top=T0 + 40, bottom=T1 - 8)
        for y in range(T0 + 6, T0 + 50):
            for x in range(cx - 26, cx + 26):
                if 0 <= x < W and math.hypot(x - cx, (y - T0 - 28) * 1.2) < 24 + rng.uniform(-1.5, 1.5):
                    c.put(x, y, shade((70, 140, 50), rng.uniform(0.75, 1.1)))
        for _ in range(9):
            ax, ay = cx + rng.randint(-18, 18), T0 + rng.randint(14, 44)
            c.put(ax, ay, (210, 30, 30))
            c.put(ax + 1, ay, (180, 20, 20))
    for sx in range(12, W, 22):
        for y in range(T1 - 10, T1 - 2):
            c.put(sx, y, (60, 120, 40))
        c.put(sx - 1, T1 - 9, (80, 150, 50))
        c.put(sx + 1, T1 - 8, (80, 150, 50))
    return finish(c, rng, (8, 20, 8), (34, 66, 28), (50, 92, 40), (108, 84, 54), (54, 40, 24), (154, 124, 84),
                  (34, 28, 20))


def scene_feller(seed=14):
    """A dark spruce wood, one great trunk coming down across it."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((70, 96, 80), (20, 34, 26), v))
    for tx in (6, 34, 120, 150, 176):
        for y in range(T0, T1):
            half = 2 + (y - T0) // 10
            for x in range(tx - half, tx + half):
                if 0 <= x < W and rng.random() < 0.9:
                    c.put(x, y, shade((30, 60, 40), rng.uniform(0.7, 1.05)))
        trunk(c, tx - 2, 4, (70, 48, 32))
    for i in range(140):
        x, y = 40 + i, T1 - 12 - int(i * 0.42)
        for d in range(-4, 5):
            if 0 <= x < W and T0 <= y + d < T1:
                c.put(x, y + d, shade((92, 64, 40), 0.75 + 0.25 * (1 - abs(d) / 4)))
    return finish(c, rng, (4, 10, 6), (22, 40, 28), (34, 58, 40), (90, 70, 50), (44, 32, 22), (134, 108, 78),
                  (28, 22, 18))


def scene_anvil_work(seed=15):
    """A smithy: dark stone, an anvil, the forge's orange glow."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    rock(c, rng, (66, 58, 56), (38, 32, 30), (76, 68, 64), (24, 20, 18), block=10)
    ax, ay = 96, T0 + 56
    for y in range(ay - 14, ay - 6):
        for x in range(ax - 26, ax + 22):
            c.put(x, y, shade((70, 72, 78), 0.8 + 0.2 * ((y - ay + 14) / 8)))
    for y in range(ay - 6, ay + 10):
        w = 8 if y < ay + 4 else 16
        for x in range(ax - w, ax + w):
            c.put(x, y, shade((58, 60, 66), 0.9))
    glow(c, 160, T0 + 30, (255, 130, 40), 70, 0.55)
    glow(c, ax, ay - 16, (255, 200, 120), 24, 0.35)
    speckles(c, rng, (255, 190, 80), 12, x0=120, size=1)
    return finish(c, rng, (10, 4, 2), (60, 28, 14), (88, 42, 20), (96, 72, 54), (48, 34, 24), (138, 108, 84),
                  (30, 22, 18))


def scene_salvager(seed=16):
    """A heap of old gear — iron, gold and gems — by a grindstone's wheel."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((110, 100, 92), (60, 54, 50), v))
    for _ in range(90):
        x, y = rng.randint(10, W - 60), rng.randint(T0 + 30, T1 - 4)
        colour = rng.choice([(190, 190, 196), (160, 160, 168), (236, 200, 70), (110, 220, 210), (120, 110, 104)])
        length = rng.randint(4, 10)
        for i in range(length):
            if 0 <= x + i < W and T0 <= y - i // 2 < T1:
                c.put(x + i, y - i // 2, shade(colour, rng.uniform(0.8, 1.1)))
    gx, gy = W - 34, T0 + 46
    for y in range(T0, T1):
        for x in range(W - 60, W):
            d = math.hypot(x - gx, y - gy)
            if d < 20:
                c.put(x, y, shade((150, 146, 140), 0.8 + 0.2 * math.sin(d)))
            elif d < 22:
                c.put(x, y, (80, 60, 40))
    return finish(c, rng, (12, 10, 8), (56, 52, 50), (80, 74, 70), (100, 80, 60), (50, 38, 28), (144, 118, 92),
                  (32, 26, 22))


def scene_forgemaster(seed=17):
    """Blackstone and lava: the nether's forge, netherite's dark sheen."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    rock(c, rng, (46, 40, 46), (24, 20, 26), (56, 50, 56), (12, 10, 14), block=8)
    for x in range(W):
        top = T1 - 16 + int(math.sin(x * 0.1) * 2)
        for y in range(top, T1):
            c.put(x, y, mix((255, 150, 30), (210, 60, 10), rng.uniform(0, 1)))
    for y in range(T0 + 24, T0 + 46):
        for x in range(70, 122):
            c.put(x, y, shade((68, 58, 62), 0.75 + 0.25 * ((x - 70) / 52)))
    glow(c, 96, T1 - 10, (255, 110, 30), 90, 0.5)
    speckles(c, rng, (255, 170, 60), 16, size=1)
    return finish(c, rng, (6, 2, 2), (40, 20, 22), (60, 30, 30), (80, 64, 60), (40, 30, 28), (120, 100, 96),
                  (26, 20, 20))


PAINTERS = [scene_vitality, scene_might, scene_marksman, scene_shadow,
            scene_arcana, scene_guardian, scene_warden, scene_arms,
            scene_fury, scene_trickshot, scene_ranger, scene_acrobatics,
            scene_rogue, scene_mysticism, scene_battlemage, scene_prospector,
            scene_delver, scene_veinbreaker, scene_digger, scene_treasure_hunter,
            scene_archaeologist, scene_lumberjack, scene_forester, scene_feller,
            scene_anvil_work, scene_salvager, scene_forgemaster] + nature_scenes.PAINTERS


def write_scenes():
    out = os.path.join(ASSETS, "textures", "font", "tree_scene")
    os.makedirs(out, exist_ok=True)
    providers = []
    files = []
    for index, (name, paint) in enumerate(zip(SCENES, PAINTERS)):
        c = paint()
        c.save(os.path.join(out, name + ".png"))
        files.append("assets/%s/textures/font/tree_scene/%s.png" % (NS, name))
        providers.append({"type": "bitmap", "file": "%s:font/tree_scene/%s.png" % (NS, name),
                          "ascent": 13, "height": height(ROWS), "chars": [chr(0xE000 + index)]})
    providers.append({"type": "space", "advances": {"": -8, "": -169}})
    with open(os.path.join(ASSETS, "font", "tree_scene.json"), "w") as f:
        json.dump({"providers": providers}, f, indent=1)
    files.append("assets/%s/font/tree_scene.json" % NS)
    return files


def paint():
    """Writes the frames, lines and paintings into the pack; returns the files written."""
    os.makedirs(os.path.join(ASSETS, "font"), exist_ok=True)
    files, images = core_tree.write(ASSETS, NS, "tree", AdventurerLook())
    core_tree.sheet(images, "/tmp/tree-adventurers.png")
    return files + write_scenes()


if __name__ == "__main__":
    print(len(paint()), "files")

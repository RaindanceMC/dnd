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
SCENES = ["vitality", "might", "marksman", "shadow", "arcana"] + nature_scenes.SCENES

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


PAINTERS = [scene_vitality, scene_might, scene_marksman, scene_shadow, scene_arcana] + nature_scenes.PAINTERS


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

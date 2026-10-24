"""
The adventurers' menus on parchment, in the character sheet's hand: a page with scorched edges bound in leather,
an inked frame under every slot, the navigation on a gold-tooled leather strip, the inventory in a stitched
pocket. One painting per window height; the menus' slots move, so every slot gets a frame.

Uses Core's menu canvas (RainsCore/art/menus, checked out next to this repository).

    python3 parchment.py
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "..", "..", "RainsCore", "art", "menus"))
from canvas import Canvas, W, chest_well, inventory_well, hotbar_well, label_y, height, mix

PACK = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules", "dnd", "pack")
NS = "rainsadventurers"

PARCHMENT = (226, 205, 160)
PARCHMENT_DARK = (190, 160, 110)
INK = (74, 50, 32)
GOLD = (214, 168, 64)
GOLD_LIGHT = (250, 220, 130)
GOLD_DARK = (130, 92, 28)
LEATHER = (92, 56, 34)
LEATHER_DARK = (58, 34, 20)


def paint(rows):
    h = height(rows)
    c = Canvas(W, h, seed=40 + rows)
    rng = random.Random(rows)

    def noise(x, y, scale):
        return (math.sin(x * 0.31 + y * 0.17) * 0.5 + math.sin(x * 0.07 - y * 0.23) * 0.5) * scale \
            + rng.uniform(-1, 1) * scale

    # the leather binding
    for y in range(h):
        for x in range(W):
            c.put(x, y, mix(LEATHER, LEATHER_DARK, 0.3 + (y / h) * 0.3 + noise(x, y, 6) / 40))
    # blind-tooled border on the leather
    for x in range(2, W - 2):
        c.put(x, 1, LEATHER_DARK)
        c.put(x, h - 2, LEATHER_DARK)
    for y in range(2, h - 2):
        c.put(1, y, LEATHER_DARK)
        c.put(W - 2, y, LEATHER_DARK)

    # the page: from the window's top to the bottom of the last content row (the chrome row is leather)
    chrome_y = chest_well(rows - 1, 0)[1] if rows > 1 else None
    page = (3, 3, W - 4, (chrome_y - 1) if chrome_y else 16)
    for y in range(page[1], page[3]):
        for x in range(page[0], page[2]):
            edge = min(x - page[0], y - page[1], page[2] - 1 - x, page[3] - 1 - y)
            burn = max(0.0, 1.0 - edge / 6.0) ** 1.6
            col = mix(PARCHMENT, PARCHMENT_DARK, max(0, min(1, 0.12 + noise(x, y, 0.06) + burn * 0.75)))
            if edge == 0 and rng.random() < 0.55:
                col = mix(col, (60, 36, 18), 0.7)
            c.put(x, y, col)
    # faint ruled lines
    for y in range(page[1] + 16, page[3] - 3, 6):
        for x in range(page[0] + 6, page[2] - 6):
            if rng.random() < 0.8:
                c.tint(x, y, INK, 0.06)
    # the title band
    for y in range(4, 15):
        for x in range(6, W - 7):
            c.tint(x, y, PARCHMENT_DARK, 0.35)
    for x in range(6, W - 7):
        c.put(x, 15, INK, 200)
        c.put(x, 16, GOLD_DARK, 160)

    def frame(x0, y0, style):
        for y in range(y0, y0 + 18):
            for x in range(x0, x0 + 18):
                lx, ly = x - x0, y - y0
                if lx in (0, 17) and ly in (0, 17):
                    continue
                border = lx in (0, 17) or ly in (0, 17)
                shade_in = lx == 1 or ly == 1
                light_in = lx == 16 or ly == 16
                if style == "ink":
                    if border:
                        c.put(x, y, mix(INK, PARCHMENT_DARK, 0.25), 235)
                    elif shade_in:
                        c.put(x, y, mix(PARCHMENT_DARK, INK, 0.35))
                    elif light_in:
                        c.put(x, y, mix(PARCHMENT, (255, 248, 230), 0.4))
                    else:
                        c.put(x, y, mix(PARCHMENT, PARCHMENT_DARK, 0.45 + noise(x, y, 0.04)))
                elif style == "chrome":
                    if border:
                        c.put(x, y, LEATHER_DARK)
                    elif shade_in:
                        c.put(x, y, mix(LEATHER_DARK, (0, 0, 0), 0.3))
                    else:
                        c.put(x, y, mix(LEATHER, LEATHER_DARK, 0.25 + noise(x, y, 0.05)))
                elif style == "pocket":
                    stitch = (lx in (1, 16) or ly in (1, 16)) and (x + y) % 3 == 0
                    if border:
                        c.put(x, y, LEATHER_DARK)
                    elif stitch:
                        c.put(x, y, (156, 122, 82))
                    else:
                        c.put(x, y, mix(LEATHER, LEATHER_DARK, 0.45 + noise(x, y, 0.05)))

    for r in range(rows):
        for col in range(9):
            x, y = chest_well(r, col)
            frame(x, y, "chrome" if (r == rows - 1 and rows > 1) else "ink")
    if chrome_y:
        # gold tooling along the leather strip the navigation sits on
        for x in range(4, W - 4):
            c.put(x, chrome_y - 1, GOLD_DARK)
        for x in range(4, W - 4, 3):
            c.put(x, chrome_y + 18, GOLD_DARK, 170)
    # gold corners on the page
    for (x, y), (dx, dy) in (((3, 3), (1, 1)), ((W - 5, 3), (-1, 1)), ((3, page[3] - 1), (1, -1)),
                             ((W - 5, page[3] - 1), (-1, -1))):
        for i in range(9):
            c.put(x + dx * i, y, GOLD if i < 8 else GOLD_DARK)
            c.put(x, y + dy * i, GOLD if i < 8 else GOLD_DARK)
        for i in range(1, 4):
            c.put(x + dx * i, y + dy * i, GOLD_LIGHT)
    # the inventory: a stitched leather pocket under a light strip for the game's "Inventory"
    ly = label_y(rows)
    for y in range(ly - 1, ly + 10):
        for x in range(6, W - 7):
            c.put(x, y, mix(PARCHMENT, PARCHMENT_DARK, 0.35 + noise(x, y, 0.05)))
    for x in range(6, W - 7):
        c.put(x, ly - 2, GOLD_DARK, 160)
    for r in range(3):
        for col in range(9):
            frame(*inventory_well(rows, r, col), "pocket")
    for col in range(9):
        frame(*hotbar_well(rows, col), "pocket")
    return c


def write():
    providers, files = [], []
    for rows in range(1, 7):
        c = paint(rows)
        rel = f"assets/{NS}/textures/font/parchment/parchment_{rows}.png"
        path = os.path.join(PACK, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        c.save(path)
        files.append(rel)
        providers.append({"type": "bitmap", "file": f"{NS}:font/parchment/parchment_{rows}.png", "ascent": 13,
                          "height": c.h, "chars": [chr(0xE000 + rows)]})
    providers.append({"type": "space", "advances": {"": -8, "": -169}})
    rel = f"assets/{NS}/font/parchment.json"
    with open(os.path.join(PACK, rel), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, indent=1)
    files.append(rel)
    paint(6).img.resize((W * 3, height(6) * 3), Image.NEAREST).save(os.path.join(HERE, "parchment-preview.png"))
    print("\n".join(files))


if __name__ == "__main__":
    write()

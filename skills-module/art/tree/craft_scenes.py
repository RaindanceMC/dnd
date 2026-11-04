"""
The paintings behind Artisan, Building, Trading, Enchanting and Exploration's branches, in the order SkillsPack.SCENES
lists them after the fighting skills'. skilltree_art.py hands itself in (painters(art)) for its canvas, chrome and
helpers, so this file never imports it back.
"""
import math
import random

SCENES = ["workbench", "furnace", "mason", "architect", "haggler", "merchant", "table", "anvil", "wayfarer",
          "cartographer"]


def painters(art):
    Canvas, W, mix, shade, vignette = art.Canvas, art.W, art.mix, art.shade, art.vignette
    T0, T1, ROWS = art.T0, art.T1, art.ROWS
    glow, title_band, chrome, height = art.glow, art.title_band, art.chrome_and_inventory, art.height

    def sky(c, top, bottom):
        for y in range(T0, T1):
            for x in range(W):
                c.put(x, y, mix(top, bottom, (y - T0) / (T1 - T0)))

    def finish(c, rng, edge, band, plank, well_inner):
        vignette(c, 0, T0, W, T1, edge, strength=0.5, reach=18)
        title_band(c, band[0], band[1], band[2])
        chrome(c, rng, plank[0], plank[1], plank[2], well_inner)
        return c

    def workbench(seed=11):
        """A workshop wall of planks, tools on pegs, lamplight on a crafting table."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        for y in range(T0, T1):
            for x in range(W):
                seam = x % 22 == 0
                c.put(x, y, (60, 42, 26) if seam else shade((132, 96, 60), 0.9 + 0.08 * math.sin(y * 0.7 + x // 22)))
        for tx, length in ((20, 26), (48, 20), (76, 30), (120, 22), (150, 28)):
            for y in range(T0 + 8, T0 + 8 + length):
                c.put(tx, y, (110, 80, 50))
            for i in range(-4, 5):
                c.put(tx + i, T0 + 8 + length, (170, 170, 180))
            c.put(tx, T0 + 6, (40, 30, 20))
        top = T1 - 22
        for y in range(top, T1):
            for x in range(40, 136):
                grid = (x - 40) % 10 == 0 or (y - top) % 7 == 0
                c.put(x, y, (78, 52, 30) if grid else (168, 120, 74))
        glow(c, 88, T0 + 30, (255, 210, 140), 80, 0.45)
        return finish(c, rng, (30, 18, 8), ((60, 40, 22), (84, 58, 32), (30, 20, 10)),
                      ((104, 78, 52), (54, 38, 24), (150, 116, 80)), (34, 28, 24))

    def furnace(seed=12):
        """Dark brick around a furnace's mouth, fire inside, sparks rising."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        for y in range(T0, T1):
            row = (y - T0) // 7
            for x in range(W):
                mortar = (y - T0) % 7 == 0 or (x + (8 if row % 2 else 0)) % 16 == 0
                c.put(x, y, (30, 26, 26) if mortar else shade((84, 76, 74), 0.85 + 0.1 * ((x // 16 + row) % 3)))
        cx, cy = W // 2, T0 + 52
        for y in range(T0, T1):
            for x in range(W):
                if abs(x - cx) < 30 and cy - 18 < y < cy + 18:
                    heat = 1 - abs(x - cx) / 30
                    flicker = 0.5 + 0.5 * math.sin(x * 0.9 + y * 0.4)
                    c.put(x, y, mix((120, 30, 10), (255, 200, 60), heat * (0.6 + 0.4 * flicker)))
        for _ in range(40):
            c.put(rng.randint(cx - 40, cx + 40), rng.randint(T0, cy - 20), (255, 180, 80))
        glow(c, cx, cy, (255, 140, 40), 90, 0.5)
        return finish(c, rng, (20, 6, 2), ((60, 24, 14), (86, 34, 18), (28, 10, 6)),
                      ((90, 70, 60), (44, 34, 28), (130, 106, 92)), (30, 22, 20))

    def mason(seed=13):
        """A brick wall going up under a pale sky, scaffolding poles beside it."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        sky(c, (176, 206, 236), (230, 236, 240))
        wall_top = T0 + 26
        for y in range(wall_top, T1):
            row = (y - wall_top) // 6
            for x in range(14, W - 14):
                edge = wall_top + (3 if (x // 30) % 2 else 0)
                if y < edge:
                    continue
                mortar = (y - wall_top) % 6 == 0 or (x + (6 if row % 2 else 0)) % 12 == 0
                c.put(x, y, (196, 186, 170) if mortar else shade((166, 84, 64), 0.88 + rng.uniform(0, 0.12)))
        for px in (8, W - 10):
            for y in range(T0 + 4, T1):
                c.put(px, y, (180, 150, 90))
                c.put(px + 1, y, (130, 100, 60))
            for y in range(T0 + 10, T1, 18):
                c.hline(px - 5, px + 7, y, (150, 120, 70))
        return finish(c, rng, (40, 30, 24), ((120, 60, 44), (150, 78, 58), (60, 28, 20)),
                      ((104, 78, 52), (54, 38, 24), (150, 116, 80)), (34, 28, 24))

    def architect(seed=14):
        """A blueprint: blue paper, a white grid, a house drawn in lines."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        for y in range(T0, T1):
            for x in range(W):
                line = (x % 8 == 0 or (y - T0) % 8 == 0)
                c.put(x, y, (60, 110, 180) if line else (36, 78, 150))
        house = [(50, 70), (50, 40), (88, 18), (126, 40), (126, 70), (50, 70)]
        for (x0, y0), (x1, y1) in zip(house, house[1:]):
            steps = max(abs(x1 - x0), abs(y1 - y0))
            for s in range(steps + 1):
                x = round(x0 + (x1 - x0) * s / steps)
                y = round(y0 + (y1 - y0) * s / steps)
                c.put(x, T0 + y, (236, 244, 255))
        c.hline(50, 127, T0 + 40, (236, 244, 255))
        for y in range(T0 + 52, T0 + 71):
            c.put(80, y, (236, 244, 255))
            c.put(96, y, (236, 244, 255))
        c.hline(80, 97, T0 + 52, (236, 244, 255))
        return finish(c, rng, (8, 20, 50), ((24, 50, 96), (34, 66, 120), (10, 22, 46)),
                      ((96, 80, 64), (48, 38, 30), (140, 120, 100)), (28, 26, 30))

    def haggler(seed=15):
        """A market stall: a striped awning, crates and a heap of emeralds."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        sky(c, (250, 210, 150), (230, 170, 110))
        for y in range(T0, T0 + 20):
            for x in range(W):
                stripe = (x // 12) % 2 == 0
                scallop = y > T0 + 16 and (x % 12 - 6) ** 2 > (T0 + 20 - y) * 9
                if not scallop:
                    c.put(x, y, (200, 50, 50) if stripe else (240, 230, 210))
        for x0 in (10, 60, 120):
            for y in range(T1 - 26, T1):
                for x in range(x0, x0 + 40):
                    c.put(x, y, (70, 48, 28) if (x - x0) % 10 == 0 or (y - T1) % 9 == 0 else (150, 106, 62))
        for _ in range(70):
            x, y = rng.randint(70, 110), rng.randint(T1 - 40, T1 - 27)
            if abs(x - 90) < (y - (T1 - 42)) * 1.6:
                c.put(x, y, (40, 200, 90) if rng.random() < 0.7 else (160, 255, 190))
        return finish(c, rng, (40, 20, 10), ((90, 40, 20), (120, 56, 30), (44, 18, 8)),
                      ((104, 78, 52), (54, 38, 24), (150, 116, 80)), (34, 28, 24))

    def merchant(seed=16):
        """A road over the hills at dusk, a laden cart going down it."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        sky(c, (90, 60, 120), (240, 150, 90))
        for x in range(W):
            ridge = T0 + 46 + int(8 * math.sin(x * 0.05) + 4 * math.sin(x * 0.13 + 1))
            for y in range(ridge, T1):
                c.put(x, y, mix((60, 80, 50), (30, 44, 30), (y - ridge) / 40))
        for y in range(T0 + 50, T1):
            half = 2 + (y - T0 - 50) // 2
            mid = W // 2 + int(10 * math.sin(y * 0.2))
            for x in range(mid - half, mid + half):
                c.put(x, y, (150, 120, 80))
        for y in range(T0 + 56, T0 + 66):
            for x in range(96, 116):
                c.put(x, y, (110, 70, 40))
        for wx in (99, 113):
            for a in range(0, 360, 20):
                c.put(wx + round(3 * math.cos(math.radians(a))), T0 + 67 + round(3 * math.sin(math.radians(a))),
                      (40, 26, 16))
        return finish(c, rng, (20, 10, 30), ((54, 34, 70), (76, 48, 96), (24, 14, 34)),
                      ((96, 72, 52), (48, 36, 26), (140, 110, 84)), (30, 24, 26))

    def table(seed=17):
        """A dark library, an enchanting table's book lit up, glyphs drifting to it."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        for y in range(T0, T1):
            for x in range(W):
                shelf = (y - T0) % 18 < 3
                book = (x // 5 + (y - T0) // 18) % 4
                colours = [(110, 40, 40), (40, 70, 110), (60, 100, 50), (120, 100, 50)]
                c.put(x, y, (70, 46, 26) if shelf else shade(colours[book], 0.55))
        cx, cy = W // 2, T0 + 58
        for y in range(cy, T1):
            for x in range(cx - 20, cx + 20):
                c.put(x, y, (40, 20, 30) if y > cy + 4 else (180, 30, 40))
        glow(c, cx, cy - 6, (160, 120, 255), 70, 0.6)
        for _ in range(30):
            gx, gy = rng.randint(20, W - 20), rng.randint(T0 + 4, cy - 6)
            c.put(gx, gy, (220, 210, 255))
            c.put(gx + 1, gy, (180, 160, 255))
        return finish(c, rng, (10, 4, 20), ((40, 20, 60), (60, 30, 86), (18, 8, 28)),
                      ((80, 64, 70), (40, 30, 34), (120, 100, 110)), (28, 20, 26))

    def anvil(seed=18):
        """A smithy: an anvil's dark shape, a forge behind it, sparks flying."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        sky(c, (50, 40, 36), (26, 20, 18))
        glow(c, 40, T0 + 40, (255, 120, 40), 70, 0.6)
        base_y = T1 - 12
        shape = [(60, 116, base_y - 26, base_y - 20), (78, 100, base_y - 20, base_y - 8), (70, 108, base_y - 8, base_y)]
        for x0, x1, y0, y1 in shape:
            for y in range(y0, y1):
                for x in range(x0, x1):
                    c.put(x, y, shade((70, 72, 78), 0.8 + 0.2 * (y1 - y) / max(1, y1 - y0)))
        for y in range(base_y - 30, base_y - 26):
            for x in range(52, 60 + (y - base_y + 30) * 2):
                c.put(x, y, (70, 72, 78))
        for _ in range(60):
            a = rng.uniform(math.pi * 1.1, math.pi * 1.9)
            r = rng.uniform(4, 40)
            c.put(int(88 + r * math.cos(a)), int(base_y - 28 + r * math.sin(a)), (255, 200, 90))
        return finish(c, rng, (8, 4, 2), ((50, 34, 26), (70, 48, 36), (24, 14, 10)),
                      ((90, 70, 60), (44, 34, 28), (130, 106, 92)), (30, 22, 20))

    def wayfarer(seed=19):
        """Rolling hills under a morning sky, a dirt path winding into the distance."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        sky(c, (150, 200, 240), (250, 230, 190))
        for band, (colour, base, amp) in enumerate((((110, 150, 90), 34, 6), ((80, 130, 64), 46, 8),
                                                     ((60, 110, 50), 60, 5))):
            for x in range(W):
                top = T0 + base + int(amp * math.sin(x * (0.04 + band * 0.02) + band))
                for y in range(top, T1):
                    c.put(x, y, shade(colour, 0.95 + rng.uniform(-0.04, 0.04)))
        for y in range(T0 + 40, T1):
            t = (y - T0 - 40) / (T1 - T0 - 40)
            half = 1 + int(t * 12)
            mid = int(W / 2 + 30 * math.sin(t * 4))
            for x in range(mid - half, mid + half):
                c.put(x, y, (176, 140, 96))
        glow(c, 140, T0 + 10, (255, 250, 220), 50, 0.6)
        return finish(c, rng, (20, 30, 20), ((50, 80, 50), (70, 104, 64), (24, 40, 22)),
                      ((104, 78, 52), (54, 38, 24), (150, 116, 80)), (34, 28, 24))

    def cartographer(seed=20):
        """An old map on parchment: a coastline, dotted roads and a compass rose."""
        c = Canvas(W, height(ROWS), seed)
        rng = random.Random(seed)
        for y in range(T0, T1):
            for x in range(W):
                c.put(x, y, mix((236, 216, 170), (200, 170, 120), rng.uniform(0, 0.25) + (y - T0) / 300))
        for x in range(W):
            coast = T0 + 50 + int(10 * math.sin(x * 0.07) + 5 * math.sin(x * 0.19))
            for y in range(coast, T1):
                c.put(x, y, mix((120, 160, 180), (80, 120, 150), (y - coast) / 30))
            c.put(x, coast, (90, 70, 50))
        for i in range(0, 120, 4):
            c.put(20 + i, T0 + 20 + int(8 * math.sin(i * 0.1)), (140, 60, 40))
        cx, cy = 140, T0 + 26
        for a in range(0, 360, 45):
            length = 14 if a % 90 == 0 else 8
            for r in range(length):
                c.put(cx + round(r * math.cos(math.radians(a))), cy + round(r * math.sin(math.radians(a))),
                      (80, 50, 30) if a % 90 else (150, 40, 30))
        return finish(c, rng, (60, 40, 20), ((110, 80, 46), (140, 104, 62), (60, 40, 20)),
                      ((104, 78, 52), (54, 38, 24), (150, 116, 80)), (34, 28, 24))

    return [workbench, furnace, mason, architect, haggler, merchant, table, anvil, wayfarer, cartographer]

"""
The paintings behind Farming's, Foraging's, Cooking's, Alchemy's and Taming's branches, in the order
SkillsPack.SCENES lists them after the fighting skills'. skilltree_art.py adds them to its own; the helpers (canvas,
glow, vignette, the title band, the planked bottom) are its, imported when a painting is made.
"""
import math
import random

SCENES = ["fields", "pasture", "gatherer", "herbalist", "kitchen", "table", "potency", "apparatus", "pack", "stable"]


def _start(seed):
    import skilltree_art as art
    c = art.Canvas(art.W, art.height(art.ROWS), seed)
    return art, c, random.Random(seed)


def _sky(art, c, top, bottom, until):
    for y in range(art.T0, until):
        v = (y - art.T0) / max(1, until - art.T0)
        for x in range(art.W):
            c.put(x, y, art.mix(top, bottom, v))


def _finish(art, c, rng, vignette, band, planks):
    art.vignette(c, 0, art.T0, art.W, art.T1, vignette, strength=0.5, reach=18)
    art.title_band(c, *band)
    art.chrome_and_inventory(c, rng, *planks)
    return c


def _disc(c, cx, cy, r, colour, art, soft=0.0):
    reach = int(r + soft + 2)
    for y in range(int(cy) - reach, int(cy) + reach + 1):
        for x in range(int(cx) - reach, int(cx) + reach + 1):
            if not (art.T0 <= y < art.T1 and 0 <= x < art.W):
                continue
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                c.put(x, y, colour)
            elif soft and d <= r + soft:
                c.tint(x, y, colour, 1 - (d - r) / soft)


def scene_fields(seed=11):
    """Wheat ripe to the horizon under a low sun, furrows running away."""
    art, c, rng = _start(seed)
    horizon = art.T0 + 30
    _sky(art, c, (250, 214, 140), (255, 240, 200), horizon)
    for y in range(horizon, art.T1):
        depth = (y - horizon) / (art.T1 - horizon)
        for x in range(art.W):
            row = math.sin((x - art.W / 2) / (4 + depth * 22) * 2.2 + depth * 9)
            base = art.mix((196, 160, 60), (226, 186, 72), 0.5 + row * 0.5)
            c.put(x, y, art.shade(base, 0.75 + depth * 0.3 + rng.uniform(-0.04, 0.04)))
        if rng.random() < 0.6:
            for x in range(0, art.W, rng.randint(3, 6)):
                c.put(x, y - 1, (240, 214, 120))
    _disc(c, 132, horizon - 4, 11, (255, 236, 170), art, soft=6)
    art.glow(c, 132, horizon - 4, (255, 210, 120), 80, 0.35)
    return _finish(art, c, rng, (60, 34, 8), ((96, 70, 20), (124, 94, 32), (48, 32, 8)),
                   ((120, 92, 56), (60, 44, 24), (164, 128, 84), (40, 30, 20)))


def scene_pasture(seed=12):
    """Green hills under a fair sky, a fence, sheep grazing."""
    art, c, rng = _start(seed)
    _sky(art, c, (140, 196, 240), (214, 236, 250), art.T1)
    for cx in (30, 96, 150):
        for y in range(art.T0 + 6, art.T0 + 16):
            for x in range(cx - 18, cx + 18):
                if math.hypot((x - cx) / 18, (y - art.T0 - 11) / 5) < 1 and 0 <= x < art.W:
                    c.tint(x, y, (255, 255, 255), 0.75)
    for layer, (colour, base, amp) in enumerate([((110, 170, 80), 40, 8), ((86, 150, 62), 56, 6),
                                                  ((70, 130, 50), 72, 4)]):
        for x in range(art.W):
            top = art.T0 + base + amp * math.sin(x / (20 + layer * 9) + layer * 2)
            for y in range(int(top), art.T1):
                c.put(x, y, art.shade(colour, 0.92 + 0.08 * math.sin(x * 0.7 + y)))
    fence_y = art.T0 + 62
    for x in range(art.W):
        c.put(x, fence_y, (120, 86, 52))
        c.put(x, fence_y + 5, (120, 86, 52))
    for x in range(4, art.W, 16):
        for y in range(fence_y - 3, fence_y + 12):
            c.put(x, y, (100, 70, 42))
            c.put(x + 1, y, (140, 104, 66))
    for sx, sy in ((40, art.T0 + 50), (110, art.T0 + 46), (150, art.T0 + 70), (70, art.T0 + 76)):
        for y in range(sy - 4, sy + 4):
            for x in range(sx - 6, sx + 6):
                if math.hypot((x - sx) / 6, (y - sy) / 4) < 1:
                    c.put(x, y, art.shade((240, 238, 230), 0.9 + rng.uniform(0, 0.1)))
        for y in range(sy - 2, sy + 2):
            c.put(sx + 6, y, (60, 50, 44))
            c.put(sx + 7, y, (60, 50, 44))
    return _finish(art, c, rng, (10, 30, 10), ((40, 80, 30), (56, 104, 40), (20, 40, 14)),
                   ((112, 86, 56), (56, 42, 26), (156, 122, 84), (36, 30, 22)))


def scene_gatherer(seed=13):
    """Berry bushes at dusk, a hive hanging from a branch, bees about."""
    art, c, rng = _start(seed)
    _sky(art, c, (250, 150, 90), (120, 60, 90), art.T1)
    for cx, cy, r in ((24, 70, 26), (84, 78, 30), (150, 66, 28), (120, 92, 22), (40, 96, 20)):
        for y in range(art.T0 + cy - r, art.T0 + cy + r):
            for x in range(cx - r - 4, cx + r + 4):
                if 0 <= x < art.W and art.T0 <= y < art.T1:
                    if math.hypot((x - cx) / (r + 4), (y - art.T0 - cy) / r) < 1:
                        c.put(x, y, art.shade((40, 86, 44), 0.7 + 0.3 * math.sin(x * 0.9 + y * 1.3)))
        for _ in range(r):
            bx, by = cx + rng.randint(-r, r), art.T0 + cy + rng.randint(-r // 2, r // 2)
            if 0 <= bx < art.W - 1 and art.T0 <= by < art.T1 - 1:
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    c.put(bx + dx, by + dy, (210, 30, 60) if (dx + dy) else (255, 120, 140))
    for x in range(100, art.W):
        c.put(x, art.T0 + 8, (70, 44, 26))
        c.put(x, art.T0 + 9, (50, 30, 18))
    for y in range(art.T0 + 10, art.T0 + 26):
        w = 7 - abs(y - art.T0 - 18) // 2
        for x in range(128 - w, 128 + w):
            c.put(x, y, (230, 180, 60) if (y % 3) else (180, 130, 30))
    for _ in range(9):
        bx, by = rng.randint(100, 170), rng.randint(art.T0 + 14, art.T0 + 44)
        c.put(bx, by, (250, 210, 40))
        c.put(bx + 1, by, (30, 24, 10))
    return _finish(art, c, rng, (40, 10, 30), ((86, 36, 46), (112, 50, 60), (40, 14, 20)),
                   ((104, 76, 54), (52, 36, 26), (150, 114, 82), (36, 26, 22)))


def scene_herbalist(seed=14):
    """A shady wood floor: red-capped mushrooms and wildflowers in pools of green light."""
    art, c, rng = _start(seed)
    _sky(art, c, (40, 70, 44), (20, 40, 26), art.T1)
    for x0 in (10, 52, 118, 160):
        for y in range(art.T0, art.T1):
            for x in range(x0, x0 + 7):
                if x < art.W:
                    c.put(x, y, art.shade((62, 46, 34), 0.8 + 0.2 * ((x - x0) / 7)))
    for gx in (36, 96, 140):
        art.glow(c, gx, art.T0 + 20, (200, 255, 170), 40, 0.35)
    ground = art.T0 + 64
    for y in range(ground, art.T1):
        for x in range(art.W):
            c.put(x, y, art.shade((54, 90, 40), 0.8 + rng.uniform(0, 0.2)))
    for mx, h in ((30, 14), (80, 18), (130, 12), (100, 10)):
        for y in range(ground - h, ground + 2):
            for x in range(mx - 2, mx + 2):
                c.put(x, y, (226, 214, 190))
        for y in range(ground - h - 7, ground - h + 1):
            w = 9 - (ground - h - y) // 1 if y > ground - h - 4 else 6
            for x in range(mx - w, mx + w):
                if 0 <= x < art.W:
                    spot = (x * 7 + y * 3) % 11 == 0
                    c.put(x, y, (250, 240, 230) if spot else (200, 36, 36))
    for _ in range(26):
        fx, fy = rng.randint(2, art.W - 3), rng.randint(ground + 2, art.T1 - 3)
        colour = rng.choice([(250, 220, 60), (120, 150, 255), (240, 120, 200), (255, 255, 255)])
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            c.put(fx + dx, fy + dy, colour)
        c.put(fx, fy, (250, 200, 40))
    return _finish(art, c, rng, (4, 14, 6), ((24, 52, 28), (36, 70, 38), (10, 24, 12)),
                   ((90, 70, 50), (44, 32, 22), (130, 104, 76), (30, 24, 18)))


def scene_kitchen(seed=15):
    """A brick hearth, a furnace's mouth aglow, pots hanging over it."""
    art, c, rng = _start(seed)
    for y in range(art.T0, art.T1):
        row = (y - art.T0) // 6
        for x in range(art.W):
            mortar = (y - art.T0) % 6 == 0 or (x + (6 if row % 2 else 0)) % 12 == 0
            brick = art.shade((150, 70, 50), 0.8 + ((x // 12 + row) % 3) * 0.07)
            c.put(x, y, (80, 60, 50) if mortar else brick)
    mx, my = art.W // 2, art.T0 + 58
    for y in range(my - 18, my + 18):
        for x in range(mx - 30, mx + 30):
            d = math.hypot((x - mx) / 30, (y - my) / 18)
            if d < 1 and y < my + 14:
                c.put(x, y, art.mix((255, 220, 120), (200, 60, 20), d))
    art.glow(c, mx, my, (255, 150, 60), 90, 0.45)
    for px in (40, 136):
        for y in range(art.T0, art.T0 + 14):
            c.put(px, y, (60, 60, 64))
        for y in range(art.T0 + 14, art.T0 + 26):
            w = 10 - abs(y - art.T0 - 20)
            for x in range(px - w, px + w):
                c.put(x, y, art.shade((70, 70, 78), 0.8 + 0.2 * (x - px + w) / max(1, 2 * w)))
    return _finish(art, c, rng, (30, 8, 2), ((80, 30, 18), (110, 44, 26), (40, 14, 8)),
                   ((106, 76, 52), (54, 38, 24), (150, 112, 78), (38, 28, 20)))


def scene_table(seed=16):
    """A laid table by candlelight: bread, a pie, a steaming bowl."""
    art, c, rng = _start(seed)
    _sky(art, c, (40, 28, 22), (24, 16, 12), art.T1)
    top = art.T0 + 46
    for y in range(top, art.T1):
        for x in range(art.W):
            c.put(x, y, art.shade((140, 96, 58), 0.8 + 0.2 * math.sin(x * 0.4 + (y - top) * 0.1)
                                  + rng.uniform(-0.03, 0.03)))
    c.hline(0, art.W, top, (90, 60, 34))
    for cx in (60, 120):
        for y in range(top - 22, top):
            for x in range(cx - 2, cx + 2):
                c.put(x, y, (240, 230, 200))
        _disc(c, cx, top - 26, 2, (255, 220, 120), art, soft=2)
        art.glow(c, cx, top - 26, (255, 200, 120), 60, 0.4)
    _disc(c, 32, top + 18, 12, (220, 220, 214), art)
    _disc(c, 32, top + 17, 8, (200, 150, 70), art)
    _disc(c, 90, top + 22, 13, (220, 220, 214), art)
    _disc(c, 90, top + 21, 9, (230, 160, 60), art)
    _disc(c, 148, top + 18, 11, (150, 100, 60), art)
    _disc(c, 148, top + 17, 8, (170, 90, 50), art)
    for y in range(top - 6, top + 6):
        c.tint(148 + int(3 * math.sin(y * 0.8)), y, (255, 255, 255), 0.4)
    return _finish(art, c, rng, (10, 4, 2), ((60, 36, 20), (84, 52, 30), (28, 16, 8)),
                   ((110, 80, 54), (56, 40, 26), (156, 118, 82), (40, 30, 22)))


def scene_potency(seed=17):
    """A dark shelf of bottles, each glowing its own colour."""
    art, c, rng = _start(seed)
    _sky(art, c, (24, 20, 40), (12, 10, 22), art.T1)
    for shelf in (art.T0 + 34, art.T0 + 74):
        for y in range(shelf, shelf + 4):
            for x in range(art.W):
                c.put(x, y, art.shade((100, 70, 44), 1 - (y - shelf) * 0.12))
        for bx in range(14, art.W - 8, 22):
            colour = rng.choice([(240, 60, 80), (90, 140, 255), (120, 230, 120), (230, 120, 255), (255, 190, 60)])
            for y in range(shelf - 16, shelf):
                for x in range(bx - 6, bx + 6):
                    neck = y < shelf - 12 and abs(x - bx) > 1
                    if neck:
                        continue
                    body = math.hypot((x - bx) / 6, (y - shelf + 6) / 7) < 1 or y < shelf - 11
                    if body:
                        c.put(x, y, art.mix(colour, (255, 255, 255), 0.3 if x < bx - 2 else 0.0))
            art.glow(c, bx, shelf - 6, colour, 22, 0.35)
    return _finish(art, c, rng, (4, 2, 10), ((34, 20, 56), (50, 30, 80), (14, 8, 26)),
                   ((84, 66, 72), (42, 32, 36), (124, 102, 112), (28, 22, 26)))


def scene_apparatus(seed=18):
    """A brewing stand on stone, a blue flame under it, steam curling up."""
    art, c, rng = _start(seed)
    for y in range(art.T0, art.T1):
        row = (y - art.T0) // 9
        for x in range(art.W):
            mortar = (y - art.T0) % 9 == 0 or (x + (11 if row % 2 else 0)) % 22 == 0
            stone = art.shade((96, 92, 104), 0.82 + ((x // 22 + row) % 3) * 0.05 + rng.uniform(-0.04, 0.04))
            c.put(x, y, (44, 42, 50) if mortar else stone)
    cx, base = art.W // 2, art.T0 + 82
    for y in range(base - 46, base):
        c.put(cx, y, (190, 160, 70))
        c.put(cx + 1, y, (140, 110, 40))
    for arm in (-1, 1):
        for i in range(26):
            x, y = cx + arm * i, base - 26 + i // 3
            c.put(x, y, (180, 150, 60))
        _disc(c, cx + arm * 26, base - 12, 7, (110, 180, 255), art, soft=2)
    _disc(c, cx, base - 6, 7, (200, 120, 255), art, soft=2)
    for y in range(base - 2, base + 4):
        for x in range(cx - 18, cx + 18):
            c.put(x, y, (70, 70, 74))
    art.glow(c, cx, base + 2, (90, 160, 255), 40, 0.5)
    for y in range(art.T0, base - 46):
        rise = (base - 46 - y) / max(1, base - 46 - art.T0)
        x0 = cx + 6 * math.sin(y * 0.18) * (0.4 + rise)
        width = 2 + rise * 7
        for x in range(int(x0 - width), int(x0 + width) + 1):
            if 0 <= x < art.W:
                c.tint(x, y, (230, 230, 240), 0.35 * (1 - abs(x - x0) / (width + 1)) * (1 - rise * 0.6))
    return _finish(art, c, rng, (6, 6, 12), ((40, 42, 60), (56, 58, 84), (16, 16, 26)),
                   ((86, 72, 66), (42, 34, 30), (126, 108, 98), (28, 22, 20)))


def scene_pack(seed=19):
    """A snowy wood under the moon, a wolf's eyes shining out of the dark."""
    art, c, rng = _start(seed)
    _sky(art, c, (30, 40, 80), (14, 18, 40), art.T1)
    _disc(c, 140, art.T0 + 16, 9, (240, 244, 250), art, soft=10)
    ground = art.T0 + 70
    for x0 in range(-4, art.W, 22):
        h = rng.randint(36, 60)
        for y in range(ground - h, ground):
            w = (y - (ground - h)) // 3 + 1
            for x in range(x0 + 10 - w, x0 + 10 + w):
                if 0 <= x < art.W:
                    c.put(x, y, (16, 30, 26))
    for y in range(ground, art.T1):
        for x in range(art.W):
            c.put(x, y, art.shade((220, 228, 240), 0.8 + 0.2 * (y - ground) / (art.T1 - ground)))
    for ex, ey in ((60, ground - 10), (100, ground - 14)):
        for dx in (0, 5):
            c.put(ex + dx, ey, (255, 230, 120))
            c.put(ex + dx + 1, ey, (255, 230, 120))
        art.glow(c, ex + 3, ey, (255, 220, 120), 10, 0.3)
    return _finish(art, c, rng, (2, 4, 12), ((20, 26, 50), (30, 38, 72), (8, 10, 22)),
                   ((92, 80, 76), (46, 38, 36), (134, 120, 116), (30, 26, 26)))


def scene_stable(seed=20):
    """A meadow at sunset: hay bales, a fence, a horse against the sky."""
    art, c, rng = _start(seed)
    horizon = art.T0 + 54
    _sky(art, c, (250, 120, 70), (255, 210, 130), horizon)
    _disc(c, 40, horizon - 12, 11, (255, 236, 170), art, soft=8)
    for y in range(horizon, art.T1):
        for x in range(art.W):
            c.put(x, y, art.shade((110, 140, 50), 0.75 + 0.25 * (y - horizon) / (art.T1 - horizon)))
    hx, hy = 120, horizon
    for y in range(hy - 26, hy):
        for x in range(hx - 20, hx + 22):
            body = math.hypot((x - hx) / 16, (y - hy + 18) / 7) < 1
            neck = hx + 10 <= x <= hx + 16 and hy - 28 <= y <= hy - 16
            head = math.hypot((x - hx - 18) / 5, (y - hy + 27) / 3) < 1
            legs = y > hy - 12 and ((x - hx + 12) % 22 in (0, 1) or (x - hx + 4) % 22 in (0, 1))
            if body or neck or head or legs:
                c.put(x, y, (40, 24, 20))
    for bx in (30, 64):
        for y in range(horizon + 14, horizon + 26):
            for x in range(bx - 9, bx + 9):
                c.put(x, y, (226, 190, 70) if (x + y) % 4 else (180, 140, 40))
    for x in range(art.W):
        c.put(x, horizon + 6, (110, 76, 46))
    for x in range(6, art.W, 18):
        for y in range(horizon, horizon + 12):
            c.put(x, y, (90, 60, 36))
    return _finish(art, c, rng, (40, 16, 6), ((90, 44, 24), (120, 62, 34), (44, 20, 10)),
                   ((114, 84, 56), (58, 42, 26), (160, 124, 86), (40, 30, 22)))


PAINTERS = [scene_fields, scene_pasture, scene_gatherer, scene_herbalist, scene_kitchen, scene_table,
            scene_potency, scene_apparatus, scene_pack, scene_stable]

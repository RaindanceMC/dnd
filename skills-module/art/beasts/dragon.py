"""
The dragon: a western dragon on four legs with a pair of bat wings — horned head on a long S-neck, a crest of spines
from brow to tail, amber belly plates, a spade at the tail's end. Companion that fights and grows; ridden at
mastery. Built at full size; BetterModel scales the young ones down.

    python3 dragon.py [gold|crystal|verdant ...]   # builds each style in a running Blockbench: dragon_<style>.bbmodel,
                                                  # its texture and preview sheets
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFilter

import beastkit as kit
from beastkit import (Bone, armour_plate, feathers, gait, hexrgb, keys, mirror, mix, noise, plate_edge, ramp_at,
                      smooth, wave, with_)

HERE = os.path.dirname(os.path.abspath(__file__))

# One model, three dragons: the style sets every colour (scales, belly plates, horns, the wing blades, the eyes) and
# how a blade is drawn — a feather with a shaft (gold), a crystal shard in two facets (crystal), a leaf with veins
# (verdant). Each style builds its own .bbmodel.
STYLES = {
    "gold": {"head": "crown", "scale": ("#7d7466", "#bdb5a6", "#e2dccf", "#f6f2ea", "#ffffff"),
             "belly": ("#7a4a0e", "#b97c16", "#e2a828", "#f8d65a"),
             "horn": ("#6a4410", "#a8701c", "#dca83a", "#f6d870"),
             "blade": ("#fbf8f0", "#f2e6c8", "#f2b440", "#e8780c"), "blade_line": "#a87a3a",
             "under": ("#d8d0c0", "#c8b48c", "#e09a3a", "#c0600e"), "kind": "feather",
             "eye": ("#1c6ab8", "#7ad0ff")},
    "crystal": {"head": "shards", "scale": ("#3a0c2c", "#6c1a52", "#a02c7a", "#cc4a9e", "#ec86c6"),
                "belly": ("#4a1440", "#76245e", "#a8407e", "#d26aa4"),
                "horn": ("#7a2a5c", "#c45a98", "#f08cc4", "#ffd0ea"),
                "blade": ("#ffd6ec", "#ff9ccc", "#ff5aa8", "#c81e78"), "blade_line": "#8a1458",
                "under": ("#f0a8cc", "#e07ab0", "#d04c94", "#9a1662"), "kind": "crystal",
                "eye": ("#e8e8ff", "#ffffff")},
    "verdant": {"head": "leaf", "scale": ("#1a3a0e", "#2f6418", "#4e9424", "#7cc436", "#b4ea66"),
                "belly": ("#6a5a12", "#a8901e", "#d8c040", "#f2e47a"),
                "horn": ("#6a4410", "#a8701c", "#dca83a", "#f6d870"),
                "blade": ("#9ae84a", "#5cc42a", "#2e8c18", "#1a5a0e"), "blade_line": "#0e3a08",
                "under": ("#7cc444", "#4e9c26", "#2c7016", "#164a0a"), "kind": "leaf",
                "eye": ("#d84a0a", "#ffc040")},
    "ember": {"head": "ram", "scale": ("#1c0c0a", "#3a1410", "#5e1e14", "#86301a", "#b04a22"),
              "belly": ("#6a2a0a", "#a8480e", "#e07a18", "#ffb43a"),
              "horn": ("#2a2420", "#4a403a", "#6e625a", "#9a8c80"),
              "blade": ("#2a1210", "#5a1c12", "#e0501a", "#ffc23a"), "blade_line": "#120604",
              "under": ("#3a1810", "#7a2a14", "#e06a1e", "#ffd060"), "kind": "crystal",
              "eye": ("#ff6a00", "#ffe060")},
    "jade": {"head": "crown", "scale": ("#0a302a", "#124e44", "#1c705e", "#2e9a7e", "#62c6a0"),
             "belly": ("#9a7430", "#cca450", "#ecca80", "#fff0c4"),
             "horn": ("#6a4410", "#a8701c", "#dca83a", "#f6d870"),
             "blade": ("#62c6a0", "#2e9a7e", "#1c705e", "#124e44"), "blade_line": "#082420",
             "under": ("#62c6a0", "#2e9a7e", "#1c705e", "#124e44"), "kind": "leaf",
             "eye": ("#c81818", "#ffd040"), "mane": ("#c49a50", "#e6cc8c", "#fbf1d8")},
}
STYLE = {}
SCALE = BELLY = HORN = BLADE = UNDER = EYE = MANE = []
BLADE_LINE = None
BONE_ = [hexrgb(h) for h in ("#463c30", "#776a56", "#ad9f80", "#ddd2b6")]
DARK = hexrgb("#120406")


def apply_style(name):
    global STYLE, SCALE, BELLY, HORN, BLADE, UNDER, EYE, BLADE_LINE
    STYLE = STYLES[name]
    SCALE, BELLY, HORN, BLADE, UNDER, EYE = ([hexrgb(h) for h in STYLE[k]] for k in
                                             ("scale", "belly", "horn", "blade", "under", "eye"))
    BLADE_LINE = hexrgb(STYLE["blade_line"])
    global MANE
    MANE = [hexrgb(h) for h in STYLE.get("mane", ("#c49a50", "#e6cc8c", "#fbf1d8"))]


# ------------------------------------------------------------------------------------------------ painting

def belly(tile, rng):
    """Transverse belly plates: bands across the body, lit along their front edge, a dark seam behind each."""
    px = tile.load()
    size = tile.size[0]
    for y in range(size):
        for x in range(size):
            k = 0.75 - 0.5 * (y % 5) / 5 + 0.05 * ((x // 3 + y // 5) % 2)
            c = ramp_at(BELLY, k)
            if y % 5 == 4:
                c = BELLY[0]
            px[x, y] = c + (255,)


def horn(tile, rng):
    """Horn and spine: smooth bone, darker to the base, with growth rings."""
    px = tile.load()
    size = tile.size[0]
    for y in range(size):
        for x in range(size):
            c = ramp_at(HORN, 0.9 - 0.7 * y / size)
            if y % 4 == 3:
                c = mix(c, HORN[0], 0.35)
            px[x, y] = c + (255,)


def big_eye(image):
    """A big round eye painted to the face's own size: dark rim, amber iris lighter below, a large pupil, two
    catch-lights up front — most of the cute is this."""
    w, h = image.size
    px = image.load()
    cx, cy, r = (w - 1) / 2, (h - 1) / 2, min(w, h) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy) / r
            if d > 1.0:
                px[x, y] = SCALE[2] + (255,)
                continue
            if d > 0.82:
                c = hexrgb("#2a0e10")
            elif math.hypot(x - cx, y - cy - 0.3) / r < 0.42:
                c = (18, 10, 14)
            else:
                c = mix(EYE[0], EYE[1], max(0.0, (y - cy) / r + 0.3))
            for hx, hy, hr in ((-0.3, -0.35, 0.22), (0.25, 0.2, 0.1)):
                if math.hypot(x - cx - hx * r, y - cy - hy * r) / r < hr:
                    c = (255, 255, 255)
            px[x, y] = c + (255,)


def mane(tile, rng):
    """Long hair: streaks of light and shade running down, lighter at the ends."""
    px = tile.load()
    size = tile.size[0]
    streak = [rng.uniform(-0.2, 0.2) for _ in range(size)]
    for y in range(size):
        for x in range(size):
            px[x, y] = ramp_at(MANE, 0.3 + 0.6 * y / size + streak[x] + (0.1 if x % 3 == 0 else 0)) + (255,)


def paint_atlas():
    atlas = kit.Atlas()
    atlas.tile("scale", lambda t, r: feathers(t, r, SCALE[1:], 6, 5, SCALE[0]), 1)
    atlas.tile("plates", lambda t, r: feathers(t, r, SCALE[1:], 10, 8, SCALE[0]), 2)
    atlas.tile("belly", belly, 3)
    atlas.tile("horn", horn, 4)
    atlas.tile("claw", lambda t, r: smooth(t, r, [(10, 8, 8), (34, 28, 26), (78, 70, 64)], 0.9, 0.0), 5)
    atlas.tile("teeth", lambda t, r: smooth(t, r, BONE_[1:] + [hexrgb("#f4eedd")], 1.0, 0.5), 6)
    atlas.tile("mouth", lambda t, r: smooth(t, r, [hexrgb("#2a0608"), hexrgb("#5a1418"), hexrgb("#8a2a2c")], 0.8, 0.1), 7)
    atlas.tile("skin", lambda t, r: smooth(t, r, SCALE[1:], 0.8, 0.3), 9)
    atlas.tile("leather", lambda t, r: smooth(t, r, [hexrgb(h) for h in ("#2e1c10", "#4a2e18", "#6a4426", "#8a5c34")],
                                              0.9, 0.2), 10)
    atlas.tile("plate_edge", plate_edge, 11)
    atlas.tile("mane", mane, 12)
    return atlas


# ------------------------------------------------------------------------------------------------ the wing
#
# A bat wing: arm and three long fingers, the membrane stretched between them and back to the flank, its trailing
# edge curving in between the finger tips. The membrane is painted once over the whole spread wing (veins fanning
# from the wrist, cells lighter between them, darker along bones and edges) and each wing bone carries the part of
# it that lies on its side of the joints, so the panels meet without seams and nothing overlaps.

SHOULDER = (6.0, -7.0)
ARM = (("wing_humerus", SHOULDER, 18.0, -25.0), ("wing_forearm", (18.0, -7.0), 34.0, 50.0),
       ("wing_hand", (34.0, -7.0), None, -28.0))   # bone, pivot (x, z), arm end x, rotation about y
FINGERS = ((0.0, 30.0), (-22.5, 28.0), (-45.0, 24.0))   # cube rotation in the hand, length
FLANK = (6.5, 15.0)   # where the membrane meets the body
W_PX = 4               # pixels per unit of the painted membrane
WING_SCALE = 1.6
HEAD = 1.12   # on top of the nightwing head, already big and blocky
LEGS = 1.0


def turn(p, pivot, degrees):
    """Blockbench's rotation about y in the x/z plane: +degrees swings +x towards -z."""
    a = math.radians(degrees)
    dx, dz = p[0] - pivot[0], p[1] - pivot[1]
    return pivot[0] + dx * math.cos(a) + dz * math.sin(a), pivot[1] - dx * math.sin(a) + dz * math.cos(a)


def to_wing(bone, p):
    """A point given in a wing bone's own (rest) coordinates, placed where the bent arm puts it."""
    chain = [b for b in ARM[:[a[0] for a in ARM].index(bone) + 1]]
    for name, pivot, _, degrees in reversed(chain):
        p = turn(p, pivot, degrees)
    return p


def finger_tip(n):
    angle, length = FINGERS[n]
    wrist = ARM[2][1]
    return to_wing("wing_hand", turn((wrist[0] + length, wrist[1]), wrist, angle))


def membrane_outline():
    elbow, wrist = to_wing("wing_humerus", (18.0, -7.0)), to_wing("wing_forearm", (34.0, -7.0))
    tips = [finger_tip(n) for n in range(3)]
    lead = [(SHOULDER[0], SHOULDER[1] - 1.0), (elbow[0], elbow[1] - 1.2), (wrist[0], wrist[1] - 1.2),
            (tips[0][0] + 0.5, tips[0][1] - 0.6)]

    def scallop(a, b, depth=0.3, steps=12):
        # the free edge between two tips: a curve pulled in towards the wrist
        mx, mz = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        cx, cz = mx + (wrist[0] - mx) * depth, mz + (wrist[1] - mz) * depth
        return [((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * cx + t * t * b[0],
                 (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * cz + t * t * b[1]) for t in (i / steps for i in range(1, steps))]
    trail = [tips[0]] + scallop(tips[0], tips[1]) + [tips[1]] + scallop(tips[1], tips[2]) + [tips[2]] + \
        scallop(tips[2], FLANK, 0.22) + [FLANK]
    return lead + trail, elbow, wrist, tips


def resample(points, n):
    """n points evenly along a polyline."""
    lengths = [math.dist(points[i], points[i + 1]) for i in range(len(points) - 1)]
    total, out = sum(lengths), []
    for k in range(n):
        target, i = total * k / (n - 1), 0
        while i < len(lengths) - 1 and target > lengths[i]:
            target -= lengths[i]
            i += 1
        t = min(1.0, target / lengths[i]) if lengths[i] else 0.0
        a, b = points[i], points[i + 1]
        out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    return out


def blades():
    """The wing's blades as (base, tip, width), innermost first: long two-tone blades fanning from the wrist across
    the hand and from the arm back to the flank, each tip reaching a little past the scalloped edge so the edge
    is the blades' own points."""
    outline, elbow, wrist, tips = membrane_outline()
    trail = outline[4:]                      # from the first finger's tip round to the flank
    hand_part = trail[:trail.index(tips[2]) + 1]
    arm_part = trail[trail.index(tips[2]):]
    out = []
    arm_line = [wrist, elbow, SHOULDER, (SHOULDER[0] + 0.5, SHOULDER[1] + 6.0)]
    arm_tips = resample(arm_part, 10)
    for k, tip in enumerate(reversed(arm_tips)):          # flank first: it lies underneath
        f = k / (len(arm_tips) - 1)
        base = resample(arm_line, 11)[10 - round(f * 10)]
        out.append((base, tip, 3.6))
    hand_tips = resample(hand_part, 13)
    for k, tip in enumerate(reversed(hand_tips)):
        base = (wrist[0] + (tip[0] - wrist[0]) * 0.18, wrist[1] + (tip[1] - wrist[1]) * 0.18)
        out.append((base, tip, 4.2))
    return out, outline, elbow, wrist, tips


def blade_colour(f, across, ramp, kind):
    """The colour along a blade (f 0 at the base .. 1 at the tip) and across it (-1 .. 1)."""
    c = ramp_at(ramp, 0.15 + 0.85 * f ** 1.4)
    if kind == "feather" and abs(across) < 0.12:
        c = mix(c, (255, 255, 255), 0.5)                       # the shaft
    elif kind == "crystal":
        c = mix(c, (255, 255, 255), 0.28) if across < 0 else mix(c, ramp[-1], 0.25)   # two facets
    elif kind == "leaf":
        if abs(across) < 0.1 or abs(abs(across) - (f * 3.0) % 1.0) < 0.08:
            c = mix(c, ramp[-1], 0.55)                         # midrib and side veins
    return c


def membrane_images():
    """(top, under, owner) painted over the whole spread wing in wing coordinates: first the membrane, then the
    blades over it from the flank out to the leading finger; owner says which bone's panel shows each pixel."""
    fan, outline, elbow, wrist, tips = blades()
    x0, z0, w, h = -4.0, -16.0, 80, 58
    size = (int(w * W_PX), int(h * W_PX))

    def px(p):
        return ((p[0] - x0) * W_PX, (p[1] - z0) * W_PX)

    def world(i, j):
        return (x0 + (i + 0.5) / W_PX, z0 + (j + 0.5) / W_PX)
    shapes = []
    for base, tip, width in fan:
        d = (tip[0] - base[0], tip[1] - base[1])
        length = math.hypot(*d)
        u = (d[0] / length, d[1] / length)
        n = (-u[1], u[0])
        tip2 = (tip[0] + u[0] * 1.6, tip[1] + u[1] * 1.6)

        def at(f, side, hw):
            return (base[0] + d[0] * f + n[0] * side * hw, base[1] + d[1] * f + n[1] * side * hw)
        poly = [at(0, -1, width * 0.35), at(0.75, -1, width * 0.55), tip2, at(0.75, 1, width * 0.55),
                at(0, 1, width * 0.35)]
        shapes.append((poly, base, u, n, length + 1.6, width))
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.polygon([px(p) for p in outline], fill=255)
    for poly, *_ in shapes:
        draw.polygon([px(p) for p in poly], fill=255)
    m = mask.load()
    images = []
    for ramp in (BLADE, UNDER):
        image = Image.new("RGBA", size, (0, 0, 0, 0))
        out = image.load()
        for j in range(size[1]):
            for i in range(size[0]):
                if m[i, j]:
                    out[i, j] = ramp_at(ramp, 0.35) + (255,)       # membrane between the blades
        for poly, base, u, n, length, width in shapes:
            one = Image.new("L", size, 0)
            ImageDraw.Draw(one).polygon([px(p) for p in poly], fill=255)
            inner = one.filter(ImageFilter.MinFilter(3))
            o1, i1 = one.load(), inner.load()
            bx = [px(p)[0] for p in poly]
            by = [px(p)[1] for p in poly]
            for j in range(max(0, int(min(by))), min(size[1], int(max(by)) + 1)):
                for i in range(max(0, int(min(bx))), min(size[0], int(max(bx)) + 1)):
                    if not o1[i, j]:
                        continue
                    p = world(i, j)
                    rel = (p[0] - base[0], p[1] - base[1])
                    f = max(0.0, min(1.0, (rel[0] * u[0] + rel[1] * u[1]) / length))
                    across = (rel[0] * n[0] + rel[1] * n[1]) / (width * 0.55)
                    c = blade_colour(f, across, ramp, STYLE["kind"])
                    if not i1[i, j]:
                        c = BLADE_LINE                                # the dark line round each blade
                    out[i, j] = c + (255,)
        images.append(image)

    def side_of(p, a, b):
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
    owner = Image.new("L", size, 0)
    o = owner.load()
    for j in range(size[1]):
        for i in range(size[0]):
            if not m[i, j]:
                continue
            p = world(i, j)
            if side_of(p, wrist, tips[2]) < 0.3 * math.dist(wrist, tips[2]) and p[0] > wrist[0] - 2:
                o[i, j] = 3
            elif side_of(p, elbow, ((tips[2][0] + FLANK[0]) / 2 + 4, (tips[2][1] + FLANK[1]) / 2)) < 0:
                o[i, j] = 2
            else:
                o[i, j] = 1
    return images[0], images[1], owner, (x0, z0)


def build_wing(body):
    top, under, owner, (x0, z0) = membrane_images()
    t, u, o = top.load(), under.load(), owner.load()
    w, h = top.size

    def sample(image_px, bone, number, lx, lz):
        wx, wz = to_wing(bone, (lx, lz))
        i, j = int((wx - x0) * W_PX), int((wz - z0) * W_PX)
        if 0 <= i < w and 0 <= j < h and o[i, j] == number:
            return image_px[i, j]
        return None

    def panel(bone_obj, bone, number, rect, y):
        lx0, lx1, lz0, lz1 = rect

        def painter(image_px, flip):
            def paint(image):
                iw, ih = image.size
                px = image.load()
                for j in range(ih):
                    for i in range(iw):
                        lx = lx0 + (i + 0.5) * (lx1 - lx0) / iw
                        dz = (j + 0.5) * (lz1 - lz0) / ih
                        c = sample(image_px, bone, number, lx, lz1 - dz if flip else lz0 + dz)
                        if c:
                            px[i, j] = c
            return paint
        bone_obj.cube(f"membrane_{number}_r", (lx0, y, lz0), (lx1, y + 0.25, lz1), "clear",
                      paint={"up": (f"membrane_{number}_up", painter(t, False)),
                             "down": (f"membrane_{number}_down", painter(u, True))})

    root = body.bone("wing_root_r", (5.0, 26.5, -7.0), (0, -6, 28))
    root.rounded("wing_shoulder_r", (3.6, 24.6, -9.6), (8.2, 28.8, -4.4), "plates", 1.0)
    hum = root.bone("wing_humerus_r", (6.0, 27.0, -7.0), (0, ARM[0][3], 0))
    hum.rounded("wing_humerus_r", (5.6, 25.9, -8.4), (18.4, 28.1, -5.6), "scale", 0.6)
    panel(hum, "wing_humerus", 1, (2.0, 21.0, -9.0, 26.0), 26.9)
    fore = hum.bone("wing_forearm_r", (18.0, 27.0, -7.0), (0, ARM[1][3], 0))
    fore.rounded("wing_forearm_r", (17.6, 26.2, -8.0), (34.4, 27.8, -6.0), "scale", 0.5)
    panel(fore, "wing_forearm", 2, (15.0, 36.0, -9.0, 26.0), 26.95)
    hand = fore.bone("wing_hand_r", (34.0, 27.0, -7.0), (0, ARM[2][3], 0))
    hand.rounded("wrist_r", (33.0, 25.9, -8.4), (36.0, 28.1, -5.6), "scale", 0.6)
    # the thumb: a hooked claw on the wrist's leading edge
    hand.cube("thumb_r", (33.6, 26.6, -10.4), (34.8, 27.6, -8.0), "skin")
    hand.cube("thumb_claw_r", (33.8, 26.0, -11.6), (34.6, 27.4, -10.2), "claw", rot=("x", 22.5),
              origin=(34.2, 27, -10.4))
    for n, (angle, length) in enumerate(FINGERS):
        r = ("y", angle) if angle else None
        o_ = (34.0, 27.0, -7.0)
        hand.cube(f"finger_{n}_r", (34.0, 26.3, -7.7), (34.0 + length * 0.55, 27.7, -6.3), "plates", rot=r, origin=o_)
        hand.cube(f"finger_{n}_end_r", (34.0 + length * 0.55, 26.6, -7.4), (34.0 + length, 27.4, -6.6), "plates",
                  rot=r, origin=o_)
    panel(hand, "wing_hand", 3, (32.0, 66.0, -12.0, 26.0), 27.0)
    hum.scale(WING_SCALE)   # painted and placed at life size, then grown to a fantasy span
    kit.mirror_x(root, body)


# ------------------------------------------------------------------------------------------------ the folded wing
#
# Folded, a bat wing is a narrow bundle: the forearm rides high along the side with the wrist up by the shoulder
# (thumb claw forward), and the fingers run back along the flank with the membrane gathered between them in pleats.

FOLD_LENGTH = 36.0


FOLD_DEPTH = 13.0
FOLD_PX = 6
_fold_cache = {}


def fold_image():
    """The folded wing painted once in (s back along the arm, t down from the top): the forearm as a ridge along the
    top, then the blades closed like a fan — each from the arm back and down, the rearmost laid first and each
    next one over it, their points making the lower, jagged edge."""
    key = STYLE.get("blade"), STYLE.get("kind")
    if key in _fold_cache:
        return _fold_cache[key]
    w, h = int(FOLD_LENGTH * FOLD_PX), int(FOLD_DEPTH * FOLD_PX)
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out = image.load()

    def px(p):
        return (p[0] * FOLD_PX, p[1] * FOLD_PX)
    blades_ = []
    for k in range(10):
        base = (1.5 + k * 2.3, 1.2)
        tip = (min(FOLD_LENGTH - 0.5, base[0] + 9.0 + k * 0.9), 6.5 + 5.6 * (1 - abs(k - 4.5) / 5.5))
        blades_.append((base, tip, 3.4))
    for base, tip, width in reversed(blades_):
        d = (tip[0] - base[0], tip[1] - base[1])
        length = math.hypot(*d)
        u = (d[0] / length, d[1] / length)
        n = (-u[1], u[0])
        poly = [(base[0] - n[0] * width * 0.4, base[1] - n[1] * width * 0.4),
                (base[0] + d[0] * 0.7 - n[0] * width * 0.55, base[1] + d[1] * 0.7 - n[1] * width * 0.55),
                (tip[0] + u[0] * 1.2, tip[1] + u[1] * 1.2),
                (base[0] + d[0] * 0.7 + n[0] * width * 0.55, base[1] + d[1] * 0.7 + n[1] * width * 0.55),
                (base[0] + n[0] * width * 0.4, base[1] + n[1] * width * 0.4)]
        one = Image.new("L", (w, h), 0)
        ImageDraw.Draw(one).polygon([px(p) for p in poly], fill=255)
        inner = one.filter(ImageFilter.MinFilter(3))
        o1, i1 = one.load(), inner.load()
        for j in range(h):
            for i in range(w):
                if not o1[i, j]:
                    continue
                p = ((i + 0.5) / FOLD_PX, (j + 0.5) / FOLD_PX)
                rel = (p[0] - base[0], p[1] - base[1])
                f = max(0.0, min(1.0, (rel[0] * u[0] + rel[1] * u[1]) / (length + 1.2)))
                across = (rel[0] * n[0] + rel[1] * n[1]) / (width * 0.55)
                c = blade_colour(f, across, BLADE, STYLE["kind"]) if i1[i, j] else BLADE_LINE
                out[i, j] = c + (255,)
    for j in range(h):                     # the forearm, a ridge along the top over the blades' roots
        for i in range(w):
            s_, t_ = (i + 0.5) / FOLD_PX, (j + 0.5) / FOLD_PX
            if t_ < 2.0 and s_ < 24.0 - t_:
                out[i, j] = ramp_at(SCALE, 0.75 - 0.25 * t_) + (255,)
                if t_ > 1.7:
                    out[i, j] = BLADE_LINE + (255,)
    _fold_cache[key] = image
    return image


def fold_colour(s, t):
    image = fold_image()
    i, j = int(s * FOLD_PX), int(t * FOLD_PX)
    if not (0 <= i < image.width and 0 <= j < image.height):
        return None
    c = image.getpixel((i, j))
    return c if c[3] else None


FOLD_STRIPS = ((3.4, 45.0), (4.4, 22.5), (5.2, 0.0))


def build_folded_wing(body):
    fold = body.bone("wingfold_r", (5.4, 30.4, -10.0), (0, -4, 0))
    x, y, t = 5.2, 30.4, 0.0
    for n, (depth, lean) in enumerate(FOLD_STRIPS):
        def paint(image, t0=t, depth=depth, outer=True):
            w, h = image.size
            px = image.load()
            for j in range(h):
                for i in range(w):
                    s_ = (i + 0.5) / w * FOLD_LENGTH
                    c = fold_colour(FOLD_LENGTH - s_ if outer else s_, t0 + (j + 0.5) / h * depth)
                    if c:
                        px[i, j] = c
        fold.cube(f"fold_{n}_r", (x, y - depth, -10.0), (x + 0.3, y, -10.0 + FOLD_LENGTH), "clear",
                  rot=("z", lean) if lean else None, origin=(x, y, -10.0),
                  paint={"east": (f"fold_{n}_out", paint),
                         "west": (f"fold_{n}_in", lambda image, t0=t, depth=depth: paint(image, t0, depth, False))})
        a = math.radians(lean)
        x, y, t = x + depth * math.sin(a), y - depth * math.cos(a), t + depth
    # the wrist standing up by the shoulder with its thumb claw, the forearm along the top
    fold.rounded("fold_wrist_r", (5.0, 29.2, -11.4), (7.6, 32.0, -8.6), "scale", 0.6)
    fold.cube("fold_thumb_r", (5.6, 30.2, -13.4), (6.8, 31.2, -11.2), "skin")
    fold.cube("fold_claw_r", (5.8, 29.6, -14.4), (6.6, 31.0, -13.2), "claw")
    fold.rounded("fold_forearm_r", (5.2, 28.8, -9.0), (7.4, 30.8, 6.0), "scale", 0.5)
    kit.mirror_x(fold, body)


# ------------------------------------------------------------------------------------------------ the body

def spine(bone, z_from, z_to, y, height, step=3.0, lean=22.5, x=0.0):
    """A crest of spines along the top, each leaning back, shrinking towards z_to."""
    count = max(1, int(abs(z_to - z_from) / step))
    for i in range(count):
        z = z_from + (z_to - z_from) * i / count
        hgt = height * (1 - 0.35 * i / count)
        bone.cube(f"{bone.name}_spine{i}", (x - 0.55, y - 0.4, z), (x + 0.55, y + hgt * 0.55, z + 1.7), "horn",
                  rot=("x", lean), origin=(x, y, z + 0.8))
        bone.cube(f"{bone.name}_spine{i}_tip", (x - 0.3, y + hgt * 0.5, z + 0.4), (x + 0.3, y + hgt, z + 1.3), "horn",
                  rot=("x", lean), origin=(x, y, z + 0.8))


def claws(bone, side, xs, y, z):
    """Hooked claws on a foot's front edge at z: a thick base and a tip curling down."""
    for i, x in enumerate(xs):
        bone.cube(f"claw{i}_{side}", (x - 0.4, y, z - 1.6), (x + 0.4, y + 1.3, z), "claw")
        bone.cube(f"claw{i}_tip_{side}", (x - 0.3, y - 0.3, z - 2.4), (x + 0.3, y + 0.7, z - 1.5), "claw")


def chunky(leg, extra=0.5):
    """Short legs read cute when they stay thick: every cube a little fuller."""
    for b in leg.walk():
        for c in b.cubes:
            if c["material"] != "claw":
                c["inflate"] += extra


def neck_and_head(chest, suffix="", x=0.0, yaw=0.0):
    """A neck of four segments rising out of the chest and a crowned head on it, with its helm. suffix tells two
    heads apart (twin-head: _r and _l); x and yaw set the neck off to a side and turn it out."""
    parent, z, y = chest, -15.0, 24.0
    for i, (angle, r) in enumerate(zip((34, 10, -12, -22), (5.2, 4.8, 4.5, 4.3))):
        name = f"hi_neck_2{suffix}" if i == 1 else f"neck_{i + 1}{suffix}"
        seg = parent.bone(name, (x, y, z), (angle, yaw if i == 0 else 0, 0))
        seg.rounded(f"neck_{i + 1}{suffix}", (x - r, y - r, z - 4.8), (x + r, y + r, z + 0.6), "scale", 1.0,
                    {"down": "belly", "up": "plates"})
        spine(seg, z - 4.4, z, y + r - 0.3, 3.0 - i * 0.3, step=2.4, x=x)
        parent, z = seg, z - 4.4
    head = parent.bone(f"head{suffix}", (x, y, z), (-12, 0, 0))
    loy_head(head, (x, y, z), suffix, HEADS[STYLE["head"]])
    head.scale(HEAD)
    return head



# ------------------------------------------------------------------------------------------------ the head
#
# Every head is built on the nightwing's (Loy's Goodies, CC0 — vendor/loy_dragon.bbmodel): a big blocky skull with a
# closed, heavy jaw, thick brows over the eyes, pixel fins at the sides. Read from the file, brought to our size,
# painted with the dragon's materials, and varied per dragon: snout length, the horns, fins or not, a nose horn.

LOY = os.path.join(HERE, "vendor", "loy_dragon.bbmodel")
LOY_PIVOT = (0.0, 54.27, -47.35)
LOY_SIZE = 0.62
HEADS = {   # snout: length; width / height: of the whole head; jaw: depth of the lower jaw
    "crown": {"snout": 1.35, "width": 0.82, "height": 0.95, "jaw": 0.8, "horns": "crown", "fan": True,
              "nose": True, "mask": True},
    "shards": {"snout": 0.7, "width": 1.2, "height": 1.05, "jaw": 1.0, "horns": "shards", "fan": False,
               "nose": False, "brow_shards": True},
    "leaf": {"snout": 0.95, "width": 1.0, "height": 1.1, "jaw": 1.45, "horns": "hooks", "fan": False,
             "nose": False, "frill": True, "beard": True},
    "ram": {"snout": 0.9, "width": 1.3, "height": 1.15, "jaw": 1.3, "horns": "ram", "fan": False, "nose": True,
            "cheeks": True, "tusks": True, "underbite": True},
}
_loy = {}


def loy_groups():
    """The nightwing's head as {group name: [elements]}, read once."""
    if not _loy:
        with open(LOY) as f:
            model = json.load(f)
        elements = {e["uuid"]: e for e in model["elements"]}

        def find(nodes, name):
            for n in nodes:
                if isinstance(n, dict):
                    if n["name"] == name:
                        return n
                    hit = find(n.get("children", []), name)
                    if hit:
                        return hit

        def collect(node, into):
            for c in node.get("children", []):
                if isinstance(c, str):
                    into.append(elements[c])
                else:
                    collect(c, into)
        head = find(model["outliner"], "head")
        _loy["skull"] = [elements[c] for c in head["children"] if isinstance(c, str)]
        for child in head["children"]:
            if isinstance(child, dict):
                _loy[child["name"]] = []
                collect(child, _loy[child["name"]])
    return _loy


def loy_head(head, at, suffix, look):
    x, y, hz = at
    snout = look["snout"]

    width, height = look.get("width", 1.0), look.get("height", 1.0)

    def place(p, jaw=False):
        lx, ly, lz = (p[i] - LOY_PIVOT[i] for i in range(3))
        if lz < -6.0:                     # the snout: everything in front of the eyes stretches
            lz = -6.0 + (lz + 6.0) * snout
        if jaw and ly < 0.0:              # a deeper (or shallower) lower jaw
            ly *= look.get("jaw", 1.0)
        if jaw and look.get("underbite") and lz < -14.0:
            lz -= 1.6
        return [x + lx * LOY_SIZE * width, y + ly * LOY_SIZE * height, hz + lz * LOY_SIZE]

    def add(bone, element, name, material, faces=None, jaw=False):
        if any(abs(element["to"][i] - element["from"][i]) < 0.01 for i in range(3)):
            return                        # a flat plane shaped by its texture's transparency: not ours
        rot = element.get("rotation") or [0, 0, 0]
        axis = next(((a, v) for a, v in zip("xyz", rot) if v), None)
        frm, to = place(element["from"], jaw), place(element["to"], jaw)
        frm, to = [min(frm[i], to[i]) for i in range(3)], [max(frm[i], to[i]) for i in range(3)]
        bone.cube(name, frm, to, material, rot=axis, origin=place(element.get("origin", element["from"])),
                  faces=faces)
    g = loy_groups()
    for n, e in enumerate(g["skull"]):
        top = e["from"][1] > 59.0
        add(head, e, f"skull{n}{suffix}", "plates" if top else "scale", {"down": "belly"})
    for n, e in enumerate(g["eyebrow"]):
        add(head, e, f"brow{n}{suffix}", "horn")
    for n, e in enumerate(g["upmouse"]):
        if e["from"][1] > 56.9 and not look["nose"]:
            continue                       # the nose horn
        add(head, e, f"snout{n}{suffix}", "horn" if e["from"][1] > 56.9 else "scale",
            {"down": "mouth", "up": "horn"} if look.get("mask") else {"down": "mouth"})
    jaw = head.bone(f"jaw{suffix}", place((0, 56, -54.5)))
    for n, e in enumerate(g["jaw"]):
        add(jaw, e, f"jaw{n}{suffix}", "scale", {"down": "belly", "up": "mouth"}, jaw=True)
    if look["fan"]:
        for n, e in enumerate(g["fan"] + g["fan2"]):
            add(head, e, f"fin{n}{suffix}", "horn")
    if look["horns"] == "crown" or look["horns"] == "swept":
        for n, e in enumerate(g["horn"]):
            add(head, e, f"horn{n}{suffix}", "horn")
    # the eyes under the brows, teeth along the upper lip
    ey, ez = place((0, 56.6, -58.6)), place((0, 0, -55.4))
    for side, sx in (("e", 1), ("w", -1)):
        ex = place((sx * 6.05, 0, 0))[0]
        head.cube(f"eye_{side}{suffix}", (min(ex, ex + sx * 0.3), ey[1] - 1.3, ey[2] - 0.4),
                  (max(ex, ex + sx * 0.3), ey[1] + 1.0, ez[2] + 0.4), "scale",
                  paint={"east" if sx > 0 else "west": ("eye", big_eye)}, mirror_uv=sx < 0)
        for i in range(4):
            tz = place((0, 0, -68.5 + i * 2.2))[2]
            tx = place((sx * 4.45, 0, 0))[0]
            ty = place((0, 54.5, 0))[1]
            head.cube(f"tooth_{side}{i}{suffix}", (min(tx, tx - sx * 0.45), ty - 0.7, tz),
                      (max(tx, tx - sx * 0.45), ty, tz + 0.5), "teeth")
    if look["horns"] == "crown":           # a second, smaller pair inside the great horns
        for side, sx in (("e", 1), ("w", -1)):
            base = place((sx * 2.0, 61.5, -52.0))
            head.cube(f"crown_{side}{suffix}", (base[0] - 0.4, base[1], base[2]), (base[0] + 0.4, base[1] + 2.6, base[2] + 1.2),
                      "horn", rot=("x", -22.5), origin=base)
    if look["horns"] == "shards":          # a cluster of crystal prisms
        for n, (lx, ly, lz, h, r) in enumerate(((3.5, 61, -52, 7, -22.5), (-3.5, 61, -52, 7, -22.5),
                                                 (5.5, 60, -48, 5, -45), (-5.5, 60, -48, 5, -45),
                                                 (0, 62, -55, 4, 0), (1.8, 61.5, -46, 4.5, -22.5),
                                                 (-1.8, 61.5, -46, 4.5, -22.5))):
            base = place((lx, ly, lz))
            w = 0.55 if h > 5 else 0.4
            head.cube(f"shard{n}{suffix}", (base[0] - w, base[1], base[2] - w), (base[0] + w, base[1] + h * LOY_SIZE, base[2] + w),
                      "horn", rot=("x", r) if r else None, origin=base)
    if look["horns"] == "hooks":           # short horns hooking forward over the brow
        for side, sx in (("e", 1), ("w", -1)):
            base = place((sx * 4.5, 61, -49))
            head.cube(f"hook_{side}{suffix}", (base[0] - 0.6, base[1], base[2] - 0.6), (base[0] + 0.6, base[1] + 3.0, base[2] + 0.6),
                      "horn", rot=("x", 22.5), origin=base)
            tip = place((sx * 4.5, 65, -51.5))
            head.cube(f"hook_tip_{side}{suffix}", (tip[0] - 0.4, tip[1] - 0.4, tip[2] - 1.6), (tip[0] + 0.4, tip[1] + 0.4, tip[2]),
                      "horn", rot=("x", -22.5), origin=tip)
    if look["horns"] == "ram":             # ram horns curling back, down and forward round the cheek
        for side, sx in (("e", 1), ("w", -1)):
            steps = ((0, 61, -50, 0), (0.6, 62.5, -46, -45), (1.4, 61, -42, 45), (2.2, 57, -42, 0), (2.6, 54.5, -45.5, 45))
            for n, (dx, ly, lz, r) in enumerate(steps):
                base = place((sx * (5.0 + dx), ly, lz))
                w = (1.1 - n * 0.12)
                head.cube(f"ram{n}_{side}{suffix}", (base[0] - w, base[1] - w, base[2] - 1.6), (base[0] + w, base[1] + w, base[2] + 1.6),
                          "horn", rot=("x", r) if r else None, origin=base)
    def spike(name, at_, h, w, rot=None, bone=head):
        base = place(at_)
        bone.cube(name, (base[0] - w, base[1], base[2] - w), (base[0] + w, base[1] + h, base[2] + w), "horn",
                  rot=rot, origin=base)
    if look.get("mask"):                   # a gold mask down the snout's ridge
        spike(f"mask{suffix}", (0, 58.6, -66.0), 0.5, 1.6)
    if look.get("brow_shards"):
        for n, (lx, lz) in enumerate(((6.2, -60), (-6.2, -60), (0, -68), (1.8, -66), (-1.8, -66))):
            spike(f"brow_shard{n}{suffix}", (lx, 59.5 if lz > -64 else 58.0, lz), 2.2 if n < 2 else 1.6, 0.4,
                  ("x", 22.5))
    if look.get("frill"):                  # a frilled-lizard shield behind the head: three plates fanned out
        for n, (rz, sx) in enumerate(((0, 0), (45, 1), (-45, -1))):
            base = place((sx * 6.5, 55.5, -46.5))
            head.cube(f"frill_plate{n}{suffix}", (base[0] - 3.4, base[1] - 3.4, base[2]), (base[0] + 3.4, base[1] + 3.4, base[2] + 0.5),
                      "horn", rot=("z", rz) if rz else None, origin=base)
    if look.get("beard"):                  # chin spikes hanging from the deep jaw
        for n, lx in enumerate((-3.0, 0.0, 3.0)):
            base = place((lx, 48.7, -66.0 + abs(lx)), jaw=True)
            jaw.cube(f"beard{n}{suffix}", (base[0] - 0.35, base[1] - 2.2, base[2] - 0.35), (base[0] + 0.35, base[1], base[2] + 0.35),
                     "horn", rot=("x", -22.5), origin=base)
    if look.get("tusks"):                  # tusks rising from the underbite
        for side, sx in (("e", 1), ("w", -1)):
            base = place((sx * 4.0, 53.5, -69.0), jaw=True)
            jaw.cube(f"tusk_{side}{suffix}", (base[0] - 0.4, base[1], base[2] - 0.4), (base[0] + 0.4, base[1] + 2.4, base[2] + 0.4),
                     "teeth", rot=("x", -22.5), origin=base)
    helm = head.bone(f"armor_head{suffix}", head.pivot)
    armour_plate(helm, f"helm_crown{suffix}", place((-5.6, 60.6, -61.0)), place((5.6, 61.1, -48.0)), "up", mirror=False)
    armour_plate(helm, f"helm_snout{suffix}", place((-4.2, 58.6, -69.0)), place((4.2, 59.1, -62.0)), "up", mirror=False)
    if look.get("cheeks"):
        for side, sx in (("e", 1), ("w", -1)):
            for n in range(3):
                base = place((sx * 6.2, 50.5 + n * 1.6, -50 + n * 1.2))
                head.cube(f"cheek{n}_{side}{suffix}", (base[0] - 0.3, base[1] - 0.3, base[2]), (base[0] + 0.3, base[1] + 0.3, base[2] + 2.6),
                          "horn", rot=("y", -22.5 * sx), origin=base)


def forelegs(body):
    """Shoulder, a long forearm, a broad foot with four hooked claws."""
    arm = body.bone("arm_r", (5.4, 20.0, -9.0), (14, 0, 0))
    arm.rounded("arm_r", (3.2, 11.2, -12.8), (8.4, 22.8, -5.2), "scale", 1.4, {"up": "plates"})
    shin = arm.bone("foreshin_r", (5.7, 12.6, -8.6), (-30, 0, 0))
    shin.rounded("foreshin_r", (3.9, 4.0, -10.6), (7.5, 13.2, -6.6), "scale", 0.8)
    paw = shin.bone("forepaw_r", (5.7, 4.4, -8.6), (16, 0, 0))
    paw.rounded("forepaw_r", (3.3, 0.4, -12.4), (8.1, 3.2, -6.4), "scale", 0.7, {"down": "belly"})
    claws(paw, "fr", (3.9, 5.1, 6.3, 7.5), 0.4, -12.4)
    chunky(arm, 0.3)
    kit.mirror_x(arm, body)


def hindlegs(body, lift=0.0, scale=1.0):
    """A heavy thigh, the shin back to a high hock, a long foot forward. lift: degrees the body is tilted up, which
    the thighs take back so the feet stay flat."""
    thigh = body.bone("thigh_r", (5.8, 21.0, 13.0), (-lift, 0, 0))
    thigh.rounded("thigh_r", (2.6, 10.6, 8.0), (9.6, 25.0, 18.6), "scale", 2.0, {"up": "plates"})
    hshin = thigh.bone("hindshin_r", (6.0, 12.4, 14.0), (22, 0, 0))
    hshin.rounded("hindshin_r", (3.9, 4.4, 12.0), (8.1, 13.2, 16.2), "scale", 0.9)
    hock = hshin.bone("hindhock_r", (6.0, 5.2, 14.6), (-34, 0, 0))
    hock.rounded("metatarsus_r", (4.2, 1.2, 12.6), (7.8, 6.2, 16.4), "scale", 0.7)
    hpaw = hock.bone("hindpaw_r", (6.0, 2.0, 13.8), (12, 0, 0))
    hpaw.rounded("hindpaw_r", (3.4, 0.3, 9.0), (8.6, 3.0, 15.4), "scale", 0.7, {"down": "belly"})
    claws(hpaw, "hr", (4.0, 5.3, 6.7, 8.0), 0.3, 9.0)
    thigh.scale(scale)
    chunky(thigh, 0.3)
    kit.mirror_x(thigh, body)


def build_tail(body, segments=7):
    """Drooping then lifting, the crest running on to a spade at the end."""
    parent, z, y = body, 17.5, 22.5
    angles = (-18, -8, -2, 4, 8, 10, 12, 12, 12)
    for i in range(segments):
        r = 3.8 - 2.6 * i / max(1, segments - 1)
        seg = parent.bone(f"tail_{i + 1}", (0, y, z), (angles[i], 0, 0))
        seg.rounded(f"tail_{i + 1}", (-r, y - r, z - 0.6), (r, y + r, z + 5.6), "scale", min(0.7, r * 0.3),
                    {"down": "belly", "up": "plates"})
        spine(seg, z, z + 5.0, y + r - 0.3, max(1.2, 2.8 - i * 0.25), step=2.6)
        parent, z = seg, z + 5.2
    spade = parent.bone("tail_spade", (0, y, z))
    spade.cube("spade", (-2.6, y - 0.3, z + 0.4), (2.6, y + 0.3, z + 5.6), "horn", rot=("y", 45), origin=(0, y, z + 3.0))
    spade.cube("spade_rib", (-0.4, y - 0.5, z - 0.4), (0.4, y + 0.5, z + 6.0), "horn")


def build_rig(kind="drake"):
    """drake: four legs, wings on the back. wyvern: two legs, standing up, the wings its arms. twinhead: a drake with
    two necks."""
    root = Bone("root", (0, 0, 0))
    lift = 16.0 if kind == "wyvern" else 0.0
    body = root.bone("body_main", (0, 20, 4), (lift, 0, 0))
    body.rounded("waist", (-7.2, 13.0, -4.0), (7.2, 26.2, 10.0), "scale", 2.0, {"down": "belly", "up": "plates"})
    body.rounded("hips", (-7.0, 13.6, 8.0), (7.0, 26.0, 18.0), "scale", 2.0, {"down": "belly", "up": "plates"})
    spine(body, -3.0, 18.0, 25.8, 3.6)
    chest = body.bone("chest", (0, 20, -8))
    chest.rounded("chest", (-8.6, 12.0, -17.5), (8.6, 28.4, -2.6), "scale", 2.4,
                  {"north": "belly", "down": "belly", "up": "plates"})
    spine(chest, -15.0, -3.0, 28.0, 4.2)
    if kind == "twinhead":
        neck_and_head(chest, "_r", 3.4, -16)
        neck_and_head(chest, "_l", -3.4, 16)
    else:
        neck_and_head(chest)
    if kind != "wyvern":
        forelegs(body)
    hindlegs(body, lift, 1.15 if kind == "wyvern" else 1.0)
    build_tail(body)
    build_wing(body)
    build_folded_wing(body)
    build_armour(body, chest)
    root.bone("p_rider", (0, 29, 4))
    return root


# ------------------------------------------------------------------------------------------------ armour & saddle

def build_armour(body, chest):
    """Barding (the helm sits on each head): a breastplate wrapping the chest, a saddle with flaps."""
    peytral = chest.bone("armor_chest", chest.pivot)
    armour_plate(peytral, "breastplate", (-4.4, 13.0, -18.6), (4.4, 26.6, -18.1), "north", mirror=False, scallop=True,
                 lames=3)
    armour_plate(peytral, "breast_wing", (4.3, 13.0, -18.6), (8.8, 26.6, -18.1), "north", rot=("y", -45),
                 origin=(4.3, 20.0, -18.35), scallop=True, lames=3)
    saddle = body.bone("armor_saddle", body.pivot)
    saddle.cube("saddle", (-5.6, 26.8, -1.0), (5.6, 28.0, 9.0), "leather")
    saddle.cube("saddle_cantle", (-3.6, 27.8, 7.4), (3.6, 29.6, 9.0), "leather")
    saddle.cube("saddle_pommel", (-1.8, 27.8, -1.0), (1.8, 29.4, 0.4), "leather")
    saddle.cube("saddle_spine_gap", (-0.8, 25.4, -1.2), (0.8, 26.8, 9.2), "leather")
    armour_plate(saddle, "saddle_flap", (6.6, 18.6, 0.0), (7.1, 26.6, 8.0), "east", scallop=True)


# ------------------------------------------------------------------------------------------------ animations

FOLDED = {"wing_root_r": {"scale": keys((0, (0, 0, 0)))}, "wing_root_l": {"scale": keys((0, (0, 0, 0)))},
          "wingfold_r": {"scale": keys((0, (1, 1, 1)))}, "wingfold_l": {"scale": keys((0, (1, 1, 1)))}}
SPREAD = {"wing_root_r": {"scale": keys((0, (1, 1, 1)))}, "wing_root_l": {"scale": keys((0, (1, 1, 1)))},
          "wingfold_r": {"scale": keys((0, (0, 0, 0)))}, "wingfold_l": {"scale": keys((0, (0, 0, 0)))}}
NECK = ("neck_1", "hi_neck_2", "neck_3", "neck_4")


def tail(length, amp, freq=1):
    out = {f"tail_{i + 1}": {"rotation": wave(length, amp * (0.5 + 0.18 * i), -0.1 * i, axis=1, freq=freq)}
           for i in range(7)}
    out["tail_spade"] = {"rotation": wave(length, amp * 1.6, -0.75, axis=1, freq=freq)}
    return out


def neck(length, amp, phase=0.0, freq=1, axis=0):
    return {b: {"rotation": wave(length, amp * (0.6 + 0.1 * i), phase - 0.06 * i, axis=axis, freq=freq)}
            for i, b in enumerate(NECK)}


def legs(length, amp, phases):
    return gait(length, amp, (("arm_r", "foreshin_r", phases[0], 1), ("arm_l", "foreshin_l", phases[1], 1),
                              ("thigh_r", "hindshin_r", phases[2], 0.6), ("thigh_l", "hindshin_l", phases[3], 0.6)))


def flap(length, depth=1.0):
    """A bat-wing beat: a deep downstroke with the arm stretched, then the wing half folds on the way up — the hand
    swings back and in, the forearm lags."""
    return {"wing_root_r": {"rotation": wave(length, 34 * depth, 0.25, axis=2, base=(0, 0, -8))},
            "wing_humerus_r": {"rotation": wave(length, 10 * depth, 0.25, axis=1)},
            "wing_forearm_r": {"rotation": wave(length, 16 * depth, 0.1, axis=2)},
            "wing_hand_r": {"rotation": [(t, [0, max(0.0, v[1]), v[2]]) for t, v in
                                         wave(length, 22 * depth, -0.1, axis=1)]}}


TUCKED = {"arm_r": {"rotation": keys((0, (50, 0, 0)))}, "foreshin_r": {"rotation": keys((0, (-70, 0, 0)))},
          "forepaw_r": {"rotation": keys((0, (30, 0, 0)))},
          "thigh_r": {"rotation": keys((0, (55, 0, 0)))}, "hindshin_r": {"rotation": keys((0, (-20, 0, 0)))},
          "hindhock_r": {"rotation": keys((0, (30, 0, 0)))}, "hindpaw_r": {"rotation": keys((0, (35, 0, 0)))}}
STRAIGHT_TAIL = {f"tail_{i + 1}": {"rotation": keys((0, ((18, 8, 2, -4, -8, -10, -12)[i], 0, 0)))} for i in range(7)}


HEAD_BONES = ("neck_1", "hi_neck_2", "neck_3", "neck_4", "head", "jaw")


def dragon_animations(heads=("",)):
    """The animations, with every neck and head track given to each head (twin-head: _r and _l, the left half a
    beat behind so they do not move as one)."""
    out = []
    for name, length, loop, tracks in base_animations():
        each = {}
        for bone, channels in tracks.items():
            if bone not in HEAD_BONES:
                each[bone] = channels
                continue
            for n, suffix in enumerate(heads):
                each[bone + suffix] = {ch: [((t + n * 0.35) % length if loop and n else t, v) for t, v in ks]
                                       for ch, ks in channels.items()}
        out.append((name, length, loop, each))
    return out


def base_animations():
    walk, run, beat = 1.4, 0.8, 1.1
    return [
        ("idle", 5.0, True, with_(FOLDED, tail(5.0, 7), neck(5.0, 2.0), {
            "body_main": {"position": wave(5.0, 0.3, axis=1, freq=2)},
            "chest": {"scale": [(t, [1 + v[0], 1 + v[0], 1]) for t, v in wave(5.0, 0.02, freq=2)]},
            "head": {"rotation": keys((0, (0, 0, 0)), (1.2, (0, 16, 0)), (2.2, (4, 16, 0)), (3.2, (0, -12, 0)),
                                      (4.2, (0, -12, 0)), (5.0, (0, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (3.8, (0, 0, 0)), (4.1, (8, 0, 0)), (4.4, (0, 0, 0)), (5.0, (0, 0, 0)))}})),
        ("walk", walk, True, with_(FOLDED, tail(walk, 12), neck(walk, 3, 0.2, 2), legs(walk, 24, (0.0, 0.5, 0.5, 0.0)), {
            "body_main": {"position": wave(walk, 0.5, axis=1, freq=2), "rotation": wave(walk, 2.5, axis=2)},
            "chest": {"rotation": wave(walk, 4, 0.1, axis=1)}})),
        ("run", run, True, with_(FOLDED, tail(run, 8), neck(run, -5, 0.25), legs(run, 36, (0.0, 0.1, 0.5, 0.6)), {
            "body_main": {"rotation": wave(run, 7, 0.25), "position": wave(run, 1.2, axis=1, freq=2)},
            "wingfold_r": {"rotation": wave(run, 4, axis=2, freq=2)},
            "wingfold_l": {"rotation": wave(run, -4, axis=2, freq=2)}})),
        ("idle_fly", beat, True, with_(SPREAD, mirror(flap(beat)), mirror(TUCKED), STRAIGHT_TAIL,
                                       neck(beat, 2.5, 0.75), {
            "body_main": {"position": wave(beat, 1.6, 0.75, axis=1)}})),
        ("walk_fly", 0.9, True, with_(SPREAD, mirror(flap(0.9, 1.15)), mirror(TUCKED), STRAIGHT_TAIL, {
            "body_main": {"rotation": keys((0, (-6, 0, 0))), "position": wave(0.9, 1.8, 0.75, axis=1)},
            "neck_1": {"rotation": keys((0, (-14, 0, 0)))}, "head": {"rotation": keys((0, (10, 0, 0)))}})),
        ("glide", 4.0, True, with_(SPREAD, mirror(TUCKED), STRAIGHT_TAIL, tail(4.0, 3), {
            "wing_root_r": {"rotation": wave(4.0, 3, axis=2, base=(0, 0, -12))},
            "wing_root_l": {"rotation": wave(4.0, 3, 0.5, axis=2, base=(0, 0, 12))},
            "body_main": {"rotation": wave(4.0, 6, axis=2), "position": wave(4.0, 0.8, axis=1)}})),
        ("takeoff", 1.2, False, with_(SPREAD, mirror({
            "wing_root_r": {"rotation": keys((0, (0, 0, 45)), (0.4, (0, 0, 50)), (0.75, (0, 0, -42)), (1.2, (0, 0, 0)))},
            "arm_r": {"rotation": keys((0, (0, 0, 0)), (0.35, (-25, 0, 0)), (0.75, (40, 0, 0)), (1.2, (50, 0, 0)))},
            "thigh_r": {"rotation": keys((0, (0, 0, 0)), (0.35, (-25, 0, 0)), (0.75, (45, 0, 0)), (1.2, (55, 0, 0)))}}),
            {"body_main": {"position": keys((0, (0, 0, 0)), (0.35, (0, -2.5, 0)), (0.75, (0, 3.5, 0)), (1.2, (0, 0, 0)))}})),
        ("land", 1.0, False, with_(SPREAD, mirror({
            "wing_root_r": {"rotation": keys((0, (0, 0, 30)), (0.5, (0, -25, 45)), (1.0, (0, 0, 0)))},
            "arm_r": {"rotation": keys((0, (-30, 0, 0)), (0.5, (-10, 0, 0)), (1.0, (0, 0, 0)))}}),
            {"body_main": {"position": keys((0, (0, 2.5, 0)), (0.55, (0, -1.8, 0)), (1.0, (0, 0, 0)))}})),
        ("attack", 0.8, False, with_(FOLDED, {
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (0.25, (14, 0, 0)), (0.4, (-22, 0, 0)), (0.8, (0, 0, 0)))},
            "hi_neck_2": {"rotation": keys((0, (0, 0, 0)), (0.25, (12, 0, 0)), (0.4, (-12, 0, 0)), (0.8, (0, 0, 0)))},
            "head": {"rotation": keys((0, (0, 0, 0)), (0.25, (8, 0, 0)), (0.4, (-10, 0, 0)), (0.8, (0, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (0.25, (35, 0, 0)), (0.4, (0, 0, 0)), (0.8, (0, 0, 0)))},
            "arm_r": {"rotation": keys((0, (0, 0, 0)), (0.3, (-45, 0, 0)), (0.45, (25, 0, 0)), (0.8, (0, 0, 0)))}})),
        ("breath", 2.0, False, with_(FOLDED, {
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (0.5, (18, 0, 0)), (0.8, (-14, 0, 0)), (1.7, (-14, 0, 0)),
                                        (2.0, (0, 0, 0)))},
            "hi_neck_2": {"rotation": keys((0, (0, 0, 0)), (0.5, (10, 0, 0)), (0.8, (-6, 0, 0)), (2.0, (0, 0, 0)))},
            "head": {"rotation": keys((0, (0, 0, 0)), (0.5, (12, 0, 0)), (0.8, (-4, 0, 0)), (1.7, (-4, 0, 0)),
                                      (2.0, (0, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (0.5, (6, 0, 0)), (0.8, (40, 0, 0)), (1.7, (40, 0, 0)),
                                     (2.0, (0, 0, 0)))},
            "chest": {"scale": keys((0, (1, 1, 1)), (0.5, (1.06, 1.06, 1)), (0.8, (0.98, 0.98, 1)), (2.0, (1, 1, 1)))},
            "wingfold_r": {"rotation": keys((0, (0, 0, 0)), (0.5, (0, 0, 14)), (1.7, (0, 0, 14)), (2.0, (0, 0, 0)))},
            "wingfold_l": {"rotation": keys((0, (0, 0, 0)), (0.5, (0, 0, -14)), (1.7, (0, 0, -14)), (2.0, (0, 0, 0)))}})),
        ("death", 1.8, False, with_(FOLDED, {
            "body_main": {"rotation": keys((0, (0, 0, 0)), (0.7, (0, 0, 20)), (1.3, (0, 0, 85)), (1.8, (0, 0, 85))),
                          "position": keys((0, (0, 0, 0)), (1.3, (0, -12, 0)), (1.8, (0, -12, 0)))},
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (1.3, (-35, 0, 0)), (1.8, (-35, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (1.3, (18, 0, 0)), (1.8, (18, 0, 0)))}})),
        ("spawn", 1.0, False, with_(FOLDED, {
            "root": {"scale": keys((0, (0.2, 0.2, 0.2)), (0.7, (1.05, 1.05, 1.05)), (1.0, (1, 1, 1)))},
            "neck_1": {"rotation": keys((0, (-25, 0, 0)), (1.0, (0, 0, 0)))}})),
    ]



# ------------------------------------------------------------------------------------------------ the lung
#
# The eastern dragon: no wings — it swims through the air. A long body of ten segments laid in easy S-curves, short
# legs with three claws, a mane down the back, a long head with whiskers, a beard and antlers, a tuft at the tail.

LUNG_SEGMENTS = 10


def build_lung():
    root = Bone("root", (0, 0, 0))
    parent, z, y = root, -12.0, 15.0
    yaws = (0, 14, 14, -10, -18, -14, 10, 16, 14, -10)
    for i in range(LUNG_SEGMENTS):
        r = 3.4 if i < 5 else 3.4 - 0.4 * (i - 4)
        name = "body_main" if i == 0 else f"body_{i + 1}"
        seg = parent.bone(name, (0, y, z), (6 if i == 0 else 0, yaws[i], 0))
        seg.rounded(name, (-r, y - r, z - 0.5), (r, y + r, z + 6.4), "scale", min(1.2, r * 0.35),
                    {"down": "belly", "up": "plates"})
        for k in range(2):                                     # the mane, two tufts a segment leaning back
            zz = z + 0.6 + k * 3.0
            seg.cube(f"{name}_mane{k}", (-1.2, y + r - 0.6, zz), (1.2, y + r + 2.2 - 0.15 * i, zz + 3.2), "mane",
                     rot=("x", 22.5), origin=(0, y + r, zz + 1.6))
        if i in (0, 5):                                        # the two pairs of short legs
            front = i == 0
            leg = seg.bone(f"leg_{'f' if front else 'b'}_r", (2.8, y - 1.0, z + 3.0), (0, 0, 0))
            leg.rounded(f"leg_{'f' if front else 'b'}_r", (1.8, y - 9.0, z + 1.6), (4.6, y + 0.4, z + 4.6), "scale", 0.7)
            paw = leg.bone(f"paw_{'f' if front else 'b'}_r", (3.2, y - 8.8, z + 3.0))
            paw.rounded(f"paw_{'f' if front else 'b'}_r", (1.6, y - 10.8, z + 0.4), (4.8, y - 8.6, z + 4.6), "scale", 0.5,
                        {"down": "belly"})
            for c, x in enumerate((2.1, 3.2, 4.3)):
                paw.cube(f"claw{c}_{'f' if front else 'b'}_r", (x - 0.35, y - 10.8, z - 0.8), (x + 0.35, y - 9.8, z + 0.6),
                         "horn")
            kit.mirror_x(leg, seg)
        parent, z = seg, z + 5.8
    tuft = parent.bone("tail_tuft", (0, y, z))
    tuft.cube("tuft", (-1.8, y - 2.4, z - 0.4), (1.8, y + 2.6, z + 6.0), "mane")
    tuft.cube("tuft_tip", (-1.0, y - 1.6, z + 5.6), (1.0, y + 1.8, z + 8.4), "mane")

    body = root.children[0]
    neck1 = body.bone("neck_1", (0, y, -12.0), (-24, 0, 0))
    neck1.rounded("neck_1", (-3.2, y - 3.2, -16.6), (3.2, y + 3.2, -11.4), "scale", 1.0, {"down": "belly"})
    neck1.cube("neck_1_mane", (-1.4, y + 2.4, -16.4), (1.4, y + 5.2, -12.0), "mane", rot=("x", 22.5),
               origin=(0, y + 3.0, -14.0))
    neck2 = neck1.bone("hi_neck_2", (0, y, -16.4), (12, 0, 0))
    neck2.rounded("neck_2", (-3.0, y - 3.0, -20.8), (3.0, y + 3.0, -15.8), "scale", 1.0, {"down": "belly"})
    neck2.cube("neck_2_mane", (-1.6, y + 2.2, -20.6), (1.6, y + 5.4, -16.2), "mane", rot=("x", 22.5),
               origin=(0, y + 3.0, -18.0))
    head = neck2.bone("head", (0, y, -20.6), (8, 0, 0))
    hz = -20.6
    head.rounded("skull", (-3.4, y - 2.4, hz - 6.4), (3.4, y + 3.0, hz + 0.8), "scale", 1.2, {"down": "belly"})
    head.rounded("snout", (-2.4, y - 2.2, hz - 13.6), (2.4, y + 1.2, hz - 6.0), "scale", 0.8, {"down": "belly"})
    head.cube("nose_pad", (-1.8, y + 0.4, hz - 14.2), (1.8, y + 1.8, hz - 12.4), "horn")
    head.cube("eye_e", (3.25, y + 0.4, hz - 6.6), (3.6, y + 2.4, hz - 3.8), "scale", paint={"east": ("eye", big_eye)})
    head.cube("eye_w", (-3.6, y + 0.4, hz - 6.6), (-3.25, y + 2.4, hz - 3.8), "scale", paint={"west": ("eye", big_eye)},
              mirror_uv=True)
    head.cube("brow_e", (2.0, y + 2.4, hz - 7.6), (3.8, y + 3.4, hz - 3.6), "horn")
    head.cube("brow_w", (-3.8, y + 2.4, hz - 7.6), (-2.0, y + 3.4, hz - 3.6), "horn")
    for i in range(4):
        z_ = hz - 12.6 + i * 1.6
        head.cube(f"tooth_e{i}", (1.7, y - 2.9, z_), (2.2, y - 2.0, z_ + 0.6), "teeth")
        head.cube(f"tooth_w{i}", (-2.2, y - 2.9, z_), (-1.7, y - 2.0, z_ + 0.6), "teeth")
    head.cube("collar", (-4.2, y - 3.4, hz - 1.0), (4.2, y + 4.6, hz + 2.6), "mane")      # the mane round the head
    head.cube("palate", (-1.7, y - 1.9, hz - 13.0), (1.7, y - 1.4, hz - 1.2), "mouth")
    # whiskers: long and drooping from the snout's sides, each its own bone so they sway
    whisker = head.bone("whisker_r", (2.4, y + 0.2, hz - 12.0), (20, -30, 0))
    whisker.cube("whisker_r", (2.2, y - 0.2, hz - 12.0), (2.6, y + 0.2, hz - 2.0), "mane")
    whisker.cube("whisker_end_r", (2.25, y - 0.15, hz - 2.4), (2.55, y + 0.15, hz + 4.0), "mane", rot=("x", -22.5),
                 origin=(2.4, y, hz - 2.4))
    kit.mirror_x(whisker, head)
    antler = head.bone("antler_e", (1.8, y + 2.8, hz - 1.0), (-38, -16, 0))
    antler.cube("antler_beam_e", (1.3, y + 2.4, hz - 1.4), (2.3, y + 3.4, hz + 9.0), "horn")
    for k, zz in enumerate((hz + 2.0, hz + 5.4)):
        antler.cube(f"antler_tine{k}_e", (1.4, y + 3.2, zz), (2.2, y + 6.4 - k, zz + 0.8), "horn", rot=("x", -22.5),
                    origin=(1.8, y + 3.2, zz))
    antler.cube("antler_fork_e", (1.4, y + 2.6, hz + 8.4), (2.2, y + 5.4, hz + 9.2), "horn", rot=("x", -45),
                origin=(1.8, y + 3.0, hz + 8.6))
    kit.mirror_x(antler, head, rename=("_e", "_w"))
    jaw = head.bone("jaw", (0, y - 1.8, hz - 1.0))
    jaw.rounded("jaw", (-2.2, y - 3.6, hz - 12.8), (2.2, y - 1.8, hz - 0.8), "scale", 0.6, {"down": "belly"})
    jaw.cube("beard", (-1.6, y - 6.6, hz - 6.0), (1.6, y - 3.4, hz - 1.4), "mane")
    jaw.cube("tongue", (-1.0, y - 2.2, hz - 10.6), (1.0, y - 1.8, hz - 2.0), "mouth")
    head.scale(1.2)
    root.bone("p_rider", (0, y + 5, -4))
    return root


LUNG_BODY = ["body_main"] + [f"body_{i + 1}" for i in range(1, LUNG_SEGMENTS)]


def slither(length, amp, axis=1, freq=1, phase=0.0):
    """A wave running down the body from head to tail."""
    out = {b: {"rotation": wave(length, amp * (0.6 if i == 0 else 1.0), phase - 0.09 * i, axis=axis, freq=freq)}
           for i, b in enumerate(LUNG_BODY)}
    out["tail_tuft"] = {"rotation": wave(length, amp * 1.5, phase - 0.09 * LUNG_SEGMENTS, axis=axis, freq=freq)}
    return out


def lung_legs(length, amp):
    return gait(length, amp, (("leg_f_r", "paw_f_r", 0.0, 0.5), ("leg_f_l", "paw_f_l", 0.5, 0.5),
                              ("leg_b_r", "paw_b_r", 0.5, 0.5), ("leg_b_l", "paw_b_l", 0.0, 0.5)))


LUNG_TUCKED = {f"leg_{s}": {"rotation": keys((0, (60, 0, 0)))} for s in ("f_r", "f_l", "b_r", "b_l")}


def whiskers(length, amp):
    return {"whisker_r": {"rotation": wave(length, amp, 0.0, axis=0)},
            "whisker_l": {"rotation": wave(length, amp, 0.3, axis=0)}}


def lung_animations():
    return [
        ("idle", 5.0, True, with_(slither(5.0, 4), whiskers(5.0, 6), {
            "head": {"rotation": keys((0, (0, 0, 0)), (1.4, (0, 18, 0)), (2.6, (0, 18, 0)), (3.6, (0, -12, 0)),
                                      (5.0, (0, 0, 0)))}})),
        ("walk", 1.6, True, with_(slither(1.6, 10), lung_legs(1.6, 26), whiskers(1.6, 10))),
        ("run", 0.9, True, with_(slither(0.9, 14), lung_legs(0.9, 38), whiskers(0.9, 14))),
        ("idle_fly", 2.4, True, with_(slither(2.4, 9, axis=0), LUNG_TUCKED, whiskers(2.4, 14), {
            "body_main": {"position": wave(2.4, 1.6, axis=1)}})),
        ("walk_fly", 1.6, True, with_(slither(1.6, 13, axis=0), LUNG_TUCKED, whiskers(1.6, 18))),
        ("glide", 4.0, True, with_(slither(4.0, 6, axis=0), slither(4.0, 4), LUNG_TUCKED, whiskers(4.0, 10))),
        ("takeoff", 1.0, False, with_(LUNG_TUCKED, {
            "body_main": {"rotation": keys((0, (0, 0, 0)), (0.5, (-25, 0, 0)), (1.0, (0, 0, 0))),
                          "position": keys((0, (0, 0, 0)), (1.0, (0, 6, 0)))}})),
        ("land", 1.0, False, {"body_main": {"position": keys((0, (0, 6, 0)), (1.0, (0, 0, 0)))}}),
        ("attack", 0.8, False, {
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (0.25, (16, 0, 0)), (0.4, (-20, 0, 0)), (0.8, (0, 0, 0)))},
            "head": {"rotation": keys((0, (0, 0, 0)), (0.25, (10, 0, 0)), (0.4, (-10, 0, 0)), (0.8, (0, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (0.25, (35, 0, 0)), (0.4, (0, 0, 0)), (0.8, (0, 0, 0)))}}),
        ("breath", 2.0, False, {
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (0.5, (16, 0, 0)), (0.8, (-12, 0, 0)), (1.7, (-12, 0, 0)),
                                        (2.0, (0, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (0.5, (6, 0, 0)), (0.8, (40, 0, 0)), (1.7, (40, 0, 0)),
                                     (2.0, (0, 0, 0)))}}),
        ("death", 1.8, False, {
            "body_main": {"rotation": keys((0, (0, 0, 0)), (1.2, (0, 0, 80)), (1.8, (0, 0, 80))),
                          "position": keys((0, (0, 0, 0)), (1.2, (0, -8, 0)), (1.8, (0, -8, 0)))}}),
        ("spawn", 1.0, False, {"root": {"scale": keys((0, (0.2, 0.2, 0.2)), (0.7, (1.05, 1.05, 1.05)),
                                                      (1.0, (1, 1, 1)))}}),
    ]


DRAGONS = {   # name: (kind, style)
    "gold": ("drake", "gold"),
    "crystal": ("drake", "crystal"),
    "verdant": ("twinhead", "verdant"),
    "ember": ("wyvern", "ember"),
    "jade": ("lung", "jade"),
}


def main(names=None):
    for name in names or list(DRAGONS):
        kind, style = DRAGONS[name]
        apply_style(style)
        model = f"dragon_{name}"
        rig = build_lung() if kind == "lung" else build_rig(kind)
        print(name, "built", kit.build(model, rig, paint_atlas(), HERE))
        heads = ("_r", "_l") if kind == "twinhead" else ("",)
        print(name, "animations", kit.animations(lung_animations() if kind == "lung" else dragon_animations(heads)))
        kit.save_project(os.path.join(HERE, model + ".bbmodel"))
        for animation, time, armour, picture in (("idle", 0.0, False, "preview"), ("glide", 0.5, False, "flight"),
                                                 ("idle", 0.0, True, "armour")):
            kit.show("armor_", armour)
            kit.frame(animation, time)
            print(kit.shots(os.path.join(HERE, f"{model}_{picture}.png"), [(35, 18), (90, 8), (-145, 25), (0, 10)],
                            target=[0, 22, 0], distance=170))


if __name__ == "__main__":
    import sys
    main(sys.argv[1:])

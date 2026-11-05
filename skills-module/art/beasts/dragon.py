"""
The dragon: a western dragon on four legs with a pair of bat wings — horned head on a long S-neck, a crest of spines
from brow to tail, amber belly plates, a spade at the tail's end. Companion that fights and grows; ridden at
mastery. Built at full size; BetterModel scales the young ones down.

    python3 dragon.py        # builds it in a running Blockbench, saves dragon.bbmodel and preview sheets
"""
import math
import os

from PIL import Image, ImageDraw

import beastkit as kit
from beastkit import (Bone, armour_plate, feathers, gait, hexrgb, keys, mirror, mix, noise, plate_edge, ramp_at,
                      smooth, wave, with_)

HERE = os.path.dirname(os.path.abspath(__file__))

CRIMSON = [hexrgb(h) for h in ("#25080c", "#451014", "#6e191b", "#972823", "#bf4430")]
AMBER = [hexrgb(h) for h in ("#5e3a12", "#8f6020", "#bf8c36", "#e2b862")]
BONE_ = [hexrgb(h) for h in ("#463c30", "#776a56", "#ad9f80", "#ddd2b6")]
MEMBRANE = [hexrgb(h) for h in ("#2c080c", "#4c1014", "#6f1a1c", "#922a25")]
MEMBRANE_UNDER = [hexrgb(h) for h in ("#5a1a1a", "#7e2b25", "#a24333", "#c0624a")]
VEIN = hexrgb("#1c0507")
DARK = hexrgb("#120406")


# ------------------------------------------------------------------------------------------------ painting

def belly(tile, rng):
    """Transverse belly plates: bands across the body, lit along their front edge, a dark seam behind each."""
    px = tile.load()
    size = tile.size[0]
    for y in range(size):
        for x in range(size):
            k = 0.75 - 0.5 * (y % 5) / 5 + 0.05 * ((x // 3 + y // 5) % 2)
            c = ramp_at(AMBER, k)
            if y % 5 == 4:
                c = AMBER[0]
            px[x, y] = c + (255,)


def horn(tile, rng):
    """Horn and spine: smooth bone, darker to the base, with growth rings."""
    px = tile.load()
    size = tile.size[0]
    for y in range(size):
        for x in range(size):
            c = ramp_at(BONE_, 0.9 - 0.7 * y / size)
            if y % 4 == 3:
                c = mix(c, BONE_[0], 0.35)
            px[x, y] = c + (255,)


def eye(tile, rng):
    """A 4 x 4 dragon eye in the tile's corner: hot yellow iris, black slit pupil, a dark lid rim."""
    px = tile.load()
    for y in range(tile.size[0]):
        for x in range(tile.size[0]):
            px[x, y] = CRIMSON[0] + (255,)
    colours = {"r": CRIMSON[1], "i": hexrgb("#ffd23a"), "o": hexrgb("#ff8a1a"), "p": (10, 4, 4)}
    for y, row in enumerate(["ipii", "ipio", "ipoo", "rpor"]):
        for x, ch in enumerate(row):
            px[x, y] = colours[ch] + (255,)


def paint_atlas():
    atlas = kit.Atlas()
    atlas.tile("scale", lambda t, r: feathers(t, r, CRIMSON[1:], 6, 5, CRIMSON[0]), 1)
    atlas.tile("plates", lambda t, r: feathers(t, r, CRIMSON[1:], 10, 8, CRIMSON[0]), 2)
    atlas.tile("belly", belly, 3)
    atlas.tile("horn", horn, 4)
    atlas.tile("claw", lambda t, r: smooth(t, r, [(10, 8, 8), (34, 28, 26), (78, 70, 64)], 0.9, 0.0), 5)
    atlas.tile("teeth", lambda t, r: smooth(t, r, BONE_[1:] + [hexrgb("#f4eedd")], 1.0, 0.5), 6)
    atlas.tile("mouth", lambda t, r: smooth(t, r, [hexrgb("#2a0608"), hexrgb("#5a1418"), hexrgb("#8a2a2c")], 0.8, 0.1), 7)
    atlas.tile("eye", eye, 8, fixed=True)
    atlas.tile("skin", lambda t, r: smooth(t, r, CRIMSON[:4], 0.75, 0.25), 9)
    atlas.tile("leather", lambda t, r: smooth(t, r, [hexrgb(h) for h in ("#2e1c10", "#4a2e18", "#6a4426", "#8a5c34")],
                                              0.9, 0.2), 10)
    atlas.tile("plate_edge", plate_edge, 11)
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


def membrane_images():
    """(top, under, owner) painted over the whole spread wing in wing coordinates; owner says which bone's panel
    shows each pixel."""
    outline, elbow, wrist, tips = membrane_outline()
    x0, z0, w, h = 0.0, -14.0, 72, 50
    size = (int(w * W_PX), int(h * W_PX))

    def px(p):
        return ((p[0] - x0) * W_PX, (p[1] - z0) * W_PX)
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).polygon([px(p) for p in outline], fill=255)
    m = mask.load()

    def side_of(p, a, b):
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
    images = []
    for ramp in (MEMBRANE, MEMBRANE_UNDER):
        image = Image.new("RGBA", size, (0, 0, 0, 0))
        out = image.load()
        rays = [wrist] + tips
        for j in range(size[1]):
            for i in range(size[0]):
                if not m[i, j]:
                    continue
                p = (x0 + (i + 0.5) / W_PX, z0 + (j + 0.5) / W_PX)
                # lighter in the middle of each cell between bones, darker towards the bones and the free edge
                dists = [abs(side_of(p, wrist, t)) / math.dist(wrist, t) for t in tips]
                dists.append(abs(side_of(p, elbow, wrist)) / math.dist(elbow, wrist))
                near = min(dists)
                k = 0.25 + min(0.55, near * 0.07) + 0.08 * noise("mem", i // 6, j // 6)
                angle = math.atan2(p[1] - wrist[1], p[0] - wrist[0])
                spoke = abs(math.sin(angle * 9.0))
                if spoke < 0.06 and math.dist(p, wrist) > 4:
                    k -= 0.18     # veins fanning out from the wrist between the fingers
                ring = math.dist(p, wrist) % 5.0
                if ring < 0.25:
                    k -= 0.06     # faint creases across
                out[i, j] = ramp_at(ramp, k) + (255,)
        # the free edge: a dark rim one pixel in
        edge = mask.filter(__import__("PIL.ImageFilter", fromlist=["MinFilter"]).MinFilter(5))
        e = edge.load()
        for j in range(size[1]):
            for i in range(size[0]):
                if m[i, j] and not e[i, j]:
                    out[i, j] = mix(out[i, j][:3], VEIN, 0.6) + (255,)
        images.append(image)
    owner = Image.new("L", size, 0)
    o = owner.load()
    for j in range(size[1]):
        for i in range(size[0]):
            if not m[i, j]:
                continue
            p = (x0 + (i + 0.5) / W_PX, z0 + (j + 0.5) / W_PX)
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


def fold_colour(s, t):
    top = 0.0 if s < 16 else (s - 16) * 0.12
    bottom = (3.5 + s * 1.0 if s < 8 else 11.5 if s < 25 else 11.5 - (s - 25) * 0.42) + 0.7 * math.sin(s * 0.8)
    if s < 0 or s > FOLD_LENGTH or not top <= t <= bottom:
        return None
    fingers = [1.2 + s * 0.06, 3.0 + s * 0.11, 4.6 + s * 0.17]
    if t < 1.6 and s < 17:
        c = ramp_at(CRIMSON, 0.55 - 0.2 * t)          # the forearm on top
    elif any(abs(t - f) < 0.45 for f in fingers):
        c = ramp_at(CRIMSON, 0.5)                      # a finger running back
    else:
        pleat = math.sin(s * 1.1 + t * 0.6)
        c = ramp_at(MEMBRANE, 0.45 + 0.22 * pleat - 0.1 * (t / 12))
    if bottom - t < 0.5 or t - top < 0.4:
        c = mix(c, VEIN, 0.6)
    return c + (255,)


FOLD_STRIPS = ((3.4, 45.0), (4.0, 22.5), (4.6, 0.0))


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
    fold.scale(1.15)
    kit.mirror_x(fold, body)


# ------------------------------------------------------------------------------------------------ the body

def spine(bone, z_from, z_to, y, height, step=3.0, lean=22.5):
    """A crest of spines along the top, each leaning back, shrinking towards z_to."""
    count = max(1, int(abs(z_to - z_from) / step))
    for i in range(count):
        z = z_from + (z_to - z_from) * i / count
        hgt = height * (1 - 0.35 * i / count)
        bone.cube(f"{bone.name}_spine{i}", (-0.55, y - 0.4, z), (0.55, y + hgt * 0.55, z + 1.7), "horn",
                  rot=("x", lean), origin=(0, y, z + 0.8))
        bone.cube(f"{bone.name}_spine{i}_tip", (-0.3, y + hgt * 0.5, z + 0.4), (0.3, y + hgt, z + 1.3), "horn",
                  rot=("x", lean), origin=(0, y, z + 0.8))


def claws(bone, side, xs, y, z):
    """Hooked claws on a foot's front edge at z: a thick base and a tip curling down."""
    for i, x in enumerate(xs):
        bone.cube(f"claw{i}_{side}", (x - 0.4, y, z - 1.6), (x + 0.4, y + 1.3, z), "claw")
        bone.cube(f"claw{i}_tip_{side}", (x - 0.3, y - 0.3, z - 2.4), (x + 0.3, y + 0.7, z - 1.5), "claw")


def build_rig():
    root = Bone("root", (0, 0, 0))
    body = root.bone("body_main", (0, 20, 4))
    body.rounded("waist", (-6.8, 13.4, -4.0), (6.8, 26.0, 10.0), "scale", 1.6, {"down": "belly", "up": "plates"})
    body.rounded("hips", (-6.6, 14.4, 8.5), (6.6, 26.2, 18.5), "scale", 1.6, {"down": "belly", "up": "plates"})
    spine(body, -3.0, 18.0, 25.8, 3.8)

    chest = body.bone("chest", (0, 20, -8))
    chest.rounded("chest", (-9.0, 11.6, -18.2), (9.0, 29.0, -2.6), "scale", 2.2,
                  {"north": "belly", "down": "belly", "up": "plates"})
    spine(chest, -16.0, -3.0, 28.6, 4.4)

    # the S-neck: five segments rising steeply out of the chest, then bending forward to carry the head level
    parent, z, y = chest, -15.0, 24.0
    for i, (angle, r) in enumerate(zip((30, 12, 4, -14, -20), (4.9, 4.3, 3.8, 3.4, 3.1))):
        name = "hi_neck_4" if i == 3 else f"neck_{i + 1}"
        seg = parent.bone(name, (0, y, z), (angle, 0, 0))
        seg.rounded(f"neck_{i + 1}", (-r, y - r, z - 5.0), (r, y + r, z + 0.6), "scale", 0.8,
                    {"down": "belly", "up": "plates"})
        spine(seg, z - 4.5, z, y + r - 0.2, 3.0 - i * 0.25, step=2.4)
        parent, z = seg, z - 4.6
    head = parent.bone("head", (0, y, z), (-12, 0, 0))
    hz = z
    head.rounded("skull", (-3.9, y - 2.6, hz - 7.0), (3.9, y + 3.2, hz + 0.8), "scale", 1.0, {"down": "belly"})
    head.rounded("snout", (-2.7, y - 2.4, hz - 14.6), (2.7, y + 1.6, hz - 6.4), "scale", 0.7, {"down": "belly"})
    head.cube("brow_r", (2.0, y + 2.0, hz - 7.6), (3.6, y + 3.2, hz - 2.6), "plates", rot=("y", -22.5),
              origin=(2.8, y + 2.6, hz - 5))
    head.cube("brow_l", (-3.6, y + 2.0, hz - 7.6), (-2.0, y + 3.2, hz - 2.6), "plates", rot=("y", 22.5),
              origin=(-2.8, y + 2.6, hz - 5))
    head.cube("snout_ridge", (-0.7, y + 1.2, hz - 13.0), (0.7, y + 2.0, hz - 6.0), "plates")
    head.cube("nose_horn", (-0.4, y + 1.2, hz - 13.4), (0.4, y + 3.0, hz - 12.2), "horn", rot=("x", 22.5),
              origin=(0, y + 1.4, hz - 12.8))
    head.cube("nostril_r", (0.8, y + 0.6, hz - 14.1), (1.7, y + 1.3, hz - 13.2), "mouth")
    head.cube("nostril_l", (-1.7, y + 0.6, hz - 14.1), (-0.8, y + 1.3, hz - 13.2), "mouth")
    head.cube("eye_r", (3.7, y + 0.6, hz - 6.8), (4.05, y + 2.0, hz - 4.6), "eye", faces={"east": "eye"})
    head.cube("eye_l", (-4.05, y + 0.6, hz - 6.8), (-3.7, y + 2.0, hz - 4.6), "eye", faces={"west": "eye"})
    for i in range(5):
        z_ = hz - 13.2 + i * 1.5
        head.cube(f"tooth_r{i}", (2.0, y - 3.1, z_), (2.5, y - 2.3, z_ + 0.6), "teeth")
        head.cube(f"tooth_l{i}", (-2.5, y - 3.1, z_), (-2.0, y - 2.3, z_ + 0.6), "teeth")
    for i, (dy, dz) in enumerate(((-1.6, -1.0), (-0.4, 0.2), (0.8, 1.2))):   # the frill: spines down the cheek
        head.cube(f"frill_r{i}", (3.6, y + dy, hz + dz - 1.0), (4.1, y + dy + 0.9, hz + dz + 2.2), "horn",
                  rot=("y", -22.5), origin=(3.8, y + dy, hz + dz))
        head.cube(f"frill_l{i}", (-4.1, y + dy, hz + dz - 1.0), (-3.6, y + dy + 0.9, hz + dz + 2.2), "horn",
                  rot=("y", 22.5), origin=(-3.8, y + dy, hz + dz))
    horn_r = head.bone("horn_r", (2.4, y + 2.6, hz - 1.6), (-32, -16, 0))
    horn_r.cube("horn_base_r", (1.3, y + 1.5, hz - 2.4), (3.5, y + 3.7, hz + 3.6), "horn")
    horn_r.cube("horn_mid_r", (1.7, y + 1.9, hz + 3.2), (3.1, y + 3.3, hz + 8.6), "horn", rot=("x", 22.5),
                origin=(2.4, y + 2.6, hz + 3.2))
    horn_r.cube("horn_tip_r", (2.05, y + 1.0, hz + 7.8), (2.75, y + 1.8, hz + 12.0), "horn", rot=("x", 45),
                origin=(2.4, y + 1.4, hz + 7.8))
    kit.mirror_x(horn_r, head)
    jaw = head.bone("jaw", (0, y - 2.0, hz - 1.0))
    jaw.rounded("jaw", (-2.4, y - 4.2, hz - 13.6), (2.4, y - 2.2, hz - 0.8), "scale", 0.5, {"down": "belly"})
    jaw.cube("tongue", (-1.2, y - 2.3, hz - 11.5), (1.2, y - 1.9, hz - 2.0), "mouth")
    for i in range(4):
        z_ = hz - 12.4 + i * 1.6
        jaw.cube(f"fang_r{i}", (1.3, y - 2.1, z_), (1.8, y - 1.3, z_ + 0.6), "teeth")
        jaw.cube(f"fang_l{i}", (-1.8, y - 2.1, z_), (-1.3, y - 1.3, z_ + 0.6), "teeth")
    head.cube("palate", (-1.9, y - 2.2, hz - 13.2), (1.9, y - 1.6, hz - 1.0), "mouth")

    # forelegs: shoulder, a long forearm, a broad foot with four hooked claws
    arm = body.bone("arm_r", (5.4, 20.0, -9.0), (14, 0, 0))
    arm.rounded("arm_r", (3.2, 11.2, -12.8), (8.4, 22.8, -5.2), "scale", 1.4, {"up": "plates"})
    shin = arm.bone("foreshin_r", (5.7, 12.6, -8.6), (-30, 0, 0))
    shin.rounded("foreshin_r", (3.9, 4.0, -10.6), (7.5, 13.2, -6.6), "scale", 0.8)
    paw = shin.bone("forepaw_r", (5.7, 4.4, -8.6), (16, 0, 0))
    paw.rounded("forepaw_r", (3.3, 0.4, -12.4), (8.1, 3.2, -6.4), "scale", 0.7, {"down": "belly"})
    claws(paw, "fr", (3.9, 5.1, 6.3, 7.5), 0.4, -12.4)
    kit.mirror_x(arm, body)

    # hind legs: a heavy thigh, the shin back to a high hock, a long foot forward
    thigh = body.bone("thigh_r", (5.8, 21.0, 13.0))
    thigh.rounded("thigh_r", (2.6, 10.6, 8.0), (9.6, 25.0, 18.6), "scale", 2.0, {"up": "plates"})
    hshin = thigh.bone("hindshin_r", (6.0, 12.4, 14.0), (22, 0, 0))
    hshin.rounded("hindshin_r", (3.9, 4.4, 12.0), (8.1, 13.2, 16.2), "scale", 0.9)
    hock = hshin.bone("hindhock_r", (6.0, 5.2, 14.6), (-34, 0, 0))
    hock.rounded("metatarsus_r", (4.2, 1.2, 12.6), (7.8, 6.2, 16.4), "scale", 0.7)
    hpaw = hock.bone("hindpaw_r", (6.0, 2.0, 13.8), (12, 0, 0))
    hpaw.rounded("hindpaw_r", (3.4, 0.3, 9.0), (8.6, 3.0, 15.4), "scale", 0.7, {"down": "belly"})
    claws(hpaw, "hr", (4.0, 5.3, 6.7, 8.0), 0.3, 9.0)
    kit.mirror_x(thigh, body)

    # the tail: seven segments, drooping then lifting, the crest running on to a spade at the end
    parent, z, y = body, 17.5, 22.5
    for i, (angle, r) in enumerate(zip((-18, -8, -2, 4, 8, 10, 12), (3.8, 3.3, 2.8, 2.4, 2.0, 1.6, 1.25))):
        seg = parent.bone(f"tail_{i + 1}", (0, y, z), (angle, 0, 0))
        seg.rounded(f"tail_{i + 1}", (-r, y - r, z - 0.6), (r, y + r, z + 5.6), "scale", min(0.7, r * 0.3),
                    {"down": "belly", "up": "plates"})
        spine(seg, z, z + 5.0, y + r - 0.2, max(1.2, 2.8 - i * 0.25), step=2.8)
        parent, z = seg, z + 5.2
    spade = parent.bone("tail_spade", (0, y, z))
    spade.cube("spade", (-2.2, y - 0.3, z + 0.6), (2.2, y + 0.3, z + 5.0), "plates", rot=("y", 45), origin=(0, y, z + 2.8))
    spade.cube("spade_rib", (-0.4, y - 0.5, z - 0.4), (0.4, y + 0.5, z + 6.0), "horn")

    build_wing(body)
    build_folded_wing(body)
    build_armour(body, chest, head)
    head.scale(1.3)   # a fantasy head: big enough to read from far off, with horns to match
    root.bone("p_rider", (0, 29, 4))
    return root


# ------------------------------------------------------------------------------------------------ armour & saddle

def build_armour(body, chest, head):
    helm = head.bone("armor_head", head.pivot)
    y, hz = head.pivot[1], head.pivot[2]
    armour_plate(helm, "helm_crown", (-3.4, y + 3.2, hz - 6.8), (3.4, y + 3.6, hz + 0.2), "up", mirror=False)
    armour_plate(helm, "helm_snout", (-2.4, y + 1.6, hz - 12.4), (2.4, y + 2.0, hz - 7.0), "up", mirror=False)
    armour_plate(helm, "helm_cheek", (3.9, y - 1.8, hz - 3.6), (4.3, y + 1.0, hz + 0.4), "east", scallop=True)
    peytral = chest.bone("armor_chest", chest.pivot)
    armour_plate(peytral, "breastplate", (-4.6, 13.0, -19.2), (4.6, 27.0, -18.7), "north", mirror=False, scallop=True,
                 lames=3)
    armour_plate(peytral, "breast_wing", (4.5, 13.0, -19.2), (9.0, 27.0, -18.7), "north", rot=("y", -45),
                 origin=(4.5, 20.0, -18.95), scallop=True, lames=3)
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
NECK = ("neck_1", "neck_2", "neck_3", "hi_neck_4", "neck_5")


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


def dragon_animations():
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
            "hi_neck_4": {"rotation": keys((0, (0, 0, 0)), (0.25, (12, 0, 0)), (0.4, (-12, 0, 0)), (0.8, (0, 0, 0)))},
            "head": {"rotation": keys((0, (0, 0, 0)), (0.25, (8, 0, 0)), (0.4, (-10, 0, 0)), (0.8, (0, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (0.25, (35, 0, 0)), (0.4, (0, 0, 0)), (0.8, (0, 0, 0)))},
            "arm_r": {"rotation": keys((0, (0, 0, 0)), (0.3, (-45, 0, 0)), (0.45, (25, 0, 0)), (0.8, (0, 0, 0)))}})),
        ("breath", 2.0, False, with_(FOLDED, {
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (0.5, (18, 0, 0)), (0.8, (-14, 0, 0)), (1.7, (-14, 0, 0)),
                                        (2.0, (0, 0, 0)))},
            "neck_2": {"rotation": keys((0, (0, 0, 0)), (0.5, (10, 0, 0)), (0.8, (-6, 0, 0)), (2.0, (0, 0, 0)))},
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


def main():
    atlas = paint_atlas()
    rig = build_rig()
    print("built", kit.build("dragon", rig, atlas, HERE))
    print("animations", kit.animations(dragon_animations()))
    kit.save_project(os.path.join(HERE, "dragon.bbmodel"))
    for animation, time, armour, picture in (("idle", 0.0, False, "dragon_preview.png"),
                                             ("glide", 0.5, False, "dragon_flight.png"),
                                             ("idle", 0.0, True, "dragon_armour.png")):
        kit.show("armor_", armour)
        kit.frame(animation, time)
        print(kit.shots(os.path.join(HERE, picture), [(35, 18), (90, 8), (-145, 25), (0, 10)], target=[0, 22, 0],
                        distance=170))


if __name__ == "__main__":
    main()

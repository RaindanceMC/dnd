"""
The griffin: eagle head, breast, forelegs and wings on a lion's hindquarters and tail — a rideable mount about the
size of a horse. Design, proportions, bones and palette: plans/creature-modelling-guide.md §4.

    python3 griffin.py        # builds it in a running Blockbench, saves griffin.bbmodel and a preview sheet
"""
import math
import os
import random

from PIL import ImageDraw

import beastkit as kit
from beastkit import (Bone, armour_plate, feathers, fur, hexrgb, keys, lanes, metal, mirror, mix, noise,
                      plate_edge, ramp_at, scale_rows, scales, smooth, wave, with_, gait)

HERE = os.path.dirname(os.path.abspath(__file__))

BROWN = [hexrgb(h) for h in ("#2b1d12", "#4a3320", "#6b4a2b", "#8a6a42")]
GOLD = [hexrgb(h) for h in ("#8f6a22", "#c9a04a", "#e2c27a", "#f2dfa3")]
TAWNY = [hexrgb(h) for h in ("#6e4a24", "#8f6430", "#c08a45", "#d9ac68")]
CREAM = [hexrgb(h) for h in ("#b79b6a", "#d8c08e", "#efe0b8")]
HORN = [hexrgb(h) for h in ("#1c1f24", "#3a414b", "#66707c", "#9aa3ad")]
YELLOW = [hexrgb(h) for h in ("#b88a1a", "#d9b43a", "#e8c52e")]
DARK = [hexrgb(h) for h in ("#1c120a", "#2a1a0e", "#5a3a1c")]
LEATHER = [hexrgb(h) for h in ("#4a2a14", "#6a3f22", "#8a5530", "#a8703e")]


# ------------------------------------------------------------------------------------------------ painting

def hackles(tile, rng, ramp):
    """Lanceolate neck feathers hanging down in staggered rows: narrow, pointed, each outlined dark."""
    px = tile.load()
    size = tile.size[0]
    w, h = 4, 10
    for y in range(size):
        for x in range(size):
            px[x, y] = ramp[0] + (255,)
    for row in range(-1, size // (h - 3) + 2):
        top = row * (h - 3)
        shift = 2 if row % 2 else 0
        for col in range(-1, size // w + 2):
            left = col * w + shift
            tone = rng.uniform(0.45, 0.7)
            for yy in range(h):
                half = (w / 2) * (1 - max(0, yy - h * 0.55) / (h * 0.45))
                for xx in range(w):
                    x, y = left + xx, top + yy
                    if not (0 <= x < size and 0 <= y < size):
                        continue
                    d = abs(xx - (w - 1) / 2)
                    if d > half:
                        continue
                    edge = d > half - 0.8
                    c = ramp[0] if edge else ramp_at(ramp, tone + 0.25 * (1 - yy / h) - (0.12 if xx > 1 else 0))
                    px[x, y] = c + (255,)


def eye(tile, rng):
    """A 4 x 4 eye in the tile's corner (fixed window): dark lid rim, amber iris, round black pupil, a catch-light."""
    px = tile.load()
    for y in range(tile.size[0]):
        for x in range(tile.size[0]):
            px[x, y] = BROWN[0] + (255,)
    colours = {"r": hexrgb("#3a2510"), "i": hexrgb("#e59a1b"), "o": hexrgb("#c1700f"), "p": (8, 8, 8),
               "h": hexrgb("#fff2c0")}
    for y, row in enumerate(["riir", "iphi", "ippo", "roor"]):
        for x, ch in enumerate(row):
            px[x, y] = colours[ch] + (255,)


def paint_atlas():
    atlas = kit.Atlas()
    atlas.tile("brown", lambda t, r: feathers(t, r, BROWN), 1)
    atlas.tile("cream", lambda t, r: feathers(t, r, CREAM + [hexrgb("#f7eed2")], 8, 6), 2)
    atlas.tile("golden", lambda t, r: hackles(t, r, GOLD), 3)
    atlas.tile("tawny", lambda t, r: fur(t, r, TAWNY), 4)
    atlas.tile("belly", lambda t, r: fur(t, r, [TAWNY[2]] + CREAM, 0.6, 0.3), 5)
    atlas.tile("tuft", lambda t, r: hackles(t, r, DARK + [BROWN[1]]), 6)
    atlas.tile("horn", lambda t, r: smooth(t, r, HORN, 0.95, 0.45), 7)
    atlas.tile("tip", lambda t, r: smooth(t, r, HORN, 0.45, 0.0), 8)
    atlas.tile("cere", lambda t, r: smooth(t, r, YELLOW, 0.95, 0.2), 9)
    atlas.tile("talon", lambda t, r: smooth(t, r, [(12, 12, 14), (30, 30, 32), (70, 68, 64)], 0.9, 0.0), 10)
    atlas.tile("scales", lambda t, r: scales(t, r, YELLOW + [hexrgb("#f2d75a")]), 11)
    atlas.tile("eye", eye, 12, fixed=True)
    atlas.tile("leather", lambda t, r: smooth(t, r, LEATHER, 0.9, 0.2), 15)
    atlas.tile("plate_edge", plate_edge, 18)
    return atlas


# ------------------------------------------------------------------------------------------------ the body

# ------------------------------------------------------------------------------------------------ the wings
#
# A wing is a few thin panels (inner arm, forearm, hand) and five separate primaries, each painted as a whole: one
# feather pattern over the wing's own x (out along the arm) and z (leading edge -8, trailing edge +13), so the panels
# join without a seam, and everything outside a feather is transparent — the trailing edge is the feathers' own tips.
# Seen from above: small dark marginal coverts on the leading edge, lesser coverts with tawny fringes (the pale
# shoulder), median coverts with golden fringes, long greater coverts, then the secondaries; underneath a mottled
# lining and pale barred flight feathers with a dark trailing band.

WING_UNDER = [hexrgb(h) for h in ("#5b4d3e", "#86745c", "#ab9878", "#cdbd9c")]
BAR = hexrgb("#3e3126")
OUTLINE = hexrgb("#1a120b")
SHOULDER = [BROWN[1], BROWN[2], TAWNY[1], TAWNY[2]]


# name, start z, rows, feather width, row depth, tip, colour ramp, tone, fringe
ARM_COVERTS = (
    ("marginal", -8.0, 1, 2.0, 1.6, 0.7, BROWN, 0.1, BROWN[2]),
    ("lesser", -6.4, 2, 2.8, 2.2, 1.0, SHOULDER, 0.35, TAWNY[3]),
    ("median", -2.0, 1, 3.2, 3.0, 1.2, BROWN, 0.3, GOLD[0]),
    ("greater", 1.0, 1, 2.0, 5.0, 1.0, BROWN, 0.25, mix(BROWN[3], CREAM[0], 0.3)),
)
HAND_COVERTS = (
    ("marginal", -8.0, 1, 2.0, 1.6, 0.7, BROWN, 0.1, BROWN[2]),
    ("primary", -6.4, 2, 2.6, 3.2, 1.2, BROWN, 0.15, mix(BROWN[3], CREAM[0], 0.2)),
)


def leading_edge(x, hand):
    """The wing's front outline: tucked in at the body, straight along the arm, sweeping back past the wrist."""
    if hand:
        return -8.0 + max(0.0, x - 37.0) * 0.6
    return -8.0 + max(0.0, 9.0 - x) * 0.5


def remex(x, z, hand):
    """The flight feather lying at (x, z) behind the coverts: (lane, u, distance to its tip, tertial) or None."""
    if hand:
        if x > 38.6:
            return None
        lane, u = lanes(x, 31.3, 1.8)
        end = 12.4 + (x - 31.3) * 0.25 + 0.9 * (1 - u * u)
        tertial = False
    else:
        lane, u = lanes(x, 5.5, 2.0)
        tertial = x < 10.5
        # a gentle S: long tertials at the body, the arm's trailing edge bulging a little, narrowing at the wrist
        end = (14.2 if tertial else 12.6 + 0.8 * math.sin((x - 10.5) / 21 * math.pi)) + 0.9 * (1 - u * u)
    return (lane, u, end - z, tertial) if z <= end else None


def feather_at(x, z, hand, under):
    """(which feather, colour) at (x, z) on one side of the wing, or None outside the wing."""
    zones = HAND_COVERTS if hand else ARM_COVERTS
    if hand == "elbow":
        hand, zones = False, ()
    elif z < leading_edge(x, hand):
        return None
    if hand and x > 40.0 - max(0.0, z + 3.0) * 0.45:
        return None
    for name, za, rows, fw, rh, tip, ramp, tone, fringe in (zones[:1] if hand and under else
                                                             zones[:3] if under else zones):
        hit = scale_rows(x, z, za, rows, fw, rh, tip)
        if not hit:
            continue
        row, col, u, to_tip, depth = hit
        if under:
            c = ramp_at(BROWN, 0.25 + 0.25 * noise("lining", name, row, col) + 0.2 * (1 - to_tip / depth))
            if to_tip < 0.5:
                c = BROWN[3]
        else:
            # lit towards the tip, a pale shaft, a fringe at the tip, soft dark sides where the neighbours overlap
            c = ramp_at(ramp, tone + 0.25 * noise(name, row, col, hand) + 0.3 * (1 - to_tip / depth))
            if abs(u) < 0.14 and to_tip > 0.6:
                c = mix(c, ramp[-1], 0.3)
            if to_tip < 0.5:
                c = fringe
        if abs(u) > 0.86:
            c = mix(c, OUTLINE, 0.5)
        return (name, row, col), c
    hit = remex(x, z, hand)
    if not hit and under and z < 6:
        hit = (math.floor(x / 2), 0.0, 6.0, False)
    if not hit:
        return None
    lane, u, to_tip, tertial = hit
    if under:
        c = ramp_at(WING_UNDER, 0.55 + 0.25 * noise("under", lane, hand) - 0.2 * max(0.0, -u))
        if (z + 0.25 * math.sin(x * 0.7)) % 1.8 < 0.6:
            c = mix(c, BAR, 0.55)
        if abs(u) < 0.16:
            c = mix(c, CREAM[2], 0.45)
        if to_tip < 1.8:
            c = mix(c, BAR, 0.8)
    else:
        ramp = SHOULDER if tertial else BROWN
        k = 0.3 + 0.15 * noise("remex", lane, hand) + 0.12 * u
        if (z + 0.25 * math.sin(x * 0.7)) % 2.2 < 0.7:
            k += 0.12
        c = ramp_at(ramp, k)
        if abs(u) < 0.18:
            c = mix(c, CREAM[0], 0.3)
        if to_tip < 1.6:
            c = mix(c, OUTLINE, 0.4)
        if to_tip < 0.5:
            c = OUTLINE
    if abs(u) > 0.84:
        c = mix(c, OUTLINE, 0.6)
    return ("remex", lane), c


def wing_side(under):
    def colour(x, z, hand):
        here = feather_at(x, z, hand, under)
        if not here:
            return None
        which, c = here
        # the shadow a feather in front casts just behind its tip
        ahead = feather_at(x, z - 0.5, hand, under)
        if ahead and ahead[0] != which:
            c = mix(c, OUTLINE, 0.35)
        return c + (255,)
    return colour


wing_top = wing_side(False)
wing_under = wing_side(True)


def primary_painter(length, width, top):
    """One primary from above or below: broad at the base so the five overlap into one hand, then notched — the
    outer half is a narrow 'finger' with a slot to the next, as on an eagle's spread wing tip."""
    shaft = 0.3 * width

    def paint(image):
        w, h = image.size
        px = image.load()
        for j in range(h):
            for i in range(w):
                s = (i + 0.5) / w * length
                t = (j + 0.5) / h * width if top else width - (j + 0.5) / h * width
                f = s / length
                notch = min(1.0, max(0.0, (f - 0.4) / 0.12))
                lead = shaft * (1 - 0.25 * notch)
                trail = (width - shaft) * (1 - 0.45 * notch)
                if f > 0.78:
                    round_ = max(0.0, 1 - ((f - 0.78) / 0.22) ** 1.6)
                    lead, trail = lead * round_, trail * round_
                d = t - shaft
                if not -lead <= d <= trail:
                    continue
                if top:
                    c = ramp_at(BROWN, 0.3 + 0.12 * d / width + (0.1 if (s + 0.3 * d) % 2.6 < 0.8 else 0) - 0.22 * f)
                else:
                    c = ramp_at(WING_UNDER, 0.6 - 0.15 * f)
                    if (s + 0.3 * d) % 2.0 < 0.7:
                        c = mix(c, BAR, 0.55)
                    if f > 0.72:
                        c = mix(c, BAR, 0.75)
                if abs(d) < 0.3:
                    c = mix(c, CREAM[1], 0.4 if top else 0.55)
                if d < -lead + 0.45 or d > trail - 0.45:
                    c = mix(c, OUTLINE, 0.7)
                px[i, j] = c + (255,)
    return paint


def panel(bone, key, x0, x1, z0, z1, y, hand, thick=0.4):
    """A thin wing panel: painted on top and underneath, see-through elsewhere."""
    def painter(fn, flip):
        def paint(image):
            w, h = image.size
            px = image.load()
            for j in range(h):
                for i in range(w):
                    x = x0 + (i + 0.5) * (x1 - x0) / w
                    dz = (j + 0.5) * (z1 - z0) / h
                    c = fn(x, z1 - dz if flip else z0 + dz, hand)
                    if c:
                        px[i, j] = c
        return paint
    bone.cube(key, (x0, y, z0), (x1, y + thick, z1), "clear",
              paint={"up": (key + "_up", painter(wing_top, False)), "down": (key + "_down", painter(wing_under, True))})


PRIMARIES = (  # pivot x, z, angle about y (+ = forward), length — outermost first
    (39.0, -6.2, 8, 12.5),
    (38.2, -4.4, -4, 16.0),
    (37.2, -2.6, -15, 17.5),
    (36.0, -0.8, -27, 16.5),
    (34.6, 1.0, -39, 14.5),
)


def build_wing(body):
    """The right wing, spread and raised a little; mirrored to the left."""
    root = body.bone("wing_root_r", (5, 28, -6), (0, -6, 22))
    root.rounded("shoulder_r", (3.8, 25.6, -8.6), (8.4, 30, -2.4), "brown", 1.0)
    hum = root.bone("humerus_r", (7, 28.6, -7), (0, -20, 0))
    hum.rounded("humerus_r", (6.2, 28.0, -8.8), (17.8, 29.9, -6.6), "brown", 0.7)
    panel(hum, "inner_r", 5.5, 17.6, -8.0, 15.4, 29.0, False)
    arm = hum.bone("forearm_r", (17.6, 28.6, -7), (0, 42, 0))
    arm.rounded("forearm_r", (17.2, 28.0, -8.6), (31.8, 29.5, -6.8), "brown", 0.6)
    # reaching back past the elbow, under the inner wing: the bent forearm would otherwise leave a wedge open
    panel(arm, "forearm_r", 8.0, 31.5, -8.0, 15.0, 28.6, False)
    hand = arm.bone("hand_r", (31.6, 28.6, -7), (0, -30, 0))
    hand.rounded("hand_r", (31.3, 28.0, -8.4), (38.6, 29.1, -6.8), "brown", 0.5)
    panel(hand, "hand_r", 31.3, 40.2, -8.0, 16.2, 28.2, True)
    for n, (x, z, angle, length) in enumerate(PRIMARIES):
        width, y = 5.6, 27.9 - n * 0.12
        shaft = 0.3 * width
        # each finger lifts a little more than the one behind it: the upturned tips of a soaring eagle
        bone = hand.bone(f"primary_{n + 1}_r", (x, y, z), (0, angle, (5 - n) * 3))
        bone.cube(f"primary_{n + 1}_r", (x, y, z - shaft), (x + length, y + 0.3, z + width - shaft), "clear",
                  paint={"up": (f"primary_{n + 1}_up", primary_painter(length, width, True)),
                         "down": (f"primary_{n + 1}_down", primary_painter(length, width, False))})
    kit.mirror_x(root, body)


# ------------------------------------------------------------------------------------------------ the folded wing
#
# Folded, a wing is a different shape, not the spread one bent: a curved teardrop lying over the flank with every
# feather pointing back — shingled coverts at the shoulder, long tertials along the back, secondaries below stepping
# shorter towards the belly, and the dark primaries ending in points over the rump. It is its own geometry
# (wingfold_r/_l); the animations show either it or the spread wing by scaling the other to nothing.
# s runs back from the shoulder, t down from the top of the back.

FOLD_LENGTH = 37.0


def fold_outline(s):
    """(top, bottom) of the folded wing at s, or None past its tip."""
    if s < 0 or s > FOLD_LENGTH:
        return None
    top = max(0.0, s - 23.0) * 0.33
    bottom = 12.8 * math.sqrt(min(1.0, s / 4.5)) if s < 16 else 12.8 - (s - 16.0) * 0.36
    return (top, bottom) if bottom > top else None


def fold_feather(s, t):
    edge = fold_outline(s)
    if not edge or not edge[0] <= t <= edge[1]:
        return None
    hit = scale_rows(t, s, 0.0, 6, 2.6, 2.4, 1.0)  # coverts: rows down the shoulder, tips pointing back
    if hit and s < 17.0 - 0.7 * t and t < 8.5:
        row, col, u, to_tip, depth = hit
        ramp, tone = (SHOULDER, 0.4) if row < 2 else (BROWN, 0.25)
        c = ramp_at(ramp, tone + 0.25 * noise("fold", row, col) + 0.3 * (1 - to_tip / depth))
        if abs(u) < 0.14 and to_tip > 0.6:
            c = mix(c, ramp[-1], 0.3)
        if to_tip < 0.5:
            c = TAWNY[3] if row < 2 else GOLD[0]
        if abs(u) > 0.86:
            c = mix(c, OUTLINE, 0.5)
        return ("covert", row, col), c
    # remiges, outermost first: tertials along the back, secondaries below them, primaries reaching furthest back
    for kind, t0, t1, width, end_of, ramp, tone in (
            ("tertial", 0.0, 4.5, 2.2, lambda lane: 25.5 + lane * 0.8, SHOULDER, 0.35),
            ("secondary", 4.5, 13.3, 1.6, lambda lane: 24.5 - lane * 0.9, BROWN, 0.3),
            ("primary", 3.0, 12.0, 1.4, lambda lane: FOLD_LENGTH - abs(lane - 3) * 1.7, BROWN, 0.12)):
        if not t0 <= t <= t1:
            continue
        lane, u = lanes(t, t0, width)
        end = end_of(lane)
        tip = 1.6 if kind == "primary" else 0.9
        if s > end - tip * (abs(u) ** 1.5 if kind == "primary" else u * u):
            continue
        to_tip = end - s
        k = tone + 0.15 * noise(kind, lane) - 0.1 * u
        if (s + 0.2 * math.sin(t * 1.3)) % 2.3 < 0.7:
            k += 0.1
        c = ramp_at(ramp, k)
        if abs(u) < 0.2:
            c = mix(c, CREAM[0], 0.3)
        if to_tip < 1.6:
            c = mix(c, OUTLINE, 0.35)
        if abs(u) > 0.8:
            c = mix(c, OUTLINE, 0.6)
        return (kind, lane), c
    return ("down",), ramp_at(BROWN, 0.1)


def fold_colour(s, t):
    here = fold_feather(s, t)
    if not here:
        return None
    which, c = here
    ahead = fold_feather(s - 0.5, t)
    if ahead and ahead[0] != which:
        c = mix(c, OUTLINE, 0.35)
    return c + (255,)


# strips down the flank, (depth in t, lean of the strip's top towards the spine in degrees): the wing wraps over
# the back and tucks in under the belly instead of standing off the body like a board
FOLD_STRIPS = ((3.0, 45.0), (3.4, 22.5), (3.9, 0.0), (3.0, -22.5))


def build_folded_wing(body):
    fold = body.bone("wingfold_r", (4.6, 28.6, -10), (0, -5, 0))
    x, y, t = 4.4, 28.8, 0.0
    z0, z1 = -10.0, -10.0 + FOLD_LENGTH
    for n, (depth, lean) in enumerate(FOLD_STRIPS):
        def paint(image, t0=t, depth=depth, outer=True):
            w, h = image.size
            px = image.load()
            for j in range(h):
                for i in range(w):
                    s_ = (i + 0.5) / w * FOLD_LENGTH
                    if outer:
                        s_ = FOLD_LENGTH - s_
                    c = fold_colour(s_, t0 + (j + 0.5) / h * depth)
                    if c:
                        px[i, j] = c
        rot = ("z", lean) if lean else None
        fold.cube(f"fold_{n}_r", (x, y - depth, z0), (x + 0.3, y, z1), "clear", rot=rot, origin=(x, y, -10),
                  paint={"east": (f"fold_{n}_out", paint),
                         "west": (f"fold_{n}_in", lambda image, t0=t, depth=depth: paint(image, t0, depth, False))})
        a = math.radians(lean)
        x, y, t = x + depth * math.sin(a), y - depth * math.cos(a), t + depth
    kit.mirror_x(fold, body)


# ------------------------------------------------------------------------------------------------ the armour
#
# Barding in light steel, every part a bone of its own so the plugin shows only what the griffin wears and tints it
# to the tier: a chanfron over brow and crown with a crest (eyes left free), lames down the back of the neck, a
# peytral over the breast with guards round the shoulders, and a croupiere over the haunches behind the saddle.

def build_armour(body, chest, neck1, neck2, head):
    helm = head.bone("armor_head", head.pivot)
    armour_plate(helm, "crown_plate", (-3.7, 38.6, -23.0), (3.7, 39.1, -17.0), "up", mirror=False)
    armour_plate(helm, "brow_plate", (-3.9, 35.9, -25.3), (3.9, 38.8, -24.8), "north", mirror=False, scallop=True)
    armour_plate(helm, "temple_plate", (3.6, 35.4, -24.8), (4.0, 38.8, -17.6), "east", scallop=True)
    helm.cube("crest", (-0.45, 39.0, -23.6), (0.45, 40.4, -17.4), "plate_edge")
    helm.cube("crest_peak", (-0.35, 40.2, -22.6), (0.35, 41.6, -19.6), "plate_edge")

    lames = neck1.bone("armor_neck_1", neck1.pivot)
    armour_plate(lames, "nape_lames", (-3.5, 23.8, -10.0), (3.5, 32.4, -9.5), "south", mirror=False, lames=3)
    armour_plate(lames, "neck_side", (3.15, 24.0, -13.4), (3.6, 32.2, -9.5), "east", lames=3, rivets=False)
    upper = neck2.bone("armor_neck_2", neck2.pivot)
    armour_plate(upper, "upper_nape", (-3.2, 30.2, -12.6), (3.2, 35.8, -12.1), "south", mirror=False, lames=2)
    armour_plate(upper, "upper_side", (2.85, 30.4, -16.2), (3.3, 35.6, -12.1), "east", lames=2, rivets=False)

    peytral = chest.bone("armor_chest", chest.pivot)
    armour_plate(peytral, "peytral", (-3.6, 15.6, -16.7), (3.6, 24.6, -16.2), "north", mirror=False, scallop=True)
    armour_plate(peytral, "peytral_wing", (3.5, 15.6, -16.7), (6.6, 24.6, -16.2), "north", rot=("y", -45),
                 origin=(3.5, 20, -16.45), scallop=True)
    armour_plate(peytral, "shoulder_guard", (5.6, 16.4, -14.4), (6.1, 24.6, -9.6), "east", scallop=True)
    peytral.cube("peytral_boss", (-1.4, 18.8, -17.3), (1.4, 21.6, -16.6), "plate_edge")

    croup = body.bone("armor_croup", body.pivot)
    armour_plate(croup, "croupiere", (-4.8, 25.9, 10.2), (4.8, 26.4, 17.6), "up", mirror=False)
    armour_plate(croup, "croup_bevel", (4.7, 25.9, 10.2), (7.4, 26.4, 17.6), "up", rot=("z", -45),
                 origin=(4.7, 26.15, 14))
    armour_plate(croup, "croup_side", (6.6, 19.8, 10.2), (7.1, 24.4, 17.6), "east", scallop=True)


def build_rig():
    root = Bone("root", (0, 0, 0))
    body = root.bone("body_main", (0, 20, 2))
    body.rounded("waist", (-4.8, 15, -4), (4.8, 25.2, 9), "tawny", 1.2, {"down": "belly"})
    body.rounded("haunch", (-6.2, 12.8, 6), (6.2, 25.8, 17), "tawny", 1.5, {"down": "belly"})
    body.cube("belly_fur", (-3.6, 14.2, -3), (3.6, 15.4, 8), "belly")
    body.cube("saddle", (-5.4, 25, 1.5), (5.4, 26.4, 10), "leather")
    body.cube("cantle", (-4.2, 26.4, 8.4), (4.2, 28.2, 10), "leather")
    body.cube("pommel", (-2.4, 26.4, 1.5), (2.4, 27.6, 2.8), "leather")
    body.cube("girth_r", (5.1, 15, 4.4), (5.7, 25.2, 5.8), "leather")
    body.cube("girth_l", (-5.7, 15, 4.4), (-5.1, 25.2, 5.8), "leather")

    chest = body.bone("chest", (0, 20, -8))
    chest.rounded("chest", (-5.8, 14.5, -14.5), (5.8, 27.5, -2), "brown", 1.5, {"north": "cream", "down": "cream"})
    # The feathered breast: three rows of plates stepping forward and down, their lower edges jagged like feather tips.
    for row, (y1, y2, z, inset) in enumerate(((21, 27, -15.2, 0.4), (17.5, 22.5, -15.8, 0.9), (14.2, 18.8, -15.4, 1.6))):
        for i in range(4):
            x1 = -5.4 + inset + i * (10.8 - 2 * inset) / 4
            drop = (0.7 if (i + row) % 2 else 0)
            chest.cube(f"breast_{row}{i}", (x1, y1 - drop, z), (x1 + (10.8 - 2 * inset) / 4 + 0.25, y2, z + 1.4),
                       "cream")
    chest.cube("shoulder_r", (5.2, 22, -13), (6.6, 27.4, -4), "golden")
    chest.cube("shoulder_l", (-6.6, 22, -13), (-5.2, 27.4, -4), "golden")

    # An S-neck: up and forward, then the head held level; hackles hang over it like shingles.
    neck1 = chest.bone("neck_1", (0, 25, -12), (-38, 0, 0))
    neck1.rounded("neck_1", (-3.1, 23.5, -16.5), (3.1, 32.5, -10), "golden", 1.0, {"north": "cream"})
    neck1.cube("collar", (-3.9, 23.2, -15.2), (3.9, 27.2, -9.6), "golden")
    neck2 = neck1.bone("hi_neck_2", (0, 31.5, -14.5), (48, 0, 0))
    neck2.rounded("neck_2", (-2.8, 30, -18.6), (2.8, 36, -12.6), "golden", 0.9, {"north": "cream"})

    head = neck2.bone("head", (0, 35, -18))
    head.rounded("skull", (-3.5, 31, -24), (3.5, 38.6, -16.8), "brown", 1.0)
    head.cube("brow", (-3.9, 35.6, -24.8), (3.9, 37.2, -21), "brown")
    head.cube("crown", (-2.6, 38.2, -23), (2.6, 39, -18), "golden")
    for i in range(3):
        head.cube(f"nape_{i}", (-3.2 + i * 0.4, 32.5 + i * 1.6, -17.2), (3.2 - i * 0.4, 34.3 + i * 1.6, -14.4 + i * 0.6),
                  "golden", rot=("x", -22.5), origin=(0, 33 + i * 1.6, -17))
    head.cube("cheek_r", (3.1, 31.4, -23.6), (3.8, 33.8, -18.5), "brown")
    head.cube("cheek_l", (-3.8, 31.4, -23.6), (-3.1, 33.8, -18.5), "brown")
    head.cube("eye_r", (3.4, 33.6, -23.2), (3.9, 35.6, -21.2), "eye", faces={"east": "eye"})
    head.cube("eye_l", (-3.9, 33.6, -23.2), (-3.4, 35.6, -21.2), "eye", faces={"west": "eye"})
    head.cube("eyestripe_r", (3.5, 33.4, -21.2), (3.95, 34.2, -18), "tuft")
    head.cube("eyestripe_l", (-3.95, 33.4, -21.2), (-3.5, 34.2, -18), "tuft")
    # The beak: a yellow cere, then a horn beak narrowing in three steps to a dark hook bent down over the jaw.
    head.cube("cere", (-2.0, 33.4, -24.6), (2.0, 35.4, -23.6), "cere")
    head.cube("beak_base", (-1.8, 32, -26.2), (1.8, 35.1, -24.4), "horn")
    head.cube("beak_mid", (-1.45, 31.6, -28.0), (1.45, 34.7, -26.0), "horn")
    head.cube("beak_front", (-1.1, 31.0, -29.4), (1.1, 34.1, -27.8), "horn")
    head.cube("culmen", (-1.0, 34.6, -28.6), (1.0, 35.4, -24.6), "horn")
    head.cube("hook", (-1.0, 29.6, -30.2), (1.0, 32.2, -29.0), "tip")
    head.cube("hook_end", (-0.6, 29.0, -30.0), (0.6, 30.0, -29.2), "tip")
    head.cube("gape_r", (1.9, 31.9, -26.2), (2.3, 32.5, -22.4), "cere")
    head.cube("gape_l", (-2.3, 31.9, -26.2), (-1.9, 32.5, -22.4), "cere")
    jaw = head.bone("jaw", (0, 32.2, -23))
    jaw.cube("mandible", (-1.7, 31.0, -26.4), (1.7, 32.0, -23), "horn")
    jaw.cube("mandible_tip", (-1.1, 31.0, -28.4), (1.1, 31.8, -26.4), "tip")
    ear = head.bone("ear_r", (2.6, 37.6, -18.6))
    ear.cube("ear_r", (1.9, 37, -19.4), (3.3, 44, -17.9), "golden", rot=("x", 22.5), origin=(2.6, 37.6, -18.6))
    ear.cube("ear_back_r", (2.0, 37.2, -18.0), (3.2, 42.8, -17.3), "brown", rot=("x", 22.5), origin=(2.6, 37.6, -18.6))
    kit.mirror_x(ear, head)

    # Eagle forelegs: feathered trousers with a ragged fringe, a bare scaled tarsus, three toes splayed forward and
    # one back, each ending in a curved black talon of three steps.
    fore = body.bone("foreleg_r", (4.6, 21.5, -9.5))
    fore.rounded("thigh_r", (2.6, 12, -12), (6.8, 22.5, -7.2), "brown", 0.8, {"north": "cream"})
    for i, (x, dy) in enumerate(((2.4, 0.0), (3.9, 0.8), (5.4, 0.2))):
        fore.cube(f"trouser_r{i}", (x, 9.8 + dy, -12.4), (x + 1.8, 13, -6.8), "cream")
    tarsus = fore.bone("foretarsus_r", (4.7, 11.5, -9.6))
    tarsus.cube("tarsus_r", (3.6, 5.5, -10.8), (5.8, 11.5, -8.4), "scales")
    foot = tarsus.bone("forefoot_r", (4.7, 5.8, -10))
    foot.cube("pad_r", (3.0, 4.0, -12.2), (6.4, 6.0, -8.4), "scales")
    for i, (x, rot) in enumerate(((3.4, 22.5), (4.7, 0), (6.0, -22.5))):
        r = ("y", rot) if rot else None
        o = (x, 5, -12)
        foot.cube(f"toe{i}_r", (x - 0.65, 4.0, -15.2), (x + 0.65, 5.5, -12), "scales", rot=r, origin=o)
        foot.cube(f"knuckle{i}_r", (x - 0.75, 4.1, -14.2), (x + 0.75, 5.7, -13.4), "scales", rot=r, origin=o)
        foot.cube(f"talon{i}_r", (x - 0.5, 3.7, -16.6), (x + 0.5, 5.5, -15.0), "talon", rot=r, origin=o)
        foot.cube(f"talon{i}_mid_r", (x - 0.4, 2.8, -17.2), (x + 0.4, 4.0, -16.2), "talon", rot=r, origin=o)
        foot.cube(f"talon{i}_tip_r", (x - 0.3, 2.0, -17.0), (x + 0.3, 3.0, -16.4), "talon", rot=r, origin=o)
    foot.cube("hallux_r", (4.1, 4.0, -8.6), (5.3, 5.4, -6.8), "scales")
    foot.cube("hallux_talon_r", (4.2, 3.0, -6.9), (5.2, 5.0, -5.8), "talon")
    kit.mirror_x(fore, body)

    # Lion hind legs, digitigrade: thigh into the haunch, the shin slanting back to a high hock, then the foot
    # forward to a round paw.
    hind = body.bone("hindthigh_r", (5.8, 21, 11))
    hind.rounded("hindthigh_r", (3.0, 10.5, 6.8), (8.6, 24, 16.2), "tawny", 1.3, {"down": "belly"})
    shin = hind.bone("hindshin_r", (6.0, 12.5, 13.5), (18, 0, 0))
    shin.rounded("shin_r", (4.3, 4.6, 11.8), (7.7, 13.2, 15.4), "tawny", 0.7)
    hock = shin.bone("hindhock_r", (6.0, 5.5, 13.8), (-28, 0, 0))
    hock.cube("metatarsus_r", (4.5, 1.6, 12.2), (7.5, 6.4, 15.0), "tawny")
    paw = hock.bone("hindpaw_r", (6.0, 2.0, 12.6), (10, 0, 0))
    paw.rounded("paw_r", (3.8, 0, 8.6), (8.2, 3.4, 14.2), "tawny", 0.6, {"down": "belly"})
    for i, x in enumerate((4.3, 5.5, 6.7, 7.9)):
        paw.cube(f"pawtoe{i}_r", (x - 0.6, 0, 7.8), (x + 0.6, 2.4, 9.2), "belly")
    kit.mirror_x(hind, body)

    # The tail: six segments, each tucked one unit into the last, drooping and then lifting at the end into a tuft.
    parent, z, y = body, 16, 23
    angles = (-30, -6, 6, 14)
    for i, a in enumerate(angles):
        seg = parent.bone(f"tail_{i + 1}", (0, y, z), (a, 0, 0))
        w = 1.9 - i * 0.1
        seg.cube(f"tail_{i + 1}", (-w, y - w, z - 0.5), (w, y + w, z + 6.2), "tawny")
        parent, z = seg, z + 5.8
    tuft = parent.bone("tail_tuft", (0, y, z))
    tuft.rounded("tuft", (-2.4, y - 2.4, z - 0.8), (2.4, y + 2.4, z + 6.0), "tuft", 1.0)
    tuft.cube("tuft_tip", (-1.4, y - 1.4, z + 5.6), (1.4, y + 1.4, z + 8.0), "tuft")

    build_wing(body)
    build_folded_wing(body)

    build_armour(body, chest, neck1, neck2, head)
    root.bone("p_rider", (0, 28, 6))
    return root


# ------------------------------------------------------------------------------------------------ animations
#
# BetterModel plays idle and walk by itself (and idle_fly / walk_fly while the mount flies); the plugin starts the
# others. On the ground the wings are folded (the spread wing scaled to nothing), in the air the other way round.
# Values add to the rest pose; right side given, the left mirrors (phase-shifted where the gait wants it).

FOLDED = {"wing_root_r": {"scale": keys((0, (0, 0, 0)))}, "wing_root_l": {"scale": keys((0, (0, 0, 0)))},
          "wingfold_r": {"scale": keys((0, (1, 1, 1)))}, "wingfold_l": {"scale": keys((0, (1, 1, 1)))}}
SPREAD = {"wing_root_r": {"scale": keys((0, (1, 1, 1)))}, "wing_root_l": {"scale": keys((0, (1, 1, 1)))},
          "wingfold_r": {"scale": keys((0, (0, 0, 0)))}, "wingfold_l": {"scale": keys((0, (0, 0, 0)))}}


def tail(length, amp, freq=1):
    return {f"tail_{i + 1}": {"rotation": wave(length, amp * (0.6 + 0.25 * i), -0.12 * i, axis=1, freq=freq)}
            for i in range(4)} | {"tail_tuft": {"rotation": wave(length, amp * 1.4, -0.5, axis=1, freq=freq)}}


def legs(length, amp, pairs):
    """pairs: phase of foreleg_r, foreleg_l, hindthigh_r, hindthigh_l."""
    return gait(length, amp, (("foreleg_r", "foretarsus_r", pairs[0], 1), ("foreleg_l", "foretarsus_l", pairs[1], 1),
                              ("hindthigh_r", "hindshin_r", pairs[2], 0.6), ("hindthigh_l", "hindshin_l", pairs[3], 0.6)))


def flap(length, depth=1.0):
    """One wingbeat: up, then a strong downstroke; the forearm and hand follow a little late, so the wing bends on
    the way up and sweeps flat on the way down; the fingers part on the upstroke."""
    out = {"wing_root_r": {"rotation": wave(length, 30 * depth, 0.25, axis=2, base=(0, 0, -10))},
           "forearm_r": {"rotation": wave(length, 14 * depth, 0.1, axis=2)},
           "hand_r": {"rotation": wave(length, 18 * depth, 0.0, axis=2)}}
    for n in range(5):
        out[f"primary_{n + 1}_r"] = {"rotation": wave(length, 4 * depth, 0.25, axis=1, base=(0, (n - 2) * 1.5, 0))}
    return out


TUCKED = {"foreleg_r": {"rotation": keys((0, (55, 0, 0)))}, "foretarsus_r": {"rotation": keys((0, (-60, 0, 0)))},
          "forefoot_r": {"rotation": keys((0, (40, 0, 0)))},
          "hindthigh_r": {"rotation": keys((0, (50, 0, 0)))}, "hindshin_r": {"rotation": keys((0, (-15, 0, 0)))},
          "hindhock_r": {"rotation": keys((0, (25, 0, 0)))}, "hindpaw_r": {"rotation": keys((0, (30, 0, 0)))},
          "tail_1": {"rotation": keys((0, (24, 0, 0)))}}


def griffin_animations():
    walk, run, beat = 1.2, 0.7, 0.9
    return [
        ("idle", 4.0, True, with_(FOLDED, tail(4.0, 6), {
            "body_main": {"position": wave(4.0, 0.25, axis=1, freq=2)},
            "chest": {"rotation": wave(4.0, 1.2, freq=2)},
            "neck_1": {"rotation": wave(4.0, 2.5, 0.1)},
            "head": {"rotation": keys((0, (0, 0, 0)), (1.0, (0, 14, 0)), (1.8, (-4, 14, 0)), (2.5, (0, -10, 4)),
                                      (3.3, (0, -10, 0)), (4.0, (0, 0, 0)))},
            "wingfold_r": {"rotation": wave(4.0, 1.0, axis=2, freq=2)}})),
        ("walk", walk, True, with_(FOLDED, tail(walk, 9), legs(walk, 22, (0.0, 0.5, 0.5, 0.0)), {
            "body_main": {"position": wave(walk, 0.4, axis=1, freq=2)},
            "neck_1": {"rotation": wave(walk, 3, 0.2, freq=2)},
            "head": {"rotation": wave(walk, -3, 0.2, freq=2)},
            "wingfold_r": {"rotation": wave(walk, 1.5, axis=2, freq=2)}})),
        ("run", run, True, with_(FOLDED, tail(run, 6), legs(run, 34, (0.0, 0.08, 0.5, 0.58)), {
            "body_main": {"rotation": wave(run, 6, 0.25), "position": wave(run, 1.0, axis=1, freq=2)},
            "neck_1": {"rotation": wave(run, -6, 0.25)},
            "wingfold_r": {"rotation": wave(run, 3, axis=2, freq=2)}})),
        ("idle_fly", beat, True, with_(SPREAD, mirror(flap(beat)), mirror(TUCKED), tail(beat, 4), {
            "body_main": {"position": wave(beat, 1.2, 0.75, axis=1)},
            "neck_1": {"rotation": wave(beat, 3, 0.75)}})),
        ("walk_fly", 0.75, True, with_(SPREAD, mirror(flap(0.75, 1.15)), mirror(TUCKED), tail(0.75, 3), {
            "body_main": {"rotation": keys((0, (-8, 0, 0))), "position": wave(0.75, 1.4, 0.75, axis=1)},
            "neck_1": {"rotation": keys((0, (8, 0, 0)))}})),
        ("glide", 3.0, True, with_(SPREAD, mirror(TUCKED), tail(3.0, 3), {
            "wing_root_r": {"rotation": wave(3.0, 3, axis=2, base=(0, 0, -8))},
            "wing_root_l": {"rotation": wave(3.0, 3, 0.5, axis=2, base=(0, 0, 8))},
            "body_main": {"rotation": wave(3.0, 5, axis=2), "position": wave(3.0, 0.6, axis=1)}})),
        ("takeoff", 1.0, False, with_(SPREAD, mirror({
            "wing_root_r": {"rotation": keys((0, (0, 0, 40)), (0.3, (0, 0, 45)), (0.6, (0, 0, -40)), (1.0, (0, 0, 0)))},
            "foreleg_r": {"rotation": keys((0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.6, (40, 0, 0)), (1.0, (55, 0, 0)))},
            "hindthigh_r": {"rotation": keys((0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.6, (40, 0, 0)), (1.0, (50, 0, 0)))}}),
            {"body_main": {"position": keys((0, (0, 0, 0)), (0.3, (0, -2, 0)), (0.6, (0, 3, 0)), (1.0, (0, 0, 0)))}})),
        ("land", 0.8, False, with_(SPREAD, mirror({
            "wing_root_r": {"rotation": keys((0, (0, 0, 30)), (0.4, (0, -25, 40)), (0.8, (0, 0, 0)))},
            "foreleg_r": {"rotation": keys((0, (-30, 0, 0)), (0.4, (-10, 0, 0)), (0.8, (0, 0, 0)))}}),
            {"body_main": {"position": keys((0, (0, 2, 0)), (0.45, (0, -1.5, 0)), (0.8, (0, 0, 0)))}})),
        ("attack", 0.7, False, with_(FOLDED, {
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (0.2, (18, 0, 0)), (0.35, (-28, 0, 0)), (0.7, (0, 0, 0)))},
            "head": {"rotation": keys((0, (0, 0, 0)), (0.2, (10, 0, 0)), (0.35, (-15, 0, 0)), (0.7, (0, 0, 0)))},
            "jaw": {"rotation": keys((0, (0, 0, 0)), (0.2, (25, 0, 0)), (0.35, (0, 0, 0)), (0.7, (0, 0, 0)))},
            "foreleg_r": {"rotation": keys((0, (0, 0, 0)), (0.25, (-50, 0, 0)), (0.4, (20, 0, 0)), (0.7, (0, 0, 0)))},
            "wingfold_r": {"rotation": keys((0, (0, 0, 0)), (0.25, (0, 0, 12)), (0.7, (0, 0, 0)))},
            "wingfold_l": {"rotation": keys((0, (0, 0, 0)), (0.25, (0, 0, -12)), (0.7, (0, 0, 0)))}})),
        ("death", 1.6, False, with_(FOLDED, {
            "body_main": {"rotation": keys((0, (0, 0, 0)), (0.6, (0, 0, 20)), (1.2, (0, 0, 85)), (1.6, (0, 0, 85))),
                          "position": keys((0, (0, 0, 0)), (1.2, (0, -12, 0)), (1.6, (0, -12, 0)))},
            "neck_1": {"rotation": keys((0, (0, 0, 0)), (1.2, (-30, 0, 0)), (1.6, (-30, 0, 0)))}})),
        ("spawn", 0.8, False, with_(FOLDED, {
            "root": {"scale": keys((0, (0.2, 0.2, 0.2)), (0.6, (1.05, 1.05, 1.05)), (0.8, (1, 1, 1)))},
            "neck_1": {"rotation": keys((0, (-20, 0, 0)), (0.8, (0, 0, 0)))}})),
    ]


def main():
    atlas = paint_atlas()
    rig = build_rig()
    made = kit.build("griffin", rig, atlas, HERE)
    print("built", made)
    print("animations", kit.animations(griffin_animations()))
    kit.save_project(os.path.join(HERE, "griffin.bbmodel"))
    for animation, time, picture in (("idle", 0.0, "griffin_preview.png"), ("glide", 0.5, "griffin_flight.png")):
        kit.frame(animation, time)
        print(kit.shots(os.path.join(HERE, picture), [(35, 18), (90, 8), (-145, 25), (0, 10)], target=[0, 22, 0],
                        distance=150))


if __name__ == "__main__":
    main()

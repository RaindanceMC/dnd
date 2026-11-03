"""
Paints a model's faces into one texture atlas, at two pixels per model unit (a "32x" pack's density).

Every face is painted by its material, then bevelled — a light rim where light falls, a dark one where it does not —
so a cube reads as a carved, worn object rather than a coloured box. Colours come from hue-shifted ramps: shadows go
cooler, highlights warmer, the way a painter mixes them.
"""
import colorsys
import math
import random

DENSITY = 2


def hexrgb(text):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4))


def ramp(hexcolour, steps=7, spread=0.42, hue_shift=0.045):
    """From dark and cool to light and warm, around one colour."""
    r, g, b = (c / 255 for c in hexrgb(hexcolour))
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    out = []
    for i in range(steps):
        t = i / (steps - 1) - 0.5
        hue = (h - t * hue_shift * (1 if h < 0.5 else -1)) % 1.0
        light = min(0.97, max(0.04, l + t * spread))
        sat = min(1.0, max(0.0, s * (1.0 - abs(t) * 0.35) + (0.08 if t < 0 else 0)))
        rr, gg, bb = colorsys.hls_to_rgb(hue, light, sat)
        out.append((int(rr * 255), int(gg * 255), int(bb * 255)))
    return out


MATERIALS = {
    "glass": {"colour": "#d4eef5", "alpha": 46, "kind": "glass"},
    "glass_rim": {"colour": "#bfe0ea", "alpha": 175, "kind": "glass"},
    "cork": {"colour": "#a3743f", "kind": "speckle"},
    "string": {"colour": "#d8c49a", "kind": "cloth"},
    "tag": {"colour": "#ead9b0", "kind": "paper"},
    "gold": {"colour": "#e2b13c", "kind": "metal"},
    "gold_dark": {"colour": "#a87818", "kind": "metal"},
    "silver": {"colour": "#c3ccd6", "kind": "metal"},
    "parchment": {"colour": "#e8d6a6", "kind": "paper"},
    "page": {"colour": "#efe0b8", "kind": "page"},
    "wood": {"colour": "#6e4526", "kind": "wood"},
    "wood_light": {"colour": "#9a6a3e", "kind": "wood"},
    "leather": {"colour": "#7a4628", "kind": "leather"},
    "leather_dark": {"colour": "#4e2b17", "kind": "leather"},
    "leather_light": {"colour": "#a5683b", "kind": "leather"},
    "fur": {"colour": "#e6d8c0", "kind": "fur"},
    "feather": {"colour": "#f1f1ec", "kind": "feather"},
    "ribbon": {"colour": "#b8282f", "kind": "cloth"},
    "wax": {"colour": "#b02029", "kind": "wax"},
    "amethyst": {"colour": "#9f6ae6", "kind": "gem"},
    "sapphire": {"colour": "#2d6bdc", "kind": "gem"},
    "ruby": {"colour": "#cf1f3a", "kind": "gem"},
    "void": {"colour": "#2a1240", "kind": "void"},
    "rune": {"colour": "#9a58ff", "kind": "rune"},
    "rope": {"colour": "#c8a066", "kind": "cloth"},
    "liquid": {"colour": "#d23434", "kind": "liquid"},
    "filled": {"colour": "#d23434", "kind": "filled", "colour_from": "liquid"},
    "iron": {"colour": "#8c949e", "kind": "metal"},
}


class Face:
    def __init__(self, material, w, h, side, overrides=None):
        self.material = material
        self.w, self.h = max(1, w), max(1, h)
        self.side = side
        self.overrides = overrides or {}


def paint_face(pixels, x0, y0, face, rng, colours=None, frame=0):
    spec = dict(MATERIALS[face.material])
    source = spec.get("colour_from", face.material)
    if colours and source in colours:
        spec["colour"] = colours[source]
    tones = ramp(spec["colour"])
    kind = spec["kind"]
    w, h = face.w, face.h
    side_light = {"up": 1.0, "north": 0.25, "west": 0.4, "south": 0.1, "east": -0.15, "down": -0.6}[face.side]
    for y in range(h):
        for x in range(w):
            # Base tone: middle of the ramp, lit by the face's direction.
            t = 3 + side_light * 1.2
            noise = rng.uniform(-0.45, 0.45)
            alpha = 255
            if kind == "wood":
                grain = math.sin((x * 0.9 + math.sin(y * 0.35 + x * 0.2) * 1.6) * 1.7)
                t += grain * 0.7 + noise * 0.6
                if rng.random() < 0.012:
                    t -= 1.6
            elif kind == "leather":
                # Smooth, oiled leather: a soft vertical sheen, faint grain, and stitches a pixel in from the edge.
                t += 0.5 - (y / max(1, h - 1)) * 0.9 + noise * 0.35
                if (x == 1 or x == w - 2) and y % 2 == 0 and h > 5 and w > 5:
                    t += 1.6
            elif kind == "metal":
                t += math.cos((y / max(1, h - 1)) * math.pi) * 0.9 + noise * 0.35
                if (x + y * 2) % max(4, w) == 1:
                    t += 1.4
            elif kind == "gem":
                diag = (x / max(1, w - 1) + y / max(1, h - 1))
                t += 1.6 if diag < 0.6 else (-0.8 if diag > 1.4 else 0.4)
                sweep = (frame * 0.35) % 3.0 - 0.5
                if abs(diag - sweep) < 0.18:
                    t += 2.2
                if x == 1 and y == 1:
                    t = 6.5
            elif kind == "glass":
                alpha = spec.get("alpha", 100)
                t = 5 + noise * 0.2
                if y == 0 or y == h - 1:
                    t = 6
                    alpha = min(255, alpha + 70)
                if x == 1 and 0 < y < h - 1 and h > 3:
                    t = 6.6
                    alpha = min(255, alpha + 150)
                elif x == 2 and 1 < y < h - 2 and h > 5:
                    alpha = min(255, alpha + 60)
            elif kind == "liquid":
                t = 4.4 - (y / max(1, h - 1)) * 2.6 + noise * 0.2
                if face.side == "up":
                    t = 5.2 + noise * 0.25 + (0.8 if (x + y) % 7 == frame % 7 else 0)
                elif 1 < x < w - 2 and 1 < y < h - 1 and (x * 3 + (y + frame) * 5) % 41 == 0:
                    t = 6.0
            elif kind == "filled":
                # Liquid seen through polished glass: a vertical gradient, the glass's gloss painted on top.
                t = 4.3 - (y / max(1, h - 1)) * 2.6 + noise * 0.25
                if face.side == "up":
                    t = 5.0 + noise * 0.3
                if y == 0:
                    t += 1.2
                if x == 1 and 0 < y < h - 2 and face.side in ("north", "west", "south", "east"):
                    t = 6.7
                if x == 2 and 1 < y < h * 0.45:
                    t = max(t, 6.0)
                if (x * 5 + (y + frame) * 3) % 29 == 0 and 2 < x < w - 2:
                    t = 6.2
            elif kind in ("paper", "page"):
                t += noise * 0.7 + 0.6
                edge = min(x, y, w - 1 - x, h - 1 - y)
                if edge == 0:
                    t -= 1.3
                if kind == "page" and 2 <= x < w - 2 and y % 3 == 1 and y > 1 and y < h - 2 and rng.random() < 0.82:
                    t = 0.2
            elif kind == "smooth":
                # One surface cut into slices: no edge of its own, so the slices read as one curve; the shading
                # comes from the colour each slice is given.
                if face.side in ("up", "down"):
                    t = 2.2 if face.side == "up" else 3.8  # pre-darkened: the renderer lights a ledge from above, and every slice would draw a line
                t += noise * 0.15
            elif kind == "speckle":
                t += noise * 1.3 + (-1.2 if rng.random() < 0.12 else 0)
            elif kind == "cloth":
                t += math.sin(x * 1.3) * 0.5 + noise * 0.5
            elif kind == "fur":
                t += 0.8 + noise * 1.6 + (1.0 if (x * 7 + y * 3) % 5 == 0 else 0)
            elif kind == "feather":
                t += 0.9 + (0.9 if x == w // 2 else 0) - abs(x - w / 2) / max(1, w) * 1.4 + noise * 0.4
            elif kind == "wax":
                t += noise * 0.4 + (1.6 if (x - w / 2) ** 2 + (y - h / 2) ** 2 < 2 else 0)
            elif kind == "void":
                t = 0.4 + noise * 0.6 + (3.2 if (x * 7 + y * 13 + frame * 5) % 31 == 0 else 0)
            elif kind == "rune":
                # An embroidered sigil: a circle, a diamond inside it, a bright eye, on dark cloth.
                cx, cy = (w - 1) / 2, (h - 1) / 2
                radius = min(w, h) / 2 - 1.0
                d = math.hypot(x - cx, y - cy)
                ring = abs(d - radius) < 0.55
                diamond = abs(abs(x - cx) + abs(y - cy) - radius * 0.62) < 0.55
                eye = d < 0.9
                pulse = 0.7 * math.sin(frame / 8 * math.tau)
                if eye:
                    t = 6.6
                elif ring or diamond:
                    t = 5.2 + pulse
                else:
                    t = 0.6 + noise * 0.4
            # Bevel: the rim facing the light is brighter, the far rim darker.
            if kind not in ("glass", "void", "rune", "smooth"):
                if x == 0 or y == 0:
                    t += 0.9
                if x == w - 1 or y == h - 1:
                    t -= 1.0
            index = max(0, min(len(tones) - 1, int(round(t))))
            colour = tones[index]
            pixels[x0 + x, y0 + y] = colour + (alpha,)


def pack_faces(faces, size):
    """Shelf-packs face rectangles into a size x size atlas; returns their top-left corners."""
    order = sorted(range(len(faces)), key=lambda i: -faces[i].h)
    spots = [None] * len(faces)
    x = y = shelf = 0
    for i in order:
        f = faces[i]
        if x + f.w > size:
            x = 0
            y += shelf
            shelf = 0
        if y + f.h > size:
            return None
        spots[i] = (x, y)
        x += f.w
        shelf = max(shelf, f.h)
    return spots


def layout(faces):
    for size in (64, 128, 256):
        spots = pack_faces(faces, size)
        if spots:
            return spots, size
    raise ValueError("the faces do not fit a 256 atlas")


def paint(faces, seed=11, colours=None, frame=0):
    """One frame of the atlas. Each face has its own random stream, so a frame changes only what animates."""
    from PIL import Image
    spots, size = layout(faces)
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pixels = image.load()
    for index, (face, (x, y)) in enumerate(zip(faces, spots)):
        paint_face(pixels, x, y, face, random.Random(seed * 1000 + index), colours, frame)
    return image, spots, size


ANIMATED = {"gem", "liquid", "filled", "void", "rune"}


def animates(faces):
    return any(MATERIALS[face.material]["kind"] in ANIMATED for face in faces)


def frames(faces, count=8, seed=11, colours=None):
    """A vertical strip of frames, the way Minecraft wants an animated texture."""
    from PIL import Image
    first, spots, size = paint(faces, seed, colours, 0)
    strip = Image.new("RGBA", (size, size * count), (0, 0, 0, 0))
    strip.paste(first, (0, 0))
    for frame in range(1, count):
        strip.paste(paint(faces, seed, colours, frame)[0], (0, size * frame))
    return strip, first, spots, size

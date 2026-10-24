"""
The magic items' shapes. A "round" part is two boxes, one turned 45 degrees about its axis, so it reads as an
octagon from every side — the usual way to round things off in a Minecraft model.
"""


def box(name, start, end, material, sides=None, rotation=None):
    return {"name": name, "from": list(start), "to": list(end), "material": material, "sides": sides or {},
            "rotation": rotation}


def round_y(name, cx, cz, y0, y1, r, material, sides=None):
    """
    A round column standing up: a plus of two boxes, each long on one axis and short on the other, so the corners
    step in. The second box sits a hair lower and higher than the first so their tops never fight for the same plane.
    """
    short = r * 0.62
    a = box(name, (cx - r, y0, cz - short), (cx + r, y1, cz + short), material, sides)
    b = box(name + "_b", (cx - short, y0 + 0.01, cz - r), (cx + short, y1 - 0.01, cz + r), material, sides)
    return [a, b]


def round_x(name, x0, x1, cy, cz, r, material, sides=None):
    """A round roll lying along x, made the same way."""
    short = r * 0.62
    a = box(name, (x0, cy - short, cz - r), (x1, cy + short, cz + r), material, sides)
    b = box(name + "_b", (x0 + 0.01, cy - r, cz - short), (x1 - 0.01, cy + r, cz + short), material, sides)
    return [a, b]


def ring_z(name, cx, cy, r, thickness, z0, z1, material):
    """An octagonal ring standing in the x-y plane: eight bars."""
    parts = []
    side = r * 0.82
    t = thickness
    parts.append(box(name + "_bottom", (cx - side / 2, cy - r, z0), (cx + side / 2, cy - r + t, z1), material))
    parts.append(box(name + "_top", (cx - side / 2, cy + r - t, z0), (cx + side / 2, cy + r, z1), material))
    parts.append(box(name + "_left", (cx - r, cy - side / 2, z0), (cx - r + t, cy + side / 2, z1), material))
    parts.append(box(name + "_right", (cx + r - t, cy - side / 2, z0), (cx + r, cy + side / 2, z1), material))
    d = r * 0.707
    for label, (dx, dy) in {"tr": (1, 1), "tl": (-1, 1), "br": (1, -1), "bl": (-1, -1)}.items():
        mx, my = cx + dx * (d - t / 2) * 0.93, cy + dy * (d - t / 2) * 0.93
        angle = 45 if dx * dy > 0 else -45
        parts.append(box(name + "_" + label, (mx - side / 2, my - t / 2, z0), (mx + side / 2, my + t / 2, z1),
                         material, None, {"angle": -angle, "axis": "z", "origin": [mx, my, (z0 + z1) / 2]}))
    return parts


# ---------------------------------------------------------------- potions

def round_flask(collar):
    parts = []
    parts += round_y("base", 8, 8, 0, 0.6, 3.0, "glass_rim")
    parts += round_y("liquid_low", 8, 8, 0.6, 1.6, 3.4, "liquid")
    parts += round_y("liquid_belly", 8, 8, 1.6, 4.6, 4.1, "liquid")
    parts += round_y("glass_low", 8, 8, 0.6, 1.6, 3.8, "glass")
    parts += round_y("glass_belly", 8, 8, 1.6, 5.6, 4.5, "glass")
    parts += round_y("glass_high", 8, 8, 5.6, 6.6, 3.8, "glass")
    parts += round_y("glass_shoulder", 8, 8, 6.6, 7.6, 2.8, "glass")
    parts += round_y("neck", 8, 8, 7.6, 10.5, 1.3, "glass_rim")
    parts += round_y("lip", 8, 8, 10.5, 11.25, 1.8, "glass_rim")
    parts += round_y("cork", 8, 8, 11, 12.75, 1.15, "cork")
    parts += round_y("cork_top", 8, 8, 12.75, 13.25, 1.35, "cork")
    parts += round_y("string", 8, 8, 9.1, 9.7, 1.5, "string")
    parts.append(box("tag_string", (9.3, 6.5, 7.9), (9.7, 9.3, 8.1), "string", None,
                     {"angle": -22.5, "axis": "z", "origin": [9.5, 9.2, 8]}))
    parts.append(box("tag", (9.5, 4.5, 7.85), (12.5, 7.0, 8.15), "tag", None,
                     {"angle": -22.5, "axis": "z", "origin": [10, 7, 8]}))
    if collar:
        parts += round_y("collar", 8, 8, 7.6, 8.4, 3.2, "gold")
        parts += round_y("foot", 8, 8, 0, 0.6, 3.2, "gold")
    return parts


def tall_bottle(stone):
    parts = []
    parts += round_y("foot", 8, 8, 0, 0.75, 3.2, "gold_dark")
    parts += round_y("liquid", 8, 8, 0.75, 6.4, 2.8, "liquid")
    parts += round_y("glass_body", 8, 8, 0.75, 8.5, 3.2, "glass")
    parts += round_y("glass_shoulder", 8, 8, 8.5, 9.5, 2.3, "glass")
    parts += round_y("neck", 8, 8, 9.5, 12, 1.0, "glass_rim")
    parts += round_y("lip", 8, 8, 12, 12.6, 1.5, "gold")
    parts += round_y("ring_low", 8, 8, 2.0, 2.6, 3.55, "gold")
    parts += round_y("ring_high", 8, 8, 6.8, 7.4, 3.55, "gold")
    for i, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        x, z = 8 + dx * 3.45, 8 + dz * 3.45
        parts.append(box("cage_%d" % i, (x - 0.3, 0.75, z - 0.3), (x + 0.3, 8.6, z + 0.3), "gold"))
    parts.append(box("stopper", (7.0, 12.6, 7.0), (9.0, 14.8, 9.0), stone, None,
                     {"angle": 45, "axis": "y", "origin": [8, 13.7, 8]}))
    parts.append(box("stopper_tip", (7.55, 14.8, 7.55), (8.45, 15.6, 8.45), stone, None,
                     {"angle": 45, "axis": "y", "origin": [8, 15.2, 8]}))
    return parts


def vial():
    parts = []
    parts += round_y("liquid", 8, 8, 0.6, 7.6, 1.3, "liquid")
    parts += round_y("glass_tip", 8, 8, 0.4, 1.5, 1.15, "glass")
    parts += round_y("glass", 8, 8, 1.5, 11, 1.7, "glass")
    parts += round_y("lip", 8, 8, 11, 11.6, 2.0, "glass_rim")
    parts += round_y("cork", 8, 8, 11.4, 13.6, 1.4, "cork")
    parts += round_y("strap", 8, 8, 4.0, 5.2, 1.95, "leather")
    parts += round_y("buckle", 8, 8, 4.2, 5.0, 2.05, "gold")
    return parts


# ---------------------------------------------------------------- the rest

def scroll():
    parts = [box("page", (3.5, 3, 7.7), (12.5, 12, 8.3), "parchment", {"north": "page", "south": "page"})]
    parts += round_x("roll_top", 2.75, 13.25, 12.6, 8, 1.35, "parchment")
    parts += round_x("roll_bottom", 2.75, 13.25, 2.4, 8, 1.35, "parchment")
    for label, y in (("top", 12.6), ("bottom", 2.4)):
        parts += round_x("knob_left_" + label, 1.6, 2.75, y, 8, 0.9, "wood")
        parts += round_x("knob_right_" + label, 13.25, 14.4, y, 8, 0.9, "wood")
        parts += round_x("cap_left_" + label, 1.1, 1.6, y, 8, 0.65, "gold")
        parts += round_x("cap_right_" + label, 14.4, 14.9, y, 8, 0.65, "gold")
    parts.append(box("ribbon", (7.3, 3.5, 8.3), (8.7, 12.4, 8.55), "ribbon"))
    parts.append(box("ribbon_tail_l", (6.6, 1.2, 8.35), (7.6, 3.6, 8.55), "ribbon", None,
                     {"angle": 22.5, "axis": "z", "origin": [7.1, 3.5, 8.4]}))
    parts.append(box("ribbon_tail_r", (8.4, 1.2, 8.35), (9.4, 3.6, 8.55), "ribbon", None,
                     {"angle": -22.5, "axis": "z", "origin": [8.9, 3.5, 8.4]}))
    seal = box("seal", (6.6, 4.4, 8.45), (9.4, 7.2, 9.05), "wax")
    seal_45 = box("seal_45", (6.85, 4.65, 8.5), (9.15, 6.95, 9.1), "wax", None,
                  {"angle": 45, "axis": "z", "origin": [8, 5.8, 8.8]})
    parts += [seal, seal_45]
    return parts


def wand():
    parts = []
    parts += round_y("pommel", 8, 8, 0, 0.9, 0.95, "gold")
    parts += round_y("pommel_gem", 8, 8, -0.4, 0.1, 0.5, "amethyst")
    parts += round_y("grip", 8, 8, 0.9, 5.2, 0.85, "leather")
    parts += round_y("band_low", 8, 8, 0.9, 1.4, 1.0, "gold")
    parts += round_y("band_high", 8, 8, 4.8, 5.4, 1.0, "gold")
    parts += round_y("shaft_low", 8, 8, 5.4, 9.0, 0.7, "wood_light")
    parts += round_y("shaft_high", 8, 8, 9.0, 12.2, 0.58, "wood_light")
    for i, (y, dx, dz) in enumerate(((6, 1, 0), (7, 0, 1), (8, -1, 0), (9, 0, -1), (10, 1, 0), (11, 0, 1))):
        r = 0.72 if y < 9 else 0.6
        parts.append(box("wire_%d" % i, (8 + dx * r - 0.25, y, 8 + dz * r - 0.25),
                         (8 + dx * r + 0.25, y + 0.9, 8 + dz * r + 0.25), "gold"))
    parts += round_y("ferrule", 8, 8, 12.2, 12.9, 0.9, "gold")
    for i, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        parts.append(box("prong_%d" % i, (8 + dx * 0.75 - 0.15, 12.6, 8 + dz * 0.75 - 0.15),
                         (8 + dx * 0.75 + 0.15, 14.2, 8 + dz * 0.75 + 0.15), "gold", None,
                         {"angle": 22.5 * (dx + dz), "axis": "z" if dx else "x", "origin": [8, 12.7, 8]}))
    parts.append(box("crystal", (7.25, 12.9, 7.25), (8.75, 15.6, 8.75), "amethyst", None,
                     {"angle": 45, "axis": "y", "origin": [8, 14.2, 8]}))
    parts.append(box("crystal_tip", (7.65, 15.6, 7.65), (8.35, 16.4, 8.35), "amethyst", None,
                     {"angle": 45, "axis": "y", "origin": [8, 16, 8]}))
    parts.append(box("shard_a", (8.6, 13.0, 7.7), (9.2, 14.6, 8.3), "amethyst", None,
                     {"angle": -22.5, "axis": "z", "origin": [8.9, 13, 8]}))
    parts.append(box("shard_b", (6.8, 13.0, 7.7), (7.4, 14.3, 8.3), "amethyst", None,
                     {"angle": 22.5, "axis": "z", "origin": [7.1, 13, 8]}))
    return parts


def ring():
    parts = ring_z("band", 8, 7.5, 4.3, 1.1, 7.0, 9.0, "gold")
    parts += round_y("bezel", 8, 8, 11.6, 12.4, 1.7, "gold_dark")
    for i, (dx, dz) in enumerate(((1, 1), (1, -1), (-1, 1), (-1, -1))):
        parts.append(box("prong_%d" % i, (8 + dx * 1.15 - 0.2, 12.2, 8 + dz * 1.15 - 0.2),
                         (8 + dx * 1.15 + 0.2, 13.9, 8 + dz * 1.15 + 0.2), "gold"))
    parts.append(box("gem", (6.9, 12.4, 6.9), (9.1, 14.0, 9.1), "sapphire", None,
                     {"angle": 45, "axis": "y", "origin": [8, 13.2, 8]}))
    parts.append(box("gem_table", (7.45, 14.0, 7.45), (8.55, 14.6, 8.55), "sapphire"))
    parts.append(box("shoulder_l", (5.3, 10.4, 7.2), (6.8, 11.9, 8.8), "gold", None,
                     {"angle": 22.5, "axis": "z", "origin": [6, 11, 8]}))
    parts.append(box("shoulder_r", (9.2, 10.4, 7.2), (10.7, 11.9, 8.8), "gold", None,
                     {"angle": -22.5, "axis": "z", "origin": [10, 11, 8]}))
    return parts


def amulet():
    parts = []
    for i in range(11):
        t = i / 10
        x_l, x_r = 6.6 - t * 1.6, 9.4 + t * 1.6
        y = 9.4 + t * 5.8
        horizontal = i % 2 == 0
        size = (1.0, 0.75) if horizontal else (0.6, 1.0)
        for side, x in (("l", x_l), ("r", x_r)):
            parts.append(box("link_%s%d" % (side, i), (x - size[0] / 2, y - size[1] / 2, 7.7),
                             (x + size[0] / 2, y + size[1] / 2, 8.3), "silver"))
    parts.append(box("link_top", (6.6, 15.0, 7.7), (9.4, 15.6, 8.3), "silver"))
    parts += round_y("bail", 8, 8, 8.6, 9.6, 0.6, "gold")
    frame = box("frame", (5.0, 2.0, 7.3), (11.0, 8.0, 8.7), "gold")
    frame_45 = box("frame_45", (5.6, 2.6, 7.35), (10.4, 7.4, 8.65), "gold", None,
                   {"angle": 45, "axis": "z", "origin": [8, 5, 8]})
    parts += [frame, frame_45]
    parts.append(box("heart_l", (6.3, 4.6, 6.9), (8.1, 6.7, 9.1), "ruby"))
    parts.append(box("heart_r", (7.9, 4.6, 6.9), (9.7, 6.7, 9.1), "ruby"))
    parts.append(box("heart_point", (6.9, 3.2, 6.95), (9.1, 5.0, 9.05), "ruby", None,
                     {"angle": 45, "axis": "z", "origin": [8, 4.4, 8]}))
    for i, (x, y) in enumerate(((5.0, 5.0), (11.0, 5.0), (8.0, 1.5), (8.0, 8.4))):
        parts.append(box("bead_%d" % i, (x - 0.45, y - 0.45, 7.2), (x + 0.45, y + 0.45, 8.8), "gold_dark"))
    return parts


def boots():
    """A tall riding boot, toe towards the viewer, with a gilded wing at the ankle."""
    parts = [
        box("sole", (5.0, 0, 4.0), (10.5, 0.6, 13.0), "leather_dark"),
        box("sole_toe", (5.6, 0, 13.0), (9.9, 0.6, 13.8), "leather_dark"),
        box("heel", (5.2, 0, 4.0), (10.3, 1.5, 6.8), "leather_dark"),
        box("foot", (5.3, 0.6, 6.0), (10.2, 3.4, 12.6), "leather"),
        box("toe", (5.8, 0.6, 12.6), (9.7, 2.8, 13.5), "leather"),
        box("instep", (5.6, 3.4, 8.4), (9.9, 5.4, 11.4), "leather"),
        box("ankle", (5.3, 1.5, 4.2), (10.2, 6.5, 8.6), "leather"),
        box("calf", (5.5, 6.5, 4.4), (10.0, 10.6, 8.6), "leather"),
        box("shaft_front", (5.7, 5.4, 8.6), (9.8, 10.0, 9.3), "leather"),
        box("cuff", (5.0, 10.4, 3.9), (10.5, 12.4, 9.5), "leather_light"),
        box("cuff_lip", (5.2, 12.4, 4.1), (10.3, 12.8, 9.3), "leather_dark"),
        box("strap", (5.15, 4.6, 4.05), (10.35, 5.5, 8.75), "leather_dark"),
        box("buckle", (6.9, 4.4, 8.75), (8.6, 5.7, 9.05), "gold"),
    ]
    for i, y in enumerate((6.1, 7.4, 8.7)):
        for j, angle in enumerate((22.5, -22.5)):
            parts.append(box("lace_%d_%d" % (i, j), (6.5, y, 9.3 + j * 0.05), (9.0, y + 0.4, 9.5 + j * 0.05),
                             "string", None, {"angle": angle, "axis": "z", "origin": [7.75, y + 0.2, 9.4]}))
    for i, y in enumerate((6.1, 7.4, 8.7, 9.6)):
        for x in (6.2, 9.0):
            parts.append(box("eyelet_%d_%d" % (i, int(x)), (x - 0.2, y - 0.05, 9.25), (x + 0.2, y + 0.45, 9.45),
                             "gold"))
    for i, (y, length) in enumerate(((6.2, 4.6), (7.1, 3.9), (8.0, 3.1), (8.9, 2.3))):
        parts.append(box("wing_%d" % i, (10.2, y, 7.6 - length), (10.55, y + 0.8, 7.6), "gold", None,
                         {"angle": 22.5, "axis": "x", "origin": [10.4, y + 0.4, 7.6]}))
    parts.append(box("wing_root", (10.15, 6.0, 6.8), (10.6, 9.8, 7.9), "gold_dark"))
    return parts


def mirrored_z(part):
    """The same part with front and back swapped, so the boot's laces face the viewer."""
    out = dict(part)
    out["from"] = [part["from"][0], part["from"][1], 16 - part["to"][2]]
    out["to"] = [part["to"][0], part["to"][1], 16 - part["from"][2]]
    sides = dict(part["sides"])
    if "north" in sides or "south" in sides:
        sides["north"], sides["south"] = part["sides"].get("south"), part["sides"].get("north")
        sides = {k: v for k, v in sides.items() if v}
    out["sides"] = sides
    if part["rotation"]:
        rotation = dict(part["rotation"])
        origin = list(rotation["origin"])
        origin[2] = 16 - origin[2]
        rotation["origin"] = origin
        if rotation["axis"] == "x":
            rotation["angle"] = -rotation["angle"]
        rotation["angle"] = -rotation["angle"] if rotation["axis"] in ("x", "y") and False else rotation["angle"]
        out["rotation"] = rotation
    return out


def bag():
    parts = []
    parts += round_y("bottom", 8, 8, 0, 0.8, 3.1, "leather_dark")
    parts += round_y("belly", 8, 8, 0.8, 6.4, 4.3, "leather")
    parts += round_y("upper", 8, 8, 6.4, 7.6, 3.5, "leather")
    parts += round_y("cinch", 8, 8, 7.6, 8.6, 2.2, "leather_dark")
    parts += round_y("cord", 8, 8, 7.8, 8.4, 2.45, "rope")
    parts += round_y("neck", 8, 8, 8.6, 9.6, 1.9, "leather")
    parts += round_y("flare", 8, 8, 9.6, 11.0, 2.7, "leather", {"up": "void"})
    for i, (dx, angle) in enumerate(((-1, 22.5), (1, -22.5))):
        x = 8 + dx * 1.6
        parts.append(box("tassel_%d" % i, (x - 0.22, 4.6, 12.1), (x + 0.22, 8.1, 12.5), "rope", None,
                         {"angle": angle, "axis": "z", "origin": [x, 8, 12.3]}))
        parts.append(box("tassel_tip_%d" % i, (x - 0.4 + dx * 1.1, 3.6, 12.05), (x + 0.4 + dx * 1.1, 4.8, 12.55),
                         "gold"))
    parts.append(box("patch", (5.3, 1.6, 12.3), (10.7, 7.0, 12.45), "rune"))
    for i, x in enumerate((4.3, 11.7)):
        parts.append(box("seam_%d" % i, (x - 0.15, 0.9, 7.6), (x + 0.15, 6.3, 8.4), "string"))
    return parts


POTIONS = {
    "potion_of_healing": ("round_flask", {"liquid": "#d1323b"}),
    "potion_of_greater_healing": ("round_flask_collar", {"liquid": "#c0183f"}),
    "potion_of_superior_healing": ("tall_bottle_ruby", {"liquid": "#9b1145"}),
    "potion_of_supreme_healing": ("tall_bottle_amethyst", {"liquid": "#d0168f"}),
    "potion_of_heroism": ("vial", {"liquid": "#e9a51e"}),
    "potion_of_speed": ("vial", {"liquid": "#22b4dc"}),
}

SCROLL_SEALS = {"common": "#b02029", "uncommon": "#2f8f3a", "rare": "#2b5fd0", "very_rare": "#8a2fc9",
                "legendary": "#e0a51c"}

SHAPES = {
    "round_flask": round_flask(False),
    "round_flask_collar": round_flask(True),
    "tall_bottle_ruby": tall_bottle("ruby"),
    "tall_bottle_amethyst": tall_bottle("amethyst"),
    "vial": vial(),
    "spell_scroll": scroll(),
    "wand_of_magic_missiles": wand(),
    "ring_of_protection": ring(),
    "amulet_of_health": amulet(),
    "boots_of_striding": boots(),
    "bag_of_holding": bag(),
}

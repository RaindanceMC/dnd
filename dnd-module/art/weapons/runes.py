"""
Rune overlays for +1, +2 and +3 swords and axes: a second texture layer, drawn over the vanilla weapon by name.

The vanilla textures are read from the local client jar only to find where the blade is; none of them is copied into
the pack. Blade pixels are the ones that are not wood: the rune line runs inside the blade, never on its edge, and
pulses. +1 is ice blue and sparse, +2 violet, +3 gold and dense.

    python3 runes.py ~/.cache/rains-e2e/minecraft-client-26.3.jar
"""
import colorsys
import io
import json
import math
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules", "dnd", "pack",
                      "assets", "rainsadventurers")
NS = "rainsadventurers"
BASES = ["iron_sword", "diamond_sword", "netherite_sword", "iron_axe", "diamond_axe", "netherite_axe"]
TIERS = {1: ((120, 220, 255), 4), 2: ((190, 120, 255), 3), 3: ((255, 205, 80), 2)}
FRAMES = 8


def is_wood(rgba):
    r, g, b, a = rgba
    if a < 128:
        return False
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    return 0.0 <= h < 0.17 and s > 0.12 and l < 0.6


def blade(texture):
    pixels = texture.load()
    w, h = texture.size
    metal = {(x, y) for y in range(h) for x in range(w) if pixels[x, y][3] > 128 and not is_wood(pixels[x, y])}
    inner = {(x, y) for (x, y) in metal
             if all((x + dx, y + dy) in metal for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    if len(inner) < 6:
        # A thin blade has no inside: run the runes along its spine, the pixels with the most metal around them.
        inner = {(x, y) for (x, y) in metal
                 if sum((x + dx, y + dy) in metal for dx in (-1, 0, 1) for dy in (-1, 0, 1)) >= 6}
    return metal, inner


def overlay(texture, tier):
    colour, spacing = TIERS[tier]
    metal, inner = blade(texture)
    w, h = texture.size
    strip = Image.new("RGBA", (w, h * FRAMES), (0, 0, 0, 0))
    pixels = strip.load()
    runes = sorted(inner, key=lambda p: (p[0] - p[1], p[0]))
    for frame in range(FRAMES):
        glow = 0.5 + 0.5 * math.sin(frame / FRAMES * math.tau)
        for index, (x, y) in enumerate(runes):
            if (x + y * 2) % spacing:
                continue
            travel = 0.5 + 0.5 * math.sin((index / max(1, len(runes))) * math.tau * 2 - frame / FRAMES * math.tau)
            alpha = int(140 + 115 * min(1.0, glow * 0.5 + travel * 0.6))
            bright = tuple(min(255, int(c + (255 - c) * travel * 0.45)) for c in colour)
            pixels[x, y + h * frame] = bright + (alpha,)
        for (x, y) in metal - inner:
            if (x * 3 + y + frame * 2) % 17 == 0 and y < h * 0.55:
                pixels[x, y + h * frame] = (255, 255, 255, int(120 * glow))
    return strip


def main(jar):
    with zipfile.ZipFile(jar) as client:
        for base in BASES:
            texture = Image.open(io.BytesIO(client.read("assets/minecraft/textures/item/%s.png" % base))).convert("RGBA")
            for tier in TIERS:
                name = "%s_plus_%d" % (base, tier)
                folder = os.path.join(ASSETS, "textures", "item", "magic", "runes")
                os.makedirs(folder, exist_ok=True)
                overlay(texture, tier).save(os.path.join(folder, name + ".png"))
                with open(os.path.join(folder, name + ".png.mcmeta"), "w") as out:
                    json.dump({"animation": {"frametime": 2, "interpolate": True}}, out)
                model = {"parent": "minecraft:item/handheld",
                         "textures": {"layer0": "minecraft:item/" + base,
                                      "layer1": NS + ":item/magic/runes/" + name}}
                path = os.path.join(ASSETS, "models", "item", "magic", "weapon", name + ".json")
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "w") as out:
                    json.dump(model, out, indent=1)
                item = os.path.join(ASSETS, "items", "magic", "weapon", name + ".json")
                os.makedirs(os.path.dirname(item), exist_ok=True)
                with open(item, "w") as out:
                    json.dump({"model": {"type": "minecraft:model", "model": NS + ":item/magic/weapon/" + name}}, out,
                              indent=1)
            preview = Image.new("RGBA", (16 * 4, 16), (0, 0, 0, 0))
            preview.paste(texture, (0, 0))
            for tier in TIERS:
                frame = Image.open(os.path.join(ASSETS, "textures", "item", "magic", "runes",
                                                "%s_plus_%d.png" % (base, tier))).crop((0, 0, 16, 16))
                combined = texture.copy()
                combined.alpha_composite(frame)
                preview.paste(combined, (16 * tier, 0))
            preview.resize((64 * 4, 64), Image.NEAREST).save(os.path.join(HERE, base + ".png"))
            print(base)


if __name__ == "__main__":
    main(sys.argv[1])

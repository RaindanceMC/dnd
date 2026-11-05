"""
Lays the Melee skill's canvas out the way the game draws it — the branch's painting, the frames and lines over it, the
icons in their slots — for a made-up adventurer: the first window, then scrolled one column right with the mastery badge
locked, open and taken; three times the size. Needs the client's textures in /tmp/mcassets.

    python3 preview.py [out.png]
"""
import math
import os
import sys

import yaml
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DND = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules",
                   "skills")
FONT = os.path.join(DND, "pack", "assets", "rainsadventurers", "textures", "font")
MC = "/tmp/mcassets/assets/minecraft/textures"
SOME = {"brawler": 3, "crusher": 2, "dual_wielder": 1, "bloodthirst": 1, "veteran": 1}


def state(node, nid, ranks):
    rank = ranks.get(nid, 0)
    if node.get("mastery"):
        return "maxed" if rank else "open" if all(ranks.get(o, 0) for o in node.get("requires", [])) else "locked"
    if rank >= node.get("ranks", 1):
        return "maxed"
    if rank > 0:
        return "owned"
    if any(ranks.get(o, 0) > 0 for o in node.get("excludes", [])):
        return "barred"
    req = node.get("requires", [])
    return "open" if not req or any(ranks.get(o, 0) > 0 for o in req) else "locked"


def icon(name):
    for folder in ("item", "block"):
        path = os.path.join(MC, folder, name + ".png")
        if os.path.exists(path):
            img = Image.open(path).convert("RGBA")
            return img.crop((0, 0, 16, 16)) if img.height > 16 else img
    return None


CORE_UI = os.path.join(HERE, "..", "..", "..", "..", "..", "RainsCore", "src", "main", "resources", "de", "raindancer",
                       "core", "pack", "assets", "rainscore", "textures", "item", "ui")


def core_ui(name):
    """Core's own menu arrows, as a viewer wearing the pack sees them."""
    return Image.open(os.path.join(CORE_UI, name + ".png")).convert("RGBA")


def page(branch, tree, ranks, left=0):
    """The window over columns left .. left + 4 of the branch's canvas."""
    window = Image.open(os.path.join(FONT, "tree_scene", branch + ".png")).convert("RGBA")
    nodes = {k: v for k, v in tree["nodes"].items() if v["branch"] == branch}
    seen = {k: v for k, v in nodes.items() if left <= v["at"][0] < left + 5}
    sprites = []
    for nid, node in seen.items():
        for need in node.get("requires", []):
            other = nodes[need]
            if need not in seen:
                continue
            dc, dr = node["at"][0] - other["at"][0], node["at"][1] - other["at"][1]
            shape = "across" if dr == 0 else "down" if dc == 0 else "falling" if dc == dr else "rising"
            lit = state(node, nid, ranks) in ("owned", "maxed") and state(other, need, ranks) in ("owned", "maxed")
            sprites.append((shape + ("_lit" if lit else "_dim"), other["at"][1] * 2 + dr,
                            (other["at"][0] - left) * 2 + dc))
    for nid, node in seen.items():
        kind = "mastery_" if node.get("mastery") else "frame_"
        sprites.append((kind + state(node, nid, ranks), node["at"][1] * 2, (node["at"][0] - left) * 2))
    for name, row, col in sprites:
        sprite = Image.open(os.path.join(FONT, "tree", name + ".png")).convert("RGBA")
        window.alpha_composite(sprite, (16 + 18 * col - sprite.width // 2, 26 + 18 * row - sprite.height // 2))
    for nid, node in seen.items():
        img = icon(node["icon"])
        if img:
            window.alpha_composite(img, (8 + 36 * (node["at"][0] - left), 18 + 36 * node["at"][1]))
    for col, name in ((0, "ui:arrow_back"), (2, "ui:arrow_up"), (3, "ui:arrow_left"),
                      (4, tree["branches"][branch]["icon"]), (5, "ui:arrow_right"), (6, "ui:arrow_down"),
                      (7, "writable_book"), (8, "barrier")):
        img = core_ui(name[3:]) if name.startswith("ui:") else icon(name)
        if img:
            window.alpha_composite(img, (8 + 18 * col, 18 + 18 * 5))
    return window


def main():
    tree = yaml.safe_load(open(os.path.join(DND, "trees", "melee.yml")))
    branch = next(iter(tree["branches"]))
    everything = {nid: node.get("ranks", 1) for nid, node in tree["nodes"].items()
                  if not node.get("mastery") and nid != "great_weapon"}
    pages = [page(branch, tree, SOME), page(branch, tree, SOME, 1), page(branch, tree, everything, 1),
             page(branch, tree, dict(everything, melee_mastery=1), 1)]
    out = Image.new("RGBA", ((176 + 6) * 2, (222 + 6) * 2), (30, 30, 34, 255))
    for i, p in enumerate(pages):
        out.alpha_composite(p, (3 + (i % 2) * 182, 3 + (i // 2) * 228))
    target = sys.argv[1] if len(sys.argv) > 1 else "/tmp/skills-tree-preview.png"
    out.resize((out.width * 3, out.height * 3), Image.NEAREST).save(target)
    print(target)


if __name__ == "__main__":
    main()

"""
Writes the pack's item definitions, the skill tree's looks (tree/skilltree_art.py), and files.txt, the index DndPack
ships from. Run after items/build.py:

    python3 pack.py
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "tree"))
sys.path.insert(0, os.path.join(HERE, "items"))
import skilltree_art  # noqa: E402
from models import POTIONS, SCROLL_SEALS  # noqa: E402

NS = "rainsadventurers"
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "de", "raindancer", "modules", "skills", "pack")
ASSETS = os.path.join(ROOT, "assets", NS)

MAGIC = list(POTIONS) + ["spell_scroll_" + rarity for rarity in SCROLL_SEALS] + [
    "wand_of_magic_missiles", "ring_of_protection", "amulet_of_health", "boots_of_striding", "bag_of_holding"]


def write(path, data):
    full = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as out:
        json.dump(data, out, indent=1)


def item(path, model):
    write("items/" + path + ".json", {"model": {"type": "minecraft:model", "model": NS + ":item/" + model}})


def main():
    with open(os.path.join(ROOT, "pack.mcmeta"), "w") as out:
        json.dump({"pack": {"description": "Rain's Adventurers — magic items and the skill tree",
                            "min_format": 88, "max_format": 97}}, out, indent=1)
    for name in MAGIC:
        item("magic/" + name, "magic/" + name)
    skilltree_art.paint()
    files = []
    for folder, _, names in os.walk(ROOT):
        for name in names:
            if name == "files.txt":
                continue
            files.append(os.path.relpath(os.path.join(folder, name), ROOT).replace(os.sep, "/"))
    files.sort()
    with open(os.path.join(ROOT, "files.txt"), "w") as out:
        out.write("\n".join(files) + "\n")
    print(len(files), "files in the pack")
    # The parchment pages and their font live under textures/font and font/, written by gui/parchment.py.


if __name__ == "__main__":
    main()

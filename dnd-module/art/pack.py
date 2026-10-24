"""
Writes the pack's item definitions and flat icon models, and files.txt, the index DndPack ships from.
Run after icons/render.py and items/build.py:

    python3 pack.py
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "icons"))
sys.path.insert(0, os.path.join(HERE, "items"))
from recipes import RECIPES  # noqa: E402
from models import POTIONS, SCROLL_SEALS  # noqa: E402

NS = "rainsadventurers"
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "de", "raindancer", "modules", "dnd", "pack")
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
        json.dump({"pack": {"description": "Rain's Adventurers — spells, classes and magic items",
                            "min_format": 88, "max_format": 97}}, out, indent=1)
    for path in RECIPES:
        write("models/item/" + path + ".json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + path}})
        item(path, path)
    for name in MAGIC:
        item("magic/" + name, "magic/" + name)
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
    # The character sheet's background and its font live under textures/font and font/, written by gui/sheet.py.


if __name__ == "__main__":
    main()

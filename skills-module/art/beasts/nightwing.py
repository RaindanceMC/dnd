"""
The nightwing: a dark blue dragon with crimson wings from Loy's Goodies (models/generic-model/creatures/
211029_dragon.bbmodel, https://github.com/SL0ANE/Loy-s-Goodies, CC0 1.0 — free to use, credited anyway).

    python3 nightwing.py path/to/211029_dragon.bbmodel     # writes dragon_nightwing.bbmodel next to this script

Made fit for BetterModel: its hind legs were named b_leg / b_leg2, and BetterModel takes a b_ bone for a hitbox
(it would not be drawn); the animations get BetterModel's names (static -> idle and walk, flying -> idle_fly and
walk_fly).
"""
import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RENAME = {"b_leg": "hindleg_l", "b_leg2": "hindleg_r", "fleg": "foreleg_l", "fleg2": "foreleg_r"}
ANIMATIONS = {"static": ("idle", "walk"), "flying": ("idle_fly", "walk_fly")}
TAGS = ("p_", "sp_", "b_", "ob_", "h_", "hi_", "glow_", "tag_", "mtag_", "ptag_", "li_", "ri_")


def convert(model):
    model = copy.deepcopy(model)
    renamed = {}

    def walk(nodes):
        for node in nodes:
            if isinstance(node, dict):
                name = RENAME.get(node["name"], node["name"])
                if name.startswith(TAGS) and name not in RENAME.values():
                    raise ValueError(f"{name} would be read as a BetterModel tag")
                renamed[node["uuid"]] = name
                node["name"] = name
                walk(node.get("children", []))
    walk(model["outliner"])
    for group in model.get("groups", []):
        group["name"] = renamed.get(group["uuid"], group["name"])
    out = []
    for animation in model.get("animations", []):
        for animator in animation.get("animators", {}).values():
            animator["name"] = RENAME.get(animator.get("name"), animator.get("name"))
        for name in ANIMATIONS.get(animation["name"], (animation["name"],)):
            twin = copy.deepcopy(animation)
            twin["name"] = name
            twin.pop("uuid", None)
            out.append(twin)
    model["animations"] = out
    model["name"] = "dragon_nightwing"
    return model


def main(source):
    with open(source) as f:
        model = convert(json.load(f))
    target = os.path.join(HERE, "dragon_nightwing.bbmodel")
    with open(target, "w") as f:
        json.dump(model, f)
    print(target, [a["name"] for a in model["animations"]])


if __name__ == "__main__":
    main(sys.argv[1])

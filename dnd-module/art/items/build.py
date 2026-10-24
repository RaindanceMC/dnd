"""
Builds every magic item in a running Blockbench (with its MCP plugin), paints its texture, and writes:

  art/items/<model>.bbmodel     Blockbench source — open it to change a model by hand
  art/items/<model>.png         a preview render
  pack .../models/item/magic/   the exported Java item models (variants as parent + texture)
  pack .../textures/item/magic/ the painted atlases, animated where something glows or bubbles

    python3 build.py [model ...]
"""
import base64
import io
import json
import os
import sys

sys.path.insert(0, "/tmp/bbmcp")
import bb  # noqa: E402  a tiny MCP client for Blockbench's endpoint
import paint  # noqa: E402
from models import POTIONS, SCROLL_SEALS, SHAPES  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules", "dnd", "pack",
                      "assets", "rainsadventurers")
NS = "rainsadventurers"
SIDES = ("north", "east", "south", "west", "up", "down")

HELD = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -25, 0], "translation": [1.5, 2.2, 0], "scale": [0.42, 0.42, 0.42]},
    "firstperson_lefthand": {"rotation": [0, 25, 0], "translation": [1.5, 2.2, 0], "scale": [0.42, 0.42, 0.42]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [22, -32, 0], "translation": [0, 0, 0], "scale": [1.05, 1.05, 1.05]},
    "head": {"translation": [0, 13, 0]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]},
}
WIELDED = dict(HELD)
WIELDED.update({
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1, 1, 1]},
})
DISPLAY = {"wand_of_magic_missiles": WIELDED}


def eval_js(code):
    for result in bb.call("tools/call", {"name": "risky_eval", "arguments": {"code": code}}):
        for part in result.get("result", {}).get("content", []):
            if part.get("type") == "text":
                if result.get("result", {}).get("isError"):
                    raise RuntimeError(part["text"])
                return json.loads(part["text"])
        if "error" in result:
            raise RuntimeError(result["error"])
    raise RuntimeError("no answer from Blockbench")


def data_url(image):
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    # risky_eval refuses any "//", and base64 can contain one: '/' travels as '_' and is put back in Blockbench.
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode().replace("/", "_")


def faces_of(parts):
    faces = []
    for part in parts:
        dx, dy, dz = (part["to"][i] - part["from"][i] for i in range(3))
        size = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz),
                "down": (dx, dz)}
        for side in SIDES:
            w, h = size[side]
            faces.append(paint.Face(part["sides"].get(side, part["material"]),
                                    max(1, round(w * paint.DENSITY)), max(1, round(h * paint.DENSITY)), side))
    return faces


BUILD = """
(() => {
  const spec = SPEC;
  newProject(Formats.java_block);
  Project.name = spec.name;
  Project.texture_width = spec.size;
  Project.texture_height = spec.size;
  const tex = new Texture({name: spec.name}).fromDataURL(spec.texture.split('_').join('/')).add(false);
  Undo.initEdit({elements: [], outliner: true, textures: [tex]});
  const made = [];
  for (const e of spec.elements) {
    const faces = {};
    for (const side of ['north', 'east', 'south', 'west', 'up', 'down']) {
      faces[side] = {uv: e.uv[side], texture: tex.uuid};
    }
    made.push(new Cube({name: e.name, from: e.from, to: e.to, rotation: e.rotation, origin: e.origin,
      faces: faces}).init());
  }
  Undo.finishEdit('Build ' + spec.name, {elements: made, outliner: true, textures: [tex]});
  for (const [slot, data] of Object.entries(spec.display)) {
    if (!Project.display_settings[slot]) {
      Project.display_settings[slot] = new DisplaySlot(slot);
    }
    Project.display_settings[slot].extend(data);
  }
  Canvas.updateAll();
  const model = Codecs.java_block.compile();
  const project = Codecs.project.compile();
  return {model: typeof model === 'string' ? JSON.parse(model) : model,
          project: typeof project === 'string' ? project : JSON.stringify(project)};
})()
"""


def build(model_name, parts, colours=None):
    faces = faces_of(parts)
    if paint.animates(faces):
        strip, first, spots, size = paint.frames(faces, colours=colours)
    else:
        first, spots, size = paint.paint(faces, colours=colours)
        strip = None
    elements = []
    index = 0
    for part in parts:
        uv = {}
        for side in SIDES:
            face = faces[index]
            x, y = spots[index]
            uv[side] = [x, y, x + face.w, y + face.h]
            index += 1
        rotation = [0, 0, 0]
        origin = [8, 8, 8]
        if part["rotation"]:
            rotation["xyz".index(part["rotation"]["axis"])] = part["rotation"]["angle"]
            origin = part["rotation"]["origin"]
        elements.append({"name": part["name"], "from": part["from"], "to": part["to"], "uv": uv,
                         "rotation": rotation, "origin": origin})
    built = eval_js(BUILD.replace("SPEC", json.dumps({"name": model_name, "size": size, "texture": data_url(first),
                                                      "elements": elements,
                                                      "display": DISPLAY.get(model_name, HELD)})))
    open(os.path.join(HERE, model_name + ".bbmodel"), "w").write(built["project"])
    save_texture(model_name, strip or first, strip is not None)
    write_model(model_name, built["model"])
    return faces, size


def save_texture(name, image, animated):
    folder = os.path.join(ASSETS, "textures", "item", "magic")
    os.makedirs(folder, exist_ok=True)
    image.save(os.path.join(folder, name + ".png"))
    meta = os.path.join(folder, name + ".png.mcmeta")
    if animated:
        with open(meta, "w") as out:
            json.dump({"animation": {"frametime": 3, "interpolate": True}}, out, indent=1)
    elif os.path.exists(meta):
        os.remove(meta)


def write_model(name, model, parent=None):
    texture = NS + ":item/magic/" + name
    if parent:
        model = {"parent": NS + ":item/magic/" + parent}
    model["textures"] = {"0": texture, "particle": texture}
    for element in model.get("elements", []):
        for face in element.get("faces", {}).values():
            face["texture"] = "#0"
    path = os.path.join(ASSETS, "models", "item", "magic", name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as out:
        json.dump(model, out, indent=1)


def preview(name, angle=(17, 14, 21)):
    if "preview" not in json.dumps(bb.call("tools/call", {"name": "list_views", "arguments": {}})):
        bb.call("tools/call", {"name": "create_offscreen_view",
                               "arguments": {"id": "preview", "width": 640, "height": 640, "antialias": True}})
    for result in bb.call("tools/call", {"name": "set_camera_angle", "arguments": {
            "view": "preview", "position": list(angle), "target": [0, 6.5, 0], "projection": "perspective",
            "fov": 38}}):
        for part in result.get("result", {}).get("content", []):
            if part.get("type") == "image":
                open(os.path.join(HERE, name + ".png"), "wb").write(base64.b64decode(part["data"]))


def main():
    wanted = set(sys.argv[1:])
    for item, (shape, colours) in POTIONS.items():
        if not wanted or item in wanted:
            build(item, SHAPES[shape], colours)
            preview(item)
            print(item)
    if not wanted or "spell_scroll" in wanted:
        faces, size = build("spell_scroll", SHAPES["spell_scroll"], {"wax": SCROLL_SEALS["common"]})
        preview("spell_scroll", (18, 12, 22))
        for rarity, wax in SCROLL_SEALS.items():
            variant = "spell_scroll_" + rarity
            save_texture(variant, paint.paint(faces, colours={"wax": wax})[0], False)
            write_model(variant, {}, parent="spell_scroll")
        print("spell_scroll (+%d seals)" % len(SCROLL_SEALS))
    for item in ("wand_of_magic_missiles", "ring_of_protection", "amulet_of_health", "boots_of_striding",
                 "bag_of_holding"):
        if not wanted or item in wanted:
            build(item, SHAPES[item])
            preview(item, (14, 13, 20) if item == "wand_of_magic_missiles" else (17, 14, 21))
            print(item)


if __name__ == "__main__":
    main()

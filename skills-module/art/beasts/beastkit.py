"""
The beasts' build kit: bones and cubes written in Python, a texture painted from material tiles, the model built
in Blockbench through its MCP plugin (~/.claude/tools/bbmcp) and photographed from several sides.

Conventions (plans/creature-modelling-guide.md): 1 unit = 1/16 block, the model faces -Z, origin on the ground at
the centre, 2 texture pixels per unit, one 256 x 256 texture, a cube rotated about one axis only by 0/22.5/45.
"""
import base64
import io
import json
import math
import os
import random
import re
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.expanduser("~/.claude/tools/bbmcp"))
import bb  # noqa: E402

DENSITY = 2
SIZE = 512
TILE = 64
SIDES = ("north", "east", "south", "west", "up", "down")


# ------------------------------------------------------------------------------------------------ the rig

class Bone:
    def __init__(self, name, pivot, parent=None, rotation=(0, 0, 0)):
        self.name, self.pivot, self.rotation = name, list(pivot), list(rotation)
        self.parent, self.children, self.cubes = parent, [], []
        if parent:
            parent.children.append(self)

    def bone(self, name, pivot, rotation=(0, 0, 0)):
        return Bone(name, pivot, self, rotation)

    def cube(self, name, frm, to, material, rot=None, origin=None, faces=None, inflate=0.0, mirror_uv=False,
             paint=None):
        """rot = ('x'|'y'|'z', angle in 0/±22.5/±45); faces = side -> material for sides painted otherwise."""
        if rot and abs(rot[1]) not in (0, 22.5, 45):
            raise ValueError(f"{name}: {rot[1]} degrees is not a native item-model angle")
        c = {"name": name, "from": list(frm), "to": list(to), "material": material, "rot": rot,
             "origin": list(origin) if origin else [(frm[i] + to[i]) / 2 for i in range(3)],
             "faces": faces or {}, "inflate": inflate, "mirror_uv": mirror_uv, "paint": paint or {}}
        self.cubes.append(c)
        return c

    def rounded(self, name, frm, to, material, r=1.0, faces=None):
        """A box with its edges taken off: three overlapping cubes, each inset by r in a different pair of axes."""
        (x1, y1, z1), (x2, y2, z2) = frm, to
        self.cube(name, (x1, y1 + r, z1 + r), (x2, y2 - r, z2 - r), material, faces=faces)
        self.cube(name + "_v", (x1 + r, y1, z1 + r), (x2 - r, y2, z2 - r), material, faces=faces)
        self.cube(name + "_d", (x1 + r, y1 + r, z1), (x2 - r, y2 - r, z2), material, faces=faces)

    def walk(self):
        yield self
        for child in self.children:
            yield from child.walk()


def mirror_x(bone, root, rename=("_r", "_l")):
    """A copy of bone (and its children) on the other side of x = 0, under the same parent."""
    side = re.compile(re.escape(rename[0]) + r"(?=\d*$)")  # only the side suffix: wing_root_r, trouser_r0

    def copy(b, parent):
        name = side.sub(rename[1], b.name)
        r = [b.rotation[0], -b.rotation[1], -b.rotation[2]]
        nb = Bone(name, [-b.pivot[0], b.pivot[1], b.pivot[2]], parent, r)
        for c in b.cubes:
            frm, to = c["from"], c["to"]
            rot = c["rot"]
            if rot and rot[0] in ("y", "z"):
                rot = (rot[0], -rot[1])
            nb.cube(side.sub(rename[1], c["name"]), [-to[0], frm[1], frm[2]], [-frm[0], to[1], to[2]],
                    c["material"], rot, [-c["origin"][0], c["origin"][1], c["origin"][2]],
                    {({"east": "west", "west": "east"}.get(k, k)): v for k, v in c["faces"].items()}, c["inflate"],
                    not c["mirror_uv"], {({"east": "west", "west": "east"}.get(k, k)): v for k, v in c["paint"].items()})
        for child in b.children:
            copy(child, nb)
        return nb
    return copy(bone, bone.parent or root)


# ------------------------------------------------------------------------------------------------ the texture

def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Atlas:
    """Material tiles of TILE x TILE on one SIZE x SIZE texture. A face takes a window of its own pixel size inside its
    material's tile (a different window per cube, so repeats do not show); bigger faces are squeezed into the tile."""

    def __init__(self):
        self.image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        self.tiles = {}
        self.fixed = set()
        self.align = {}
        self.free = [(x, y) for y in range(0, 256, TILE) for x in range(0, 256, TILE)]
        # painted faces go on shelves: right of the tiles first, then below them
        self.shelves = [[256, 0, SIZE, 256], [0, 256, SIZE, SIZE]]
        self.cursor = None
        self.painted = {}

    def tile(self, name, painter, seed=1, fixed=False, align=1):
        """fixed: every face takes the tile's top-left corner — for a painted feature such as an eye.
        align: windows start on multiples of this many pixels across, so painted lanes (feather shafts) line up."""
        if fixed:
            self.fixed.add(name)
        self.align[name] = align
        x, y = self.free.pop(0)
        tile = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
        painter(tile, random.Random(seed))
        self.image.alpha_composite(tile, (x, y))
        self.tiles[name] = (x, y)

    def paint(self, key, w, h, painter):
        """A face of its own: w x h pixels painted by painter(image) and packed on a shelf. The same key twice (a
        mirrored wing) shares one painting."""
        if key in self.painted:
            return self.painted[key]
        for shelf in self.shelves:
            x0, y0, x1, y1 = shelf
            if self.cursor is None or self.cursor[0] is not shelf:
                self.cursor = [shelf, x0, y0, 0]
            _, cx, cy, row = self.cursor
            if cx + w > x1:
                cx, cy, row = x0, cy + row + 1, 0
            if cy + h > y1:
                self.cursor = None
                continue
            image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            painter(image)
            self.image.alpha_composite(image, (cx, cy))
            self.cursor = [shelf, cx + w + 1, cy, max(row, h)]
            self.painted[key] = [cx, cy, cx + w, cy + h]
            return self.painted[key]
        raise RuntimeError("the texture is full")

    def window(self, material, w, h, salt):
        if material == "clear":
            return list(self.paint(("clear",), 2, 2, lambda image: None))
        tx, ty = self.tiles[material]
        w, h = min(w, TILE), min(h, TILE)
        rng = random.Random(hash((material, salt)) & 0xFFFF)
        ox = rng.randint(0, TILE - w) if w < TILE and material not in self.fixed else 0
        a = self.align.get(material, 1)
        ox -= ox % a
        oy = rng.randint(0, TILE - h) if h < TILE and material not in self.fixed else 0
        return [tx + ox, ty + oy, tx + ox + w, ty + oy + h]


def face_size(cube, side):
    dx, dy, dz = (abs(cube["to"][i] - cube["from"][i]) for i in range(3))
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz),
            "down": (dx, dz)}[side]
    return max(1, round(w * DENSITY)), max(1, round(h * DENSITY))


# ------------------------------------------------------------------------------------------------ Blockbench

def eval_js(code):
    for result in bb.call("tools/call", {"name": "risky_eval", "arguments": {"code": code}}):
        for part in result.get("result", {}).get("content", []):
            if part.get("type") == "text":
                if result.get("result", {}).get("isError"):
                    raise RuntimeError(part["text"][:2000])
                return json.loads(part["text"])
        if "error" in result:
            raise RuntimeError(result["error"])
    raise RuntimeError("no answer from Blockbench")


def data_url(image):
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode().replace("/", "_")


BUILD = """
(() => {
  const spec = SPEC;
  newProject(Formats.free);
  Project.name = spec.name;
  Project.box_uv = false;
  Project.texture_width = spec.size;
  Project.texture_height = spec.size;
  const tex = new Texture({name: spec.name + '.png'}).fromDataURL(spec.texture.split('_').join('/')).add(false);
  Undo.initEdit({elements: [], outliner: true, textures: [tex]});
  const made = [];
  const make = (b, parent) => {
    const g = new Group({name: b.name, origin: b.pivot, rotation: b.rotation}).init();
    if (parent) g.addTo(parent);
    for (const c of b.cubes) {
      const faces = {};
      for (const side of ['north', 'east', 'south', 'west', 'up', 'down']) {
        faces[side] = {uv: c.uv[side], texture: tex.uuid};
      }
      const rotation = [0, 0, 0];
      if (c.rot) rotation['xyz'.indexOf(c.rot[0])] = c.rot[1];
      const cube = new Cube({name: c.name, from: c.from, to: c.to, origin: c.origin, rotation: rotation,
        inflate: c.inflate, faces: faces}).addTo(g).init();
      made.push(cube);
    }
    for (const child of b.children) make(child, g);
  };
  make(spec.root, null);
  Undo.finishEdit('Build ' + spec.name, {elements: made, outliner: true, textures: [tex]});
  Canvas.updateAll();
  return {cubes: made.length, groups: Group.all.length};
})()
"""


def spec_of(root, atlas):
    salt = [0]

    def b2d(b):
        cubes = []
        for c in b.cubes:
            salt[0] += 1
            uv = {}
            for side in SIDES:
                w, h = face_size(c, side)
                if side in c["paint"]:
                    key, painter = c["paint"][side]
                    rect = list(atlas.paint(key, w, h, painter))
                else:
                    rect = atlas.window(c["faces"].get(side, c["material"]), w, h, (c["name"], side, salt[0]))
                if c["mirror_uv"] and side in ("north", "south", "up", "down"):
                    rect = [rect[2], rect[1], rect[0], rect[3]]
                uv[side] = rect
            cubes.append({"name": c["name"], "from": c["from"], "to": c["to"], "rot": c["rot"],
                          "origin": c["origin"], "inflate": c["inflate"], "uv": uv})
        return {"name": b.name, "pivot": b.pivot, "rotation": b.rotation, "cubes": cubes,
                "children": [b2d(child) for child in b.children]}
    return b2d(root)


def build(name, root, atlas, out_dir):
    spec = spec_of(root, atlas)
    made = eval_js(BUILD.replace("SPEC", json.dumps({"name": name, "size": SIZE, "texture": data_url(atlas.image),
                                                      "root": spec})))
    atlas.image.save(os.path.join(out_dir, name + "_texture.png"))
    return made


def save_project(path):
    text = eval_js("(() => { const p = Codecs.project.compile(); return typeof p === 'string' ? p : JSON.stringify(p); })()")
    with open(path, "w") as out:
        out.write(text)


def pose(rotations):
    """Turns bones for a picture: {bone name: [x, y, z]}; returns the old rotations to put back."""
    return eval_js("""(() => { const want = %s; const old = {};
      for (const g of Group.all) { if (want[g.name]) { old[g.name] = g.rotation.slice(); g.rotation.splice(0, 3, ...want[g.name]); } }
      Canvas.updateAll(); return old; })()""" % json.dumps(rotations))


def shots(path, angles, target, distance, size=(1000, 760)):
    """Photographs from (yaw, pitch) degrees around target, composed into one sheet."""
    bb.call("tools/call", {"name": "create_offscreen_view", "arguments": {"id": "beast", "width": size[0], "height": size[1]}})
    images = []
    for yaw, pitch in angles:
        y, p = math.radians(yaw), math.radians(pitch)
        pos = [target[0] + distance * math.sin(y) * math.cos(p), target[1] + distance * math.sin(p),
               target[2] - distance * math.cos(y) * math.cos(p)]
        bb.call("tools/call", {"name": "set_camera_angle", "arguments": {"view": "beast", "position": pos,
                                                                          "target": target, "projection": "perspective", "fov": 30}})
        for m in bb.call("tools/call", {"name": "capture_screenshot", "arguments": {"view": "beast", "format": "png"}}):
            for content in m.get("result", {}).get("content", []):
                if content.get("type") == "image":
                    images.append(Image.open(io.BytesIO(base64.b64decode(content["data"]))).convert("RGBA"))
    cols = 2
    rows = (len(images) + 1) // 2
    sheet = Image.new("RGB", (size[0] * cols, size[1] * rows), (214, 222, 232))
    for i, im in enumerate(images):
        bg = Image.new("RGBA", im.size, (214, 222, 232, 255))
        bg.alpha_composite(im)
        sheet.paste(bg.convert("RGB"), ((i % cols) * size[0], (i // cols) * size[1]))
    sheet.save(path)
    return path


# ------------------------------------------------------------------------------------------------ animations

def mirrored(tracks, rename=("_r", "_l")):
    """Adds the other side's tracks to {bone: {channel: [(time, [x, y, z])]}}: a right bone's rotation mirrors as
    (x, -y, -z), its position as (-x, y, z), its scale as it is. Bones already given for both sides stay as given."""
    side = re.compile(re.escape(rename[0]) + r"(?=\d*$)")
    out = dict(tracks)
    for bone, channels in tracks.items():
        twin = side.sub(rename[1], bone)
        if twin == bone or twin in tracks:
            continue
        flip = {"rotation": lambda v: [v[0], -v[1], -v[2]], "position": lambda v: [-v[0], v[1], v[2]],
                "scale": lambda v: list(v)}
        out[twin] = {ch: [(t, flip[ch](v)) for t, v in keys] for ch, keys in channels.items()}
    return out


def animations(specs):
    """Replaces the project's animations by specs: [(name, length, loop, {bone: {channel: [(time, [x, y, z])]}})].
    Times snap to BetterModel's 20 tps; keys interpolate smoothly (catmullrom), a looping track should end where it
    starts."""
    payload = [{"name": n, "length": length, "loop": "loop" if loop else "once",
                "tracks": {b: {ch: [[round(t * 20) / 20, v] for t, v in keys] for ch, keys in chans.items()}
                           for b, chans in tracks.items()}} for n, length, loop, tracks in specs]
    return eval_js("""(() => { const specs = %s;
      for (const a of Animation.all.slice()) a.remove(false);
      const missing = [];
      for (const s of specs) {
        const a = new Animation({name: s.name, length: s.length, loop: s.loop, snapping: 20}).add(false);
        for (const [bone, chans] of Object.entries(s.tracks)) {
          const g = Group.all.find(x => x.name === bone);
          if (!g) { missing.push(bone); continue; }
          const ba = a.getBoneAnimator(g);
          for (const [ch, keys] of Object.entries(chans))
            for (const [t, v] of keys)
              ba.addKeyframe({channel: ch, time: t, interpolation: 'catmullrom',
                              data_points: [{x: v[0], y: v[1], z: v[2]}]});
        }
      }
      return {animations: Animation.all.length, missing: missing}; })()""" % json.dumps(payload))


def frame(name, time):
    """Shows animation name at time in the viewport (for shots)."""
    return eval_js("""(() => { const a = Animation.all.find(x => x.name === %s); a.select();
      Timeline.setTime(%s); Animator.preview(); return a.name; })()""" % (json.dumps(name), json.dumps(time)))

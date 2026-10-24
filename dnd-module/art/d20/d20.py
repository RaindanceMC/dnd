"""
The character sheet's d20: a true icosahedron built as a mesh in Blockbench, every face carrying its number (opposite
faces add up to 21, as on a real die), rendered with the 20 towards the viewer and brought down to a crisp 32px
icon. Java item models only know boxes, so the die ships as that icon.

Blockbench must be running with its MCP plugin on 127.0.0.1:3000 (client in /tmp/bbmcp/bb.py).

    python3 d20.py
"""
import base64
import io
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, "/tmp/bbmcp")
import bb  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "de", "raindancer", "modules", "dnd", "pack",
                   "assets", "rainsadventurers", "textures", "item", "ui", "d20.png")
PHI = (1 + 5 ** 0.5) / 2
CELL = 96
COLS, ROWS = 5, 4
RADIUS = 5.0
CENTRE = (0.0, 8.0, 0.0)

RUBY = (148, 18, 40)
RUBY_DARK = (70, 6, 22)
RUBY_LIGHT = (214, 58, 78)
GOLD = (242, 196, 92)
GOLD_DARK = (120, 78, 20)


def icosahedron():
    raw = [(0, s1, s2 * PHI) for s1 in (-1, 1) for s2 in (-1, 1)] + \
          [(s1, s2 * PHI, 0) for s1 in (-1, 1) for s2 in (-1, 1)] + \
          [(s1 * PHI, 0, s2) for s1 in (-1, 1) for s2 in (-1, 1)]
    norm = math.sqrt(1 + PHI * PHI)
    verts = [tuple(c / norm * RADIUS for c in v) for v in raw]
    edge = min(dist(verts[0], v) for v in verts[1:])
    faces = []
    n = len(verts)
    for a in range(n):
        for b in range(a + 1, n):
            for c in range(b + 1, n):
                if all(abs(dist(verts[i], verts[j]) - edge) < 1e-6 for i, j in ((a, b), (b, c), (a, c))):
                    faces.append(outward(verts, (a, b, c)))
    return verts, faces


def dist(p, q):
    return math.sqrt(sum((p[i] - q[i]) ** 2 for i in range(3)))


def sub(p, q):
    return tuple(p[i] - q[i] for i in range(3))


def cross(p, q):
    return (p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0])


def dot(p, q):
    return sum(p[i] * q[i] for i in range(3))


def centroid(verts, face):
    return tuple(sum(verts[i][k] for i in face) / 3 for k in range(3))


def outward(verts, face):
    a, b, c = (verts[i] for i in face)
    if dot(cross(sub(b, a), sub(c, a)), centroid(verts, face)) < 0:
        return (face[0], face[2], face[1])
    return face


def numbering(verts, faces, front):
    """20 on the front face, 1 opposite it; every other pair adds up to 21, high numbers near the front."""
    numbers = {}
    centres = [centroid(verts, f) for f in faces]
    opposite = {}
    for i, c in enumerate(centres):
        opposite[i] = min(range(len(faces)), key=lambda j: dist(centres[j], tuple(-x for x in c)))
    order = sorted(range(len(faces)), key=lambda i: -dot(centres[i], centres[front]))
    value = 20
    for i in order:
        if i in numbers:
            continue
        numbers[i] = value
        numbers[opposite[i]] = 21 - value
        value -= 1
    return numbers


def atlas(numbers):
    """One cell per face: the ruby triangle, a bevelled gold rim, its number in the middle."""
    img = Image.new("RGBA", (COLS * CELL, ROWS * CELL), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("DejaVuSerif-Bold.ttf", 30)
    except OSError:
        font = ImageFont.load_default()
    corners = {}
    for i in range(20):
        cx, cy = (i % COLS) * CELL, (i // COLS) * CELL
        tri = [(cx + CELL / 2, cy + 6), (cx + 6, cy + CELL - 10), (cx + CELL - 6, cy + CELL - 10)]
        corners[i] = tri
        # facet: lighter towards the top, as cut stone
        for y in range(int(tri[0][1]), int(tri[1][1]) + 1):
            t = (y - tri[0][1]) / (tri[1][1] - tri[0][1])
            half = t * (CELL / 2 - 6)
            colour = tuple(int(RUBY_LIGHT[k] + (RUBY[k] - RUBY_LIGHT[k]) * min(1, t * 1.3)) for k in range(3))
            draw.line([(cx + CELL / 2 - half, y), (cx + CELL / 2 + half, y)], fill=colour + (255,))
        draw.polygon(tri, outline=GOLD + (255,), width=3)
        inner = [(x * 0.86 + (cx + CELL / 2) * 0.14, y * 0.86 + (cy + CELL * 0.62) * 0.14) for x, y in tri]
        draw.polygon(inner, outline=RUBY_DARK + (255,), width=1)
        text = str(numbers[i]) + ("." if numbers[i] in (6, 9) else "")
        box = draw.textbbox((0, 0), text, font=font)
        tx = cx + CELL / 2 - (box[2] - box[0]) / 2 - box[0]
        ty = cy + CELL * 0.6 - (box[3] - box[1]) / 2 - box[1]
        draw.text((tx + 1, ty + 1), text, font=font, fill=GOLD_DARK + (255,))
        draw.text((tx, ty), text, font=font, fill=GOLD + (255,))
    return img, corners


def eval_js(code):
    for result in bb.call("tools/call", {"name": "risky_eval", "arguments": {"code": code}}):
        for part in result.get("result", {}).get("content", []):
            if part.get("type") == "text":
                if result.get("result", {}).get("isError"):
                    raise RuntimeError(part["text"])
                return json.loads(part["text"])
    raise RuntimeError("no answer from Blockbench")


BUILD = """
(() => {
  const spec = SPEC;
  newProject(Formats.free);
  Project.name = 'd20';
  Project.texture_width = spec.w;
  Project.texture_height = spec.h;
  const tex = new Texture({name: 'd20'}).fromDataURL(spec.texture.split('_').join('/')).add(false);
  const mesh = new Mesh({name: 'd20', vertices: {}});
  const keys = mesh.addVertices(...spec.vertices);
  for (const f of spec.faces) {
    const vk = f.v.map(i => keys[i]);
    const uv = {};
    vk.forEach((k, n) => { uv[k] = f.uv[n]; });
    mesh.addFaces(new MeshFace(mesh, {vertices: vk, uv: uv, texture: tex.uuid}));
  }
  mesh.init();
  Canvas.updateAll();
  return {faces: Object.keys(mesh.faces).length, vertices: Object.keys(mesh.vertices).length};
})()
"""


def data_url(image):
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode().replace("/", "_")


def render(position, target, name, size=640):
    if "d20view" not in json.dumps(bb.call("tools/call", {"name": "list_views", "arguments": {}})):
        bb.call("tools/call", {"name": "create_offscreen_view",
                               "arguments": {"id": "d20view", "width": size, "height": size, "antialias": True}})
    for result in bb.call("tools/call", {"name": "set_camera_angle", "arguments": {
            "view": "d20view", "position": list(position), "target": list(target), "projection": "perspective",
            "fov": 30}}):
        for part in result.get("result", {}).get("content", []):
            if part.get("type") == "image":
                image = Image.open(io.BytesIO(base64.b64decode(part["data"]))).convert("RGBA")
                image.save(os.path.join(HERE, name))
                return image
    raise RuntimeError("no render")


def main():
    verts, faces = icosahedron()
    assert len(faces) == 20, len(faces)
    # the face that looks towards the camera: the one most along (0.35, 0.3, 1)
    view = (0.35, 0.32, 1.0)
    front = max(range(20), key=lambda i: dot(centroid(verts, faces[i]), view))
    numbers = numbering(verts, faces, front)
    texture, corners = atlas(numbers)
    texture.save(os.path.join(HERE, "d20-atlas.png"))
    spec_faces = []
    for i, face in enumerate(faces):
        # the triangle's apex is the vertex nearest "up" as the face is seen, so numbers stand upright
        cen = centroid(verts, face)
        up = (0, 1, 0) if abs(dot(cen, (0, 1, 0))) < 0.9 * RADIUS else (0, 0, -1)
        apex = max(face, key=lambda v: dot(sub(verts[v], cen), up))
        rest = [v for v in face if v != apex]
        normal = cross(sub(verts[face[1]], verts[face[0]]), sub(verts[face[2]], verts[face[0]]))
        right = cross(up, normal)
        left_v, right_v = sorted(rest, key=lambda v: dot(sub(verts[v], cen), right))
        tri = corners[i]
        uv_of = {apex: tri[0], left_v: tri[1], right_v: tri[2]}
        spec_faces.append({"v": list(face), "uv": [list(uv_of[v]) for v in face]})
    vertices = [[v[0] + CENTRE[0], v[1] + CENTRE[1], v[2] + CENTRE[2]] for v in verts]
    print(eval_js(BUILD.replace("SPEC", json.dumps({"w": texture.width, "h": texture.height,
                                                    "texture": data_url(texture), "vertices": vertices,
                                                    "faces": spec_faces}))))
    d = 26
    norm = math.sqrt(dot(view, view))
    camera = tuple(CENTRE[k] + view[k] / norm * d for k in range(3))
    render(camera, CENTRE, "d20-render.png")
    project = eval_js("(() => { const p = Codecs.project.compile(); return {p: typeof p === 'string' ? p : JSON.stringify(p)}; })()")
    open(os.path.join(HERE, "d20.bbmodel"), "w").write(project["p"])
    icon(verts, faces, front, view)


DIGITS = {"2": ["###", "..#", "###", "#..", "###"], "0": ["###", "#.#", "#.#", "#.#", "###"]}


def camera(view):
    norm = math.sqrt(dot(view, view))
    return tuple(CENTRE[k] + view[k] / norm * 26 for k in range(3))


def projector(position, target, size=640, fov=30):
    """Blockbench's perspective camera, so the icon's facets sit exactly where the render has them."""
    def unit(v):
        length = math.sqrt(dot(v, v))
        return tuple(x / length for x in v)
    forward = unit(sub(target, position))
    right = unit(cross(forward, (0, 1, 0)))
    up = cross(right, forward)
    t = math.tan(math.radians(fov) / 2)

    def project(p):
        d = sub(p, position)
        x, y, z = dot(d, right), dot(d, up), dot(d, forward)
        return (x / (z * t) + 1) / 2 * size, (1 - y / (z * t)) / 2 * size, z
    return project


def icon(verts, faces, front, view):
    """
    The 32px icon, as pixel art: every visible facet in the colour Blockbench lit it with (sampled from a render
    without numbers), gold edges a pixel wide, the 20 on the front face in crisp digits, a dark outline.
    """
    position = camera(view)
    plain = Image.new("RGBA", (COLS * CELL, ROWS * CELL), (0, 0, 0, 0))
    pd = ImageDraw.Draw(plain)
    for i in range(20):
        cx, cy = (i % COLS) * CELL, (i // COLS) * CELL
        pd.rectangle((cx, cy, cx + CELL, cy + CELL), fill=RUBY + (255,))
    eval_js(RETEXTURE.replace("PLAIN_TEXTURE", json.dumps(data_url(plain))))
    lit = render(position, CENTRE, "d20-plain.png").convert("RGB")
    project = projector(position, CENTRE)
    world = [tuple(v[k] + CENTRE[k] for k in range(3)) for v in verts]
    screen = [project(p) for p in world]
    visible = [i for i, f in enumerate(faces)
               if dot(cross(sub(world[f[1]], world[f[0]]), sub(world[f[2]], world[f[0]])),
                      sub(position, centroid(world, f))) > 0]
    xs = [screen[v][0] for i in visible for v in faces[i]]
    ys = [screen[v][1] for i in visible for v in faces[i]]
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    scale = 29.0 / span
    ox = (32 - (max(xs) - min(xs)) * scale) / 2 - min(xs) * scale
    oy = (32 - (max(ys) - min(ys)) * scale) / 2 - min(ys) * scale

    def to_icon(i):
        return screen[i][0] * scale + ox, screen[i][1] * scale + oy

    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = out.load()
    owner = {}
    # Blockbench's light per facet, ranked: the brightest facet gets the brightest tone, so the cut reads
    light = {i: sum(lit.getpixel(tuple(int(sum(screen[v][k] for v in faces[i]) / 3) for k in (0, 1))))
             for i in visible}
    ranked = sorted(visible, key=lambda i: light[i])
    ramp = [(62, 5, 20), (96, 10, 30), (132, 16, 38), (168, 30, 52), (204, 58, 76), (232, 104, 116)]
    shade = {i: ramp[min(len(ramp) - 1, n * len(ramp) // len(ranked))] for n, i in enumerate(ranked)}
    for i in sorted(visible, key=lambda f: -sum(screen[v][2] for v in faces[f])):
        a, b, c = (to_icon(v) for v in faces[i])
        for y in range(32):
            for x in range(32):
                if inside((x + 0.5, y + 0.5), a, b, c):
                    owner[(x, y)] = i
                    px[x, y] = shade[i] + (255,)
    # gold edges: every edge of a visible facet, as a line, inside the silhouette
    for i in visible:
        f = faces[i]
        for a, b in ((f[0], f[1]), (f[1], f[2]), (f[2], f[0])):
            for x, y in line(to_icon(a), to_icon(b)):
                if (x, y) in owner:
                    px[x, y] = GOLD + (255,)
    # the 20, centred on the front face, with a shadow
    fx = sum(to_icon(v)[0] for v in faces[front]) / 3
    fy = sum(to_icon(v)[1] for v in faces[front]) / 3
    x0, y0 = int(round(fx - 3.5)), int(round(fy - 2))
    for n, digit in enumerate("20"):
        for dy, row in enumerate(DIGITS[digit]):
            for dx, c in enumerate(row):
                if c == "#":
                    sx, sy = x0 + n * 4 + dx, y0 + dy
                    if (sx + 1, sy + 1) in owner:
                        px[sx + 1, sy + 1] = RUBY_DARK + (255,)
    for n, digit in enumerate("20"):
        for dy, row in enumerate(DIGITS[digit]):
            for dx, c in enumerate(row):
                if c == "#":
                    px[x0 + n * 4 + dx, y0 + dy] = (255, 226, 130, 255)
    # the dark outline round the die
    solid = set(owner)
    for y in range(32):
        for x in range(32):
            if (x, y) not in solid and any((x + dx, y + dy) in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (40, 4, 14, 255)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    out.resize((256, 256), Image.NEAREST).save(os.path.join(HERE, "d20-icon.png"))
    print("icon ->", OUT)


def line(p, q):
    steps = int(max(abs(q[0] - p[0]), abs(q[1] - p[1])) * 2) + 1
    seen = []
    for n in range(steps + 1):
        t = n / steps
        cell = (int(p[0] + (q[0] - p[0]) * t), int(p[1] + (q[1] - p[1]) * t))
        if cell not in seen:
            seen.append(cell)
    return seen


def inside(p, a, b, c):
    def side(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1, d2, d3 = side(p, a, b), side(p, b, c), side(p, c, a)
    return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))


def tone(rgb):
    """Blockbench's light, snapped to a five-step ruby ramp so the facets read as cut stone, not noise."""
    ramp = [(58, 4, 18), (98, 10, 30), (140, 18, 40), (184, 36, 58), (226, 82, 98)]
    light = sum(rgb) / (3 * 255)
    lo, hi = 0.12, 0.62
    k = max(0, min(4, int(round((light - lo) / (hi - lo) * 4))))
    return ramp[k]


RETEXTURE = """
(() => {
  const tex = Texture.all[0];
  tex.fromDataURL(PLAIN_TEXTURE.split('_').join('/'));
  Canvas.updateAll();
  return {ok: true};
})()
"""


if __name__ == "__main__":
    main()

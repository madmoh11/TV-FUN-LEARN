#!/usr/bin/env python3
"""Builds the airliner the flight simulator flies: app/src/main/assets/fly/plane.glb.

The shape is made here from numbers, so it belongs to this project. It is about the size of a narrow-body jet:
38 m long with a 35 m wingspan. glTF axes: +Z is the nose, +Y is up, +X is the left wing."""
import json, math, struct, sys

mats = {"white": ([0.93, 0.94, 0.96, 1], 0.15, 0.45), "blue": ([0.05, 0.22, 0.52, 1], 0.2, 0.4), "grey": ([0.62, 0.64, 0.68, 1], 0.6, 0.35),
        "dark": ([0.03, 0.04, 0.06, 1], 0.3, 0.2), "red": ([0.85, 0.08, 0.08, 1], 0.1, 0.5), "green": ([0.1, 0.8, 0.2, 1], 0.1, 0.5)}
soup = {k: ([], []) for k in mats}          # material -> (positions, normals), three vertices to a triangle

def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def unit(a):
    l = math.sqrt(a[0] ** 2 + a[1] ** 2 + a[2] ** 2) or 1.0
    return (a[0] / l, a[1] / l, a[2] / l)

def tri(m, a, b, c, na=None, nb=None, nc=None):
    n = unit(cross(sub(b, a), sub(c, a)))
    soup[m][0].extend([a, b, c]); soup[m][1].extend([na or n, nb or n, nc or n])

def quad(m, a, b, c, d, n=None):
    if n: tri(m, a, b, c, n[0], n[1], n[2]); tri(m, a, c, d, n[0], n[2], n[3])
    else: tri(m, a, b, c); tri(m, a, c, d)

def lathe(m, stations, seg=28, x0=0.0, smooth=True):
    """A body of rings along Z. stations: (z, radius, y of the centre). Faces look outward."""
    rings = []
    for z, r, cy in stations:
        rings.append([((x0 + r * math.cos(2 * math.pi * i / seg), cy + r * math.sin(2 * math.pi * i / seg), z),
                       (math.cos(2 * math.pi * i / seg), math.sin(2 * math.pi * i / seg), 0.0)) for i in range(seg)])
    for a, b in zip(rings, rings[1:]):
        for i in range(seg):
            j = (i + 1) % seg
            p = [a[i], a[j], b[j], b[i]]
            quad(m, p[0][0], p[1][0], p[2][0], p[3][0], [q[1] for q in p] if smooth else None)

def surface(m, root, tip, thick_root, thick_tip):
    """A wing panel between two chords. root and tip: (leading point, trailing point). A flat six-sided section."""
    def section(lead, trail, t):
        mid = tuple(lead[i] + (trail[i] - lead[i]) * 0.3 for i in range(3)); aft = tuple(lead[i] + (trail[i] - lead[i]) * 0.75 for i in range(3))
        return [lead, (mid[0], mid[1] + t, mid[2]), (aft[0], aft[1] + t * 0.6, aft[2]), trail, (aft[0], aft[1] - t * 0.5, aft[2]), (mid[0], mid[1] - t * 0.8, mid[2])]
    a, b = section(root[0], root[1], thick_root), section(tip[0], tip[1], thick_tip)
    flip = tip[0][0] < root[0][0]            # the right-hand panel is a mirror image, so its faces are wound the other way
    for i in range(6):
        j = (i + 1) % 6
        if flip: quad(m, a[j], a[i], b[i], b[j])
        else: quad(m, a[i], a[j], b[j], b[i])
    for i in range(1, 5):
        if flip: tri(m, b[0], b[i + 1], b[i])
        else: tri(m, b[0], b[i], b[i + 1])

R = 1.95
# the fuselage: a rounded nose, a long tube, and a tail that tapers upward
fus = [(19.0, 0.05, -0.25), (18.6, 0.55, -0.2), (17.8, 1.05, -0.12), (16.6, 1.5, -0.05), (15.0, 1.8, 0.0), (13.0, R, 0.0), (-7.0, R, 0.0),
       (-10.0, 1.8, 0.12), (-13.0, 1.4, 0.4), (-16.0, 0.9, 0.8), (-18.2, 0.45, 1.15), (-19.0, 0.12, 1.3)]
lathe("white", fus[::-1])
# a line of cabin windows down each side, and the cockpit glass
for side in (1, -1):
    for z in [12.2 - 0.95 * i for i in range(20)]:
        x = side * (R + 0.012); quad("dark", *([(x, 0.75, z), (x, 0.35, z), (x, 0.35, z - 0.42), (x, 0.75, z - 0.42)][::side]))
for i in range(-3, 3):
    a0, a1 = math.radians(90 + i * 22), math.radians(90 + (i + 1) * 22)
    def pt(z, r, cy, a, lift): return (r * 1.012 * math.cos(a) * 1.0, cy + r * 1.012 * math.sin(a) * lift, z)
    quad("dark", pt(17.75, 1.07, -0.12, a1, 0.98), pt(17.75, 1.07, -0.12, a0, 0.98), pt(16.75, 1.47, -0.05, a0, 0.9), pt(16.75, 1.47, -0.05, a1, 0.9))

for s in (1, -1):
    # main wing with a winglet, swept back and rising toward the tip
    root = ((s * 1.6, -1.0, 4.5), (s * 1.6, -1.0, -3.6)); tip = ((s * 17.0, 0.7, -5.2), (s * 17.0, 0.7, -7.4))
    surface("white", root, tip, 0.5, 0.14)
    surface("blue", ((s * 17.0, 0.7, -5.4), (s * 17.0, 0.7, -7.4)), ((s * 17.5, 2.9, -6.9), (s * 17.5, 2.9, -7.9)), 0.1, 0.05)
    # the tailplane
    surface("white", ((s * 0.9, 1.0, -14.0), (s * 0.9, 1.0, -17.4)), ((s * 6.6, 1.5, -17.2), (s * 6.6, 1.5, -18.6)), 0.22, 0.08)
    # an engine under the wing: a pod with a dark intake and a pylon
    ex, ey = s * 6.2, -2.25
    lathe("grey", [(-0.6, 0.55, ey), (0.6, 1.0, ey), (2.6, 1.12, ey), (4.3, 1.08, ey), (4.75, 0.98, ey)], 20, ex)
    lathe("dark", [(4.75, 0.98, ey), (4.3, 0.82, ey), (4.25, 0.05, ey)], 20, ex)          # the intake, looking in from the front
    lathe("dark", [(-0.62, 0.05, ey), (-0.6, 0.55, ey)], 20, ex)                             # the exhaust
    quad("grey", (ex + 0.14, ey + 1.0, 3.4), (ex + 0.14, -0.55, 2.6), (ex + 0.14, -0.55, -0.4), (ex + 0.14, ey + 1.0, 0.3))
    quad("grey", (ex - 0.14, ey + 1.0, 0.3), (ex - 0.14, -0.55, -0.4), (ex - 0.14, -0.55, 2.6), (ex - 0.14, ey + 1.0, 3.4))
    # navigation lights: red on the left wing tip, green on the right
    lathe("red" if s > 0 else "green", [(-7.0, 0.02, 0.7), (-6.4, 0.16, 0.7), (-5.6, 0.02, 0.7)], 8, s * 17.05)

# the fin, in the livery colour: two thin halves so both faces are lit
for s in (1, -1):
    a = [(0.0, 1.2, -11.0), (0.0, 8.2, -17.6), (0.0, 8.2, -19.4), (0.0, 1.3, -18.2)]
    t = 0.2 * s
    b = [(t, p[1], p[2] - 0.5 if i in (0, 1) else p[2] + 0.25) for i, p in enumerate(a)]
    mid = [(t, 1.25, -14.0), (t, 8.2, -18.3)]
    pts = [a[0], mid[0], mid[1], a[1]]; quad("blue", *(pts if s > 0 else pts[::-1]))
    pts = [mid[0], a[3], a[2], mid[1]]; quad("blue", *(pts if s > 0 else pts[::-1]))

out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/fly/plane.glb"
buf = bytearray(); views = []; accs = []; meshes = []; materials = []
for name, (pos, nor) in soup.items():
    if not pos: continue
    col, metal, rough = mats[name]
    materials.append({"name": name, "doubleSided": name in ("blue", "grey", "dark"), "pbrMetallicRoughness": {"baseColorFactor": col, "metallicFactor": metal, "roughnessFactor": rough}})
    ids = []
    for data in (pos, nor):
        raw = b"".join(struct.pack("<3f", *v) for v in data)
        views.append({"buffer": 0, "byteOffset": len(buf), "byteLength": len(raw), "target": 34962}); buf += raw
        acc = {"bufferView": len(views) - 1, "componentType": 5126, "count": len(data), "type": "VEC3"}
        if data is pos: acc["min"] = [min(v[i] for v in data) for i in range(3)]; acc["max"] = [max(v[i] for v in data) for i in range(3)]
        accs.append(acc); ids.append(len(accs) - 1)
    meshes.append({"attributes": {"POSITION": ids[0], "NORMAL": ids[1]}, "material": len(materials) - 1})
gltf = {"asset": {"version": "2.0", "generator": "Lamma tools/make_plane.py"}, "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"mesh": 0, "name": "airliner"}],
        "meshes": [{"primitives": meshes}], "materials": materials, "accessors": accs, "bufferViews": views, "buffers": [{"byteLength": len(buf)}]}
js = json.dumps(gltf, separators=(",", ":")).encode(); js += b" " * (-len(js) % 4); buf += b"\0" * (-len(buf) % 4)
with open(out, "wb") as f:
    f.write(struct.pack("<4sII", b"glTF", 2, 12 + 8 + len(js) + 8 + len(buf))); f.write(struct.pack("<I4s", len(js), b"JSON")); f.write(js); f.write(struct.pack("<I4s", len(buf), b"BIN\0")); f.write(buf)
print(out, sum(len(p) for p, _ in soup.values()) // 3, "triangles", 12 + 16 + len(js) + len(buf), "bytes")

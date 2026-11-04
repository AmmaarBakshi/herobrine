"""Generates every texture the mod ships (original art, fixed seeds -> byte-identical output).

Run from the repo root:  python tools/gen_textures.py

Outputs (under src/main/resources/assets/herobrine/textures/):
  entity/herobrine/herobrine.png       64x64 player-layout skin
  entity/herobrine/herobrine_eyes.png  64x64 emissive layer (only the eyes)
  item/herobrine_spawn_egg.png         16x16
  item/mirror_shard.png                16x16
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path("src/main/resources/assets/herobrine/textures")
CLEAR = (0, 0, 0, 0)


def jitter(rng, rgb, amount):
    n = rng.randint(-amount, amount)
    return tuple(max(0, min(255, c + n)) for c in rgb) + (255,)


def shade(rgba, factor):
    r, g, b, a = rgba
    return (int(r * factor), int(g * factor), int(b * factor), a)


# ---------------------------------------------------------------------------
# Skin (vanilla 64x64 player UV layout)
# ---------------------------------------------------------------------------

HAIR = (34, 25, 20)
SKIN = (163, 122, 94)
STUBBLE = (120, 86, 64)
BROW = (52, 36, 28)
SHIRT = (46, 58, 74)
SHIRT_DARK = (34, 43, 56)
BELT = (58, 40, 28)
BUCKLE = (120, 110, 90)
PANTS = (31, 29, 51)
BOOTS = (48, 40, 36)
SOLE = (30, 26, 24)
EYE = (255, 255, 255)


def box_faces(u, v, w, h, d):
    """Rectangles (x, y, width, height) of each face for a model box at texture offset (u, v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_box(img, rng, u, v, w, h, d, color_at):
    px = img.load()
    for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                rgba = color_at(face, x, y, fw, fh, rng)
                # Soft vertical gradient gives the flat pixels some volume.
                if face not in ("top", "bottom") and rgba[3] == 255:
                    rgba = shade(rgba, 1.0 - 0.10 * (y / max(1, fh - 1)))
                px[fx + x, fy + y] = rgba


def head_color(face, x, y, w, h, rng):
    if face == "top":
        return jitter(rng, HAIR, 6)
    if face == "bottom":
        return jitter(rng, STUBBLE, 6)
    if face == "back":
        return jitter(rng, HAIR, 6) if y < 7 else jitter(rng, STUBBLE, 6)
    if face in ("left", "right"):
        if y < 3 or (y < 6 and ((face == "right" and x < 3) or (face == "left" and x > 4))):
            return jitter(rng, HAIR, 6)
        if y == 4 and x in (3, 4):
            return jitter(rng, shade(SKIN + (255,), 0.85)[:3], 4)  # ear
        return jitter(rng, STUBBLE if y >= 6 else SKIN, 6)
    # front: the face
    if y < 2 or (y == 2 and x in (0, 7)):
        return jitter(rng, HAIR, 6)
    if y == 3:
        return jitter(rng, BROW, 4) if x in (1, 2, 5, 6) else jitter(rng, SKIN, 6)
    if y == 4:
        if x in (1, 2, 5, 6):
            return EYE + (255,)
        return jitter(rng, SKIN, 6)
    if y == 5:
        return jitter(rng, shade(SKIN + (255,), 0.82)[:3], 4) if x in (3, 4) else jitter(rng, SKIN, 6)
    if y == 6:
        return jitter(rng, (92, 58, 46), 4) if 2 <= x <= 5 else jitter(rng, STUBBLE, 6)
    return jitter(rng, STUBBLE, 6)


def body_color(face, x, y, w, h, rng):
    if face == "top":
        return jitter(rng, SKIN if face == "top" and 3 <= x <= 4 and y >= 2 else SHIRT, 5)
    if face == "bottom":
        return jitter(rng, PANTS, 5)
    if y == 10:
        if face == "front" and x in (3, 4):
            return jitter(rng, BUCKLE, 6)
        return jitter(rng, BELT, 5)
    if y == 11:
        return jitter(rng, PANTS, 5)
    if face == "front" and y == 0 and x in (3, 4):
        return jitter(rng, SKIN, 5)  # collar opening
    if face == "front" and y == 1 and x == 3:
        return jitter(rng, SKIN, 5)
    # Darker stitched seam down the front and a torn patch on the back.
    if face == "front" and x == 4 and 2 <= y <= 9:
        return jitter(rng, SHIRT_DARK, 4)
    if face == "back" and (x, y) in ((2, 6), (3, 6), (3, 7), (5, 3)):
        return jitter(rng, SHIRT_DARK, 4)
    return jitter(rng, SHIRT, 6)


def arm_color(face, x, y, w, h, rng):
    if face == "top":
        return jitter(rng, SHIRT, 5)
    if face == "bottom":
        return jitter(rng, SKIN, 5)
    if y <= 8:
        return jitter(rng, SHIRT, 6)
    if y == 9:
        return jitter(rng, SHIRT_DARK, 4)
    return jitter(rng, SKIN, 6)


def leg_color(face, x, y, w, h, rng):
    if face == "top":
        return jitter(rng, PANTS, 5)
    if face == "bottom":
        return jitter(rng, SOLE, 3)
    if y <= 8:
        return jitter(rng, PANTS, 5)
    if y == 9:
        return jitter(rng, shade(BOOTS + (255,), 1.15)[:3], 4)
    return jitter(rng, BOOTS, 5)


def fringe_color(face, x, y, w, h, rng):
    """Hat overlay: a ragged fringe of hair hanging over the forehead, nothing else."""
    if face == "front" and y == 0:
        return jitter(rng, HAIR, 5)
    if face == "front" and y == 1 and x in (0, 1, 4, 7):
        return jitter(rng, HAIR, 5)
    return CLEAR


def build_skin():
    rng = random.Random(0xB1A2)
    img = Image.new("RGBA", (64, 64), CLEAR)
    paint_box(img, rng, 0, 0, 8, 8, 8, head_color)       # head
    paint_box(img, rng, 32, 0, 8, 8, 8, fringe_color)    # hat overlay
    paint_box(img, rng, 16, 16, 8, 12, 4, body_color)    # body
    paint_box(img, rng, 40, 16, 4, 12, 4, arm_color)     # right arm
    paint_box(img, rng, 32, 48, 4, 12, 4, arm_color)     # left arm
    paint_box(img, rng, 0, 16, 4, 12, 4, leg_color)      # right leg
    paint_box(img, rng, 16, 48, 4, 12, 4, leg_color)     # left leg
    return img


def build_eyes():
    """Only the eye pixels; rendered full-bright so they glow in total darkness."""
    img = Image.new("RGBA", (64, 64), CLEAR)
    px = img.load()
    fx, fy = 8, 8  # head front face origin
    for x in (1, 2, 5, 6):
        px[fx + x, fy + 4] = (255, 255, 255, 255)
    return img


# ---------------------------------------------------------------------------
# Items (16x16)
# ---------------------------------------------------------------------------

def build_spawn_egg():
    rng = random.Random(0xE66)
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    cx, cy = 7.5, 8.6
    for y in range(16):
        for x in range(16):
            # Egg: narrower at the top.
            rx = 5.2 if y > cy else 5.2 - (cy - y) * 0.18
            ry = 6.6
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                px[x, y] = jitter(rng, SHIRT, 6)
    # Outline.
    out = img.copy()
    opx = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and any(
                0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 0
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            ):
                opx[x, y] = (14, 14, 18, 255)
    # Hair-coloured spots and two white "eyes".
    for (x, y) in ((6, 4), (9, 5), (4, 9), (10, 11), (7, 13), (11, 8)):
        if opx[x, y][3] == 255 and opx[x, y][:3] != (14, 14, 18):
            opx[x, y] = jitter(rng, HAIR, 4)
    for (x, y) in ((6, 8), (9, 8)):
        opx[x, y] = (255, 255, 255, 255)
    return out


def build_mirror_shard():
    rng = random.Random(0x5A4D)
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    # A jagged diagonal sliver of dark glass.
    shape = [
        "................",
        "...........##...",
        "..........####..",
        ".........#####..",
        "........######..",
        ".......######...",
        "......#######...",
        ".....#######....",
        "....#######.....",
        "....######......",
        "...######.......",
        "...#####........",
        "..#####.........",
        "..####..........",
        "...##...........",
        "................",
    ]
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "#":
                t = (x + (15 - y)) / 30.0
                base = (int(40 + 80 * t), int(44 + 86 * t), int(60 + 110 * t))
                px[x, y] = jitter(rng, base, 6)
    # Edge highlight along the upper-left edge, glowing eye-white glint in the middle.
    for y, row in enumerate(shape):
        first = row.find("#")
        if first >= 0:
            px[first, y] = (200, 210, 230, 255)
    px[8, 7] = (255, 255, 255, 255)
    px[9, 6] = (230, 236, 255, 255)
    return img


def save(img, rel):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print("wrote", path)


if __name__ == "__main__":
    save(build_skin(), "entity/herobrine/herobrine.png")
    save(build_eyes(), "entity/herobrine/herobrine_eyes.png")
    save(build_spawn_egg(), "item/herobrine_spawn_egg.png")
    save(build_mirror_shard(), "item/mirror_shard.png")

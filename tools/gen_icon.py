"""Generates assets/herobrine/icon.png procedurally (original art, deterministic)."""
import random
import sys
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("src/main/resources/assets/herobrine/icon.png")
rng = random.Random(1337)  # fixed seed -> byte-identical output

S = 16
img = Image.new("RGBA", (S, S), (10, 10, 14, 255))
px = img.load()

# Vignette background: darker toward the corners.
for y in range(S):
    for x in range(S):
        d = abs(x - 7.5) + abs(y - 7.5)
        v = max(4, 22 - int(d * 1.3)) + rng.randint(0, 3)
        px[x, y] = (v, v, v + 4, 255)

# Head block (x 3..12, y 3..13): hair on top, skin below.
for y in range(3, 14):
    for x in range(3, 13):
        if y < 6 or (y < 8 and x in (3, 12)):
            n = rng.randint(-6, 6)
            px[x, y] = (40 + n, 28 + n, 20 + n, 255)  # dark brown hair
        else:
            n = rng.randint(-8, 8)
            px[x, y] = (150 + n, 108 + n, 82 + n, 255)  # skin

# Shadowed brow above the eyes.
for x in range(4, 12):
    r, g, b, a = px[x, 7]
    px[x, 7] = (r * 3 // 4, g * 3 // 4, b * 3 // 4, a)

# Blank glowing eyes.
for x in (5, 6, 9, 10):
    px[x, 8] = (255, 255, 255, 255)

# Mouth/beard shading.
for x in range(6, 10):
    px[x, 11] = (70, 46, 34, 255)

# Soft glow around eyes.
for (x, y) in [(4, 8), (7, 8), (8, 8), (11, 8), (5, 9), (6, 9), (9, 9), (10, 9)]:
    r, g, b, a = px[x, y]
    px[x, y] = (min(255, r + 60), min(255, g + 60), min(255, b + 70), a)

img = img.resize((128, 128), Image.NEAREST)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT, optimize=True)
print("wrote", OUT)

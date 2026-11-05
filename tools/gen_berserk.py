"""Builds the BERSERK memorial's structure template (original, fixed seed).

Run from the repo root:  python tools/gen_berserk.py
Writes src/main/resources/data/herobrine/structure/berserk/memorial.nbt (gzipped NBT, byte-identical on
every run). Standard library only.

The memorial is an 11 x 11 ruin of old stone around a broken shrine. It explains nothing; the lore is in
what it shows and in the books of a hidden room under the trapdoor. Template layout (y up, north is z = 0,
the way in is the south side, z = 10):

  y 0-2   foundation, and the hidden room (3 x 4) with a ladder up to the trapdoor
  y 3     the floor, flush with the ground: worn stone bricks, a blackstone sigil, the broken shrine
  y 4-10  pillars of different heights, the altar, the signs; everything else cleared to air
"""
import gzip
import io
import random
import struct
from pathlib import Path

OUT = Path("src/main/resources/data/herobrine/structure/berserk/memorial.nbt")
DATA_VERSION = 5023  # Minecraft 26.3 (version.json "world_version")
SIZE = (11, 11, 11)
LOOT_TABLE = "herobrine:chests/berserk"

# --- Minimal NBT writer -------------------------------------------------------------------------
TAG_END, TAG_BYTE, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 8, 9, 10


class Byte(int):
    """An int written as TAG_Byte (booleans in NBT)."""


def _tag_type(value):
    if isinstance(value, Byte):
        return TAG_BYTE
    if isinstance(value, bool):
        raise TypeError("use Byte(...) for NBT booleans")
    if isinstance(value, int):
        return TAG_INT
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, list):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    raise TypeError(type(value))


def _write_string(out, text):
    data = text.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _write_payload(out, value):
    kind = _tag_type(value)
    if kind == TAG_BYTE:
        out.write(struct.pack(">b", int(value)))
    elif kind == TAG_INT:
        out.write(struct.pack(">i", value))
    elif kind == TAG_STRING:
        _write_string(out, value)
    elif kind == TAG_LIST:
        element = _tag_type(value[0]) if value else TAG_END
        out.write(struct.pack(">bi", element, len(value)))
        for item in value:
            _write_payload(out, item)
    else:
        for key in value:  # insertion order, so the output is stable
            out.write(struct.pack(">b", _tag_type(value[key])))
            _write_string(out, key)
            _write_payload(out, value[key])
        out.write(struct.pack(">b", TAG_END))


def nbt_bytes(root):
    raw = io.BytesIO()
    raw.write(struct.pack(">b", TAG_COMPOUND))
    _write_string(raw, "")
    _write_payload(raw, root)
    packed = io.BytesIO()
    # mtime=0 and no file name: the same input always gives the same bytes.
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0) as gz:
        gz.write(raw.getvalue())
    return packed.getvalue()


# --- Template builder ---------------------------------------------------------------------------
class Template:
    def __init__(self):
        self.blocks = {}  # (x, y, z) -> (block id, properties, block entity nbt)

    def put(self, x, y, z, block, props=None, nbt=None):
        assert 0 <= x < SIZE[0] and 0 <= y < SIZE[1] and 0 <= z < SIZE[2], (x, y, z)
        self.blocks[(x, y, z)] = (block, dict(props or {}), nbt)

    def build(self):
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            block, props, nbt = self.blocks[pos]
            key = (block, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {"id": block}
                if props:
                    entry["properties"] = {k: props[k] for k in sorted(props)}
                palette.append(entry)
            entry = {"pos": list(pos), "state": index[key]}
            if nbt is not None:
                entry["nbt"] = nbt
            blocks.append(entry)
        return {"size": list(SIZE), "entities": [], "blocks": blocks, "palette": palette, "DataVersion": DATA_VERSION}


def sign_text(lines, color="black", glowing=False):
    return {"messages": [{"text": line} for line in lines], "color": color, "has_glowing_text": Byte(1 if glowing else 0)}


def sign_nbt(lines, color="black", glowing=False):
    return {
        "id": "minecraft:sign",
        "front_text": sign_text(lines, color, glowing),
        "back_text": sign_text(["", "", "", ""]),
        # Waxed: the words can't be edited away.
        "is_waxed": Byte(1),
    }


def memorial():
    rng = random.Random(0xBE55E4)
    t = Template()
    w, h, d = SIZE

    # Clear the air space first, so grass, flowers and saplings don't poke through the ruin.
    for y in range(4, h):
        for x in range(w):
            for z in range(d):
                t.put(x, y, z, "minecraft:air")

    # Foundation and the hidden room.
    room_x, room_z = range(4, 7), range(4, 8)
    for y in range(0, 3):
        for x in range(w):
            for z in range(d):
                inside = x in room_x and z in room_z
                wall = 3 <= x <= 7 and 3 <= z <= 8
                if y == 0 and inside:
                    t.put(x, y, z, "minecraft:polished_blackstone_bricks")
                elif inside:
                    t.put(x, y, z, "minecraft:air")
                elif wall:
                    t.put(x, y, z, "minecraft:cracked_deepslate_bricks" if rng.random() < 0.35 else "minecraft:deepslate_bricks")
                else:
                    t.put(x, y, z, "minecraft:stone")
    t.put(5, 1, 4, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": LOOT_TABLE})
    t.put(4, 1, 5, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    t.put(6, 1, 6, "minecraft:redstone_torch", {"lit": "true"})
    t.put(4, 1, 6, "minecraft:skeleton_skull", {"rotation": "6"})
    t.put(4, 2, 4, "minecraft:cobweb")
    t.put(6, 2, 4, "minecraft:cobweb")
    t.put(5, 1, 7, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
    t.put(5, 2, 7, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
    t.put(6, 2, 5, "minecraft:dark_oak_wall_sign", {"facing": "west", "waterlogged": "false"},
          sign_nbt(["YOU DUG.", "OF COURSE", "YOU DUG.", ""]))

    # The floor: worn stone bricks with ragged edges, a blackstone ring with four spokes, the shrine.
    for x in range(w):
        for z in range(d):
            edge = x in (0, w - 1) or z in (0, d - 1)
            corner = x in (0, w - 1) and z in (0, d - 1)
            if corner or (edge and rng.random() < 0.3):
                continue  # broken away: the ground shows through
            roll = rng.random()
            block = "minecraft:stone_bricks"
            if edge and roll < 0.4:
                block = "minecraft:mossy_cobblestone"
            elif roll < 0.15:
                block = "minecraft:cracked_stone_bricks"
            t.put(x, 3, z, block)
    for x in range(w):
        for z in range(d):
            dx, dz = x - 5, z - 5
            ring = round((dx * dx + dz * dz) ** 0.5) == 3
            spoke = (dx == 0 or dz == 0) and 3 < abs(dx) + abs(dz) <= 4
            if ring or spoke:
                t.put(x, 3, z, "minecraft:polished_blackstone")
    # The broken shrine: one gold block left of nine, netherrack where the fire went, two red lights of four.
    for x in range(4, 7):
        for z in range(4, 7):
            t.put(x, 3, z, "minecraft:cracked_stone_bricks")
    t.put(4, 3, 4, "minecraft:gold_block")
    t.put(5, 3, 5, "minecraft:netherrack")
    t.put(5, 4, 4, "minecraft:redstone_torch", {"lit": "true"})
    t.put(4, 4, 5, "minecraft:redstone_torch", {"lit": "true"})
    t.put(5, 3, 7, "minecraft:dark_oak_trapdoor",
          {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})

    # The altar to the north, with the riddle on its face.
    for x in range(4, 7):
        t.put(x, 4, 2, "minecraft:polished_blackstone_bricks")
    t.put(5, 4, 2, "minecraft:chiseled_polished_blackstone")
    t.put(5, 5, 2, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    t.put(4, 5, 2, "minecraft:candle", {"candles": "3", "lit": "false", "waterlogged": "false"})
    t.put(6, 5, 2, "minecraft:candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    t.put(5, 4, 3, "minecraft:dark_oak_wall_sign", {"facing": "south", "waterlogged": "false"},
          sign_nbt(["GOLD BENEATH", "FIRE ABOVE", "RED LIGHT", "ON EVERY SIDE"]))

    # Four pillars, none whole any more.
    pillars = {(1, 1): 5, (9, 1): 3, (1, 9): 4, (9, 9): 3}
    for (x, z), height in pillars.items():
        for y in range(4, 4 + height):
            top = y == 3 + height
            if top and height >= 4:
                block = "minecraft:chiseled_stone_bricks"
            elif top and height == 3:
                block = "minecraft:stone_brick_wall"
            else:
                block = "minecraft:stone_bricks"
            t.put(x, y, z, block)
    t.put(1, 9, 1, "minecraft:skeleton_skull", {"rotation": "0"})
    t.put(9, 7, 1, "minecraft:cobweb")
    t.put(2, 4, 9, "minecraft:cobweb")
    t.put(1, 5, 10, "minecraft:dark_oak_wall_sign", {"facing": "south", "waterlogged": "false"},
          sign_nbt(["HE WAS NOT", "BUILT.", "HE WAS", "REMEMBERED."]))
    t.put(9, 5, 10, "minecraft:dark_oak_wall_sign", {"facing": "south", "waterlogged": "false"},
          sign_nbt(["DO NOT", "LOOK FOR", "HIS FACE.", ""]))

    # The name, in red, glowing faintly, where you walk in.
    t.put(5, 4, 9, "minecraft:dark_oak_sign", {"rotation": "0", "waterlogged": "false"},
          sign_nbt(["", "BERSERK", "", ""], color="red", glowing=True))

    # Small wrong details. Moss and cracks are added per world by the berserk_decay processor list.
    t.put(3, 4, 3, "minecraft:potted_dead_bush")
    t.put(7, 4, 7, "minecraft:potted_dead_bush")
    return t.build()


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(nbt_bytes(memorial()))
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()

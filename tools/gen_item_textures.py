#!/usr/bin/env python3
"""
Makes the 16x16 item textures, item models and item model definitions for the custom dungeon items.

Every item is a hand-drawn template (sword, axe, staff...) recoloured with its own palette, then given a dark
outline so it reads like a vanilla item. No image library needed: the PNGs are written by hand with zlib.

Run from the repo root:  python3 tools/gen_item_textures.py [preview.png]
"""
import json
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "client", "resources", "assets", "abpsmod")

# Template letters: L/M/D main material light/mid/dark, w white glint, h/H handle light/dark, g/G accent light/dark.
T = {
    "sword": [
        "..............ww",
        ".............wLM",
        "............wLMD",
        "...........LLMD.",
        "..........LLMD..",
        ".........LLMD...",
        "........LLMD....",
        ".......LLMD.....",
        "..gG..LLMD......",
        "..GgGLLMD.......",
        "...GgGMD........",
        "....hGgG........",
        "...hH.GgG.......",
        "..hH...G........",
        ".gH.............",
        "gG..............",
    ],
    "greatsword": [
        ".............www",
        "............wLLM",
        "...........wLLMD",
        "..........LLLMD.",
        ".........LLLMD..",
        "........LLLMD...",
        ".......LLLMD....",
        "......LLLMD.....",
        ".gG..LLLMD......",
        ".GgGLLLMD.......",
        "..GgGLMD........",
        "...hGgGD........",
        "..hH.GgG........",
        ".hH...GgG.......",
        "gH.....G........",
        "G...............",
    ],
    "dagger": [
        "................",
        "................",
        "................",
        "................",
        "...........ww...",
        "..........wLM...",
        ".........LLMD...",
        "........LLMD....",
        ".......LLMD.....",
        "....gGLLMD......",
        ".....GgMD.......",
        ".....hGgG.......",
        "....hH.G........",
        "...hH...........",
        "..gH............",
        "..G.............",
    ],
    "axe": [
        "......LLM.......",
        ".....wLMMD......",
        "....LLMMMMD.....",
        "...LLMMMMMMD....",
        "...LMMMMMMhD....",
        "....LMMMMhHD....",
        ".....DDMhHDD....",
        "......hH.DD.....",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..hH............",
        ".hH.............",
        "gH..............",
        "G...............",
        "................",
    ],
    "hammer": [
        ".....LLM........",
        "....wLMMD.......",
        "...LLMMMMD......",
        "..LLMggMMMD.....",
        "...LMgGMMhD.....",
        "....LMMMhHD.....",
        ".....DMhHDD.....",
        "......hHD.......",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..hH............",
        ".hH.............",
        "gH..............",
        "G...............",
        "................",
    ],
    "pickaxe": [
        "....wLLLM.......",
        "...LL...MMD.....",
        "..L......hMD....",
        "........hH.MD...",
        ".......hH...MD..",
        "......hH.....D..",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..hH............",
        ".hH.............",
        "gH..............",
        "G...............",
        "................",
        "................",
        "................",
    ],
    "drill": [
        "..wLLLLLLM......",
        ".LLggMMMMMD.....",
        "L.....hMMMMD....",
        ".....hH..MMD....",
        "....hH....MMD...",
        "...hH......MD...",
        "..hH........D...",
        ".hH.............",
        "gH..............",
        "G...............",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "shovel": [
        "..........wLLM..",
        ".........LLMMMD.",
        ".........LMMMMD.",
        ".........LMMMMD.",
        "..........MMMD..",
        "..........hDD...",
        ".........hH.....",
        "........hH......",
        ".......hH.......",
        "......hH........",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..gG............",
        ".gG.............",
        "gG..............",
    ],
    "scythe": [
        "..wLLLLMM.......",
        ".LL.....MMD.....",
        "L.........MhD...",
        "L..........hD...",
        "..........hH....",
        ".........hH.....",
        "........hH......",
        ".......hH.......",
        "......hH........",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..hH............",
        ".gH.............",
        "gG..............",
        "................",
    ],
    "hoe": [
        "......wLLM......",
        ".....LL..MD.....",
        "..........hD....",
        ".........hH.....",
        "........hH......",
        ".......hH.......",
        "......hH........",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..hH............",
        ".gH.............",
        "gG..............",
        "................",
        "................",
        "................",
    ],
    "staff": [
        "............wg..",
        "...........gLMg.",
        "..........gLMMDg",
        "..........gMMDg.",
        "...........gDgG.",
        "..........hHG...",
        ".........hH.....",
        "........hH......",
        ".......hH.......",
        "......hH........",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..hH............",
        ".gH.............",
        "gG..............",
    ],
    "spear": [
        ".............wL.",
        "............wLMD",
        "...........LLMD.",
        "..........LMMD..",
        "..........gDD...",
        ".........hGg....",
        "........hH......",
        ".......hH.......",
        "......hH........",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..hH............",
        ".hH.............",
        "gH..............",
        "G...............",
    ],
    "chakram": [
        "................",
        ".....wLLLM......",
        "....LLMMMMD.....",
        "...LM.....MD....",
        "..LM..ggG..MD...",
        "..LM.gG.gG.MD...",
        "..LM.g...G.MD...",
        "..LM.gG.gG.MD...",
        "..LM..gGG..MD...",
        "...LM.....MD....",
        "....MMDDDDD.....",
        ".....MDDDD......",
        "................",
        "................",
        "................",
        "................",
    ],
    "feather": [
        "................",
        "...........wLL..",
        "..........wLMg..",
        ".........LLMgD..",
        "........LLMgMD..",
        ".......LLMgMD...",
        "......LLMgMD....",
        ".....LLMgMD.....",
        "....LLMgMD......",
        "...LLMgMD.......",
        "...LMgMD........",
        "...hgMD.........",
        "..hH............",
        ".hH.............",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "...LLL....MMD...",
        "..LLMMLLLLMMDD..",
        "..LMMMMMMMMMMD..",
        "..LMMMgGMMMMMD..",
        "..LLMMggMMMMDD..",
        "...LMMMMMMMMD...",
        "...LMMMMMMMMD...",
        "...LMMMgGMMMD...",
        "...LMMMMMMMMD...",
        "...LMMMMMMMMD...",
        "...LLMMMMMMDD...",
        "....DDDDDDDD....",
        "................",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "...LLM...LLM....",
        "...LMD...LMD....",
        "...LMD...LMD....",
        "...LgD...LgD....",
        "...LMD...LMD....",
        "..LLMD..LLMD....",
        ".LLMMD.LLMMD....",
        ".LMMMDDLMMMDD...",
        ".DDDDD.DDDDD....",
        "................",
        "................",
        "................",
    ],
}


def shade(c, k):
    """Lighter for k > 0, darker for k < 0."""
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    if k > 0:
        r, g, b = r + (255 - r) * k, g + (255 - g) * k, b + (255 - b) * k
    else:
        r, g, b = r * (1 + k), g * (1 + k), b * (1 + k)
    return (int(r) << 16) | (int(g) << 8) | int(b)


# id: (template, main color, handle color, accent color, base item for the model parent)
ITEMS = {
    # Older dungeon items, now with their own look
    "bloodfang": ("sword", 0xC62828, 0x4E342E, 0xFFCDD2),
    "frostbite": ("sword", 0x81D4FA, 0x37474F, 0xE1F5FE),
    "stormcaller": ("sword", 0x4FC3F7, 0x283593, 0xFFF59D),
    "sunblade": ("sword", 0xFFD54F, 0x8D6E63, 0xFF6F00),
    "voidrender": ("greatsword", 0x7E57C2, 0x1A1028, 0xE040FB),
    "reaper": ("scythe", 0xB0BEC5, 0x3E2723, 0x76FF03),
    "earthsplitter": ("axe", 0x8D6E63, 0x4E342E, 0xFFAB40),
    "timberfall": ("axe", 0xA1887F, 0x6D4C41, 0xAED581),
    "quarry_pick": ("pickaxe", 0x90A4AE, 0x5D4037, 0xFFD54F),
    "molten_pick": ("pickaxe", 0xFF7043, 0x3E2723, 0xFFEB3B),
    "veinripper": ("pickaxe", 0x26C6DA, 0x263238, 0xB2FF59),
    "earthmover": ("shovel", 0xBCAAA4, 0x5D4037, 0x8D6E63),
    "bulwark": ("chestplate", 0x78909C, 0x37474F, 0xFFD54F),
    "windrunners": ("boots", 0xB2EBF2, 0x546E7A, 0x80DEEA),
    "phoenix_feather": ("feather", 0xFF7043, 0xBF360C, 0xFFEB3B),
    # New weapons
    "thunder_hammer": ("hammer", 0x90A4AE, 0x3E2723, 0x40C4FF),
    "ember_staff": ("staff", 0xFF6D00, 0x4E342E, 0xFFD180),
    "frost_staff": ("staff", 0x80DEEA, 0x455A64, 0xE1F5FE),
    "soul_staff": ("staff", 0x64FFDA, 0x263238, 0x1DE9B6),
    "shadow_daggers": ("dagger", 0x5E35B1, 0x212121, 0xB388FF),
    "dragonbone_greatsword": ("greatsword", 0xEFEBE9, 0x5D4037, 0xD32F2F),
    "tide_spear": ("spear", 0x26A69A, 0x37474F, 0x80CBC4),
    "echo_chakram": ("chakram", 0xB0BEC5, 0x263238, 0x00E5FF),
    # New tools
    "prospector_pick": ("pickaxe", 0xFFCA28, 0x6D4C41, 0x4DD0E1),
    "titan_drill": ("drill", 0x78909C, 0x37474F, 0xFF9800),
    "harvest_scythe": ("scythe", 0xC0CA33, 0x6D4C41, 0x9CCC65),
    "gravedigger": ("shovel", 0x607D8B, 0x3E2723, 0x76FF03),
}

# Items that are held like tools (handheld model); the rest use the flat generated model
FLAT = {"bulwark", "windrunners", "phoenix_feather", "echo_chakram"}


def pixels(template, main, handle, accent):
    pal = {
        "L": shade(main, 0.35), "M": main, "D": shade(main, -0.35), "w": 0xFFFFFF,
        "h": handle, "H": shade(handle, -0.35), "g": shade(accent, 0.2), "G": shade(accent, -0.2),
    }
    rows = T[template]
    grid = [[None] * 16 for _ in range(16)]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                grid[y][x] = pal[ch]
    # A dark outline around every shape, like vanilla items
    outline = shade(main, -0.8)
    out = [row[:] for row in grid]
    for y in range(16):
        for x in range(16):
            if grid[y][x] is not None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and grid[ny][nx] is not None:
                    out[y][x] = outline
                    break
    return out


def write_png(path, grid, scale=1):
    size = 16 * scale
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            c = grid[y // scale][x // scale] if isinstance(grid[0], list) and len(grid) == 16 else grid[y][x]
            if c is None:
                raw += bytes((0, 0, 0, 0))
            else:
                raw += bytes(((c >> 16) & 255, (c >> 8) & 255, c & 255, 255))
    write_rgba(path, size, size, raw)


def write_rgba(path, w, h, raw):
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def main():
    tex_dir = os.path.join(ASSETS, "textures", "item")
    model_dir = os.path.join(ASSETS, "models", "item")
    items_dir = os.path.join(ASSETS, "items")
    for d in (tex_dir, model_dir, items_dir):
        os.makedirs(d, exist_ok=True)
    grids = []
    for item_id, (template, main_c, handle_c, accent_c) in ITEMS.items():
        grid = pixels(template, main_c, handle_c, accent_c)
        grids.append(grid)
        write_png(os.path.join(tex_dir, item_id + ".png"), grid)
        parent = "minecraft:item/generated" if item_id in FLAT else "minecraft:item/handheld"
        with open(os.path.join(model_dir, item_id + ".json"), "w") as f:
            json.dump({"parent": parent, "textures": {"layer0": "abpsmod:item/" + item_id}}, f, indent=2)
            f.write("\n")
        with open(os.path.join(items_dir, item_id + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "abpsmod:item/" + item_id}}, f, indent=2)
            f.write("\n")
    if len(sys.argv) > 1:
        # A preview sheet: every item at 8x on a grey background, 7 to a row
        scale, cols = 8, 7
        rows = (len(grids) + cols - 1) // cols
        w, h = cols * 17 * scale, rows * 17 * scale
        raw = bytearray()
        for y in range(h):
            raw.append(0)
            for x in range(w):
                gx, gy = x // (17 * scale), y // (17 * scale)
                px, py = (x // scale) % 17, (y // scale) % 17
                i = gy * cols + gx
                c = None
                if i < len(grids) and px < 16 and py < 16:
                    c = grids[i][py][px]
                if c is None:
                    c = 0x6E6E6E if (px + py) % 2 == 0 else 0x787878
                raw += bytes(((c >> 16) & 255, (c >> 8) & 255, c & 255, 255))
        write_rgba(sys.argv[1], w, h, raw)
    print("wrote", len(ITEMS), "items")


if __name__ == "__main__":
    main()

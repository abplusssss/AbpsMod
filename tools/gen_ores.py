#!/usr/bin/env python3
"""
Generates everything the Ruby & Endite content needs besides Java code: item, block and worn-armor textures, block
models and states, item model definitions, English names, recipes, loot tables, tags, worldgen features and the
armor's equipment assets. File formats are copied from Minecraft 26.3's own data files.

    VANILLA=/path/to/extracted/minecraft-assets python3 tools/gen_ores.py

VANILLA must contain assets/minecraft/textures (the item and block textures used as shapes to recolour).
"""
import colorsys
import json
import os
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
VANILLA = os.environ.get("VANILLA", "mca")
VT = os.path.join(VANILLA, "assets", "minecraft", "textures")
ASSETS = os.path.join(ROOT, "src", "client", "resources", "assets", "abpsmod")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "abpsmod"

RAMPS = {
    "ruby": [(0x33, 0x03, 0x0e), (0x62, 0x08, 0x1a), (0x96, 0x0e, 0x26), (0xc4, 0x16, 0x30), (0xe8, 0x2c, 0x44), (0xff, 0x66, 0x74), (0xff, 0xb8, 0xbe)],
    "endite": [(0x1c, 0x06, 0x2c), (0x3e, 0x12, 0x5e), (0x66, 0x22, 0x96), (0x93, 0x3c, 0xd0), (0xbe, 0x6e, 0xf2), (0xe2, 0xab, 0xff), (0xf8, 0xe4, 0xff)],
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp_recolor(src, dst, ramp, select=lambda c: c[3] > 10, gamma=1.0):
    """Maps the selected pixels onto a colour ramp by brightness, keeping the shading of the original."""
    im = Image.open(src).convert("RGBA")
    px = im.load()
    pts = [(x, y) for y in range(im.size[1]) for x in range(im.size[0]) if select(px[x, y])]
    if not pts:
        im.save(dst)
        return
    ls = [lum(px[x, y]) for x, y in pts]
    lo, hi = min(ls), max(ls)
    R = RAMPS[ramp]
    for x, y in pts:
        t = ((lum(px[x, y]) - lo) / max(1, hi - lo)) ** gamma
        px[x, y] = R[min(len(R) - 1, int(round(t * (len(R) - 1))))] + (px[x, y][3],)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    im.save(dst)


def cyanish(c):
    if c[3] < 10:
        return False
    h, s, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    return (0.40 < h < 0.58 and s > 0.18) or (c[2] > c[0] + 12 and c[1] > c[0] + 12 and v > 0.55)


# ------------------------------------------------------------------ textures

def textures():
    # Item sprites: ruby and endite gear on vanilla's own shapes (tools/ore_sprites.py)
    tmp = tempfile.mkdtemp()
    env = dict(os.environ, VANILLA=VANILLA, SPRITES_OUT=tmp + "/")
    subprocess.run([sys.executable, os.path.join(ROOT, "tools", "ore_sprites.py")], env=env, cwd=tmp, check=True, stdout=subprocess.DEVNULL)
    item_dir = os.path.join(ASSETS, "textures", "item")
    block_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(item_dir, exist_ok=True)
    os.makedirs(block_dir, exist_ok=True)
    for f in os.listdir(tmp):
        n = f[:-4]
        if n.endswith("_ore"):
            shutil.copy(os.path.join(tmp, f), os.path.join(block_dir, f))
        elif n.startswith(("ruby", "endite")):
            shutil.copy(os.path.join(tmp, f), os.path.join(item_dir, f))
    shutil.rmtree(tmp)
    # Blocks
    ramp_recolor(f"{VT}/block/diamond_block.png", f"{block_dir}/ruby_block.png", "ruby", gamma=1.2)
    ramp_recolor(f"{VT}/block/stone_bricks.png", f"{block_dir}/ruby_bricks.png", "ruby", gamma=0.9)
    ramp_recolor(f"{VT}/block/redstone_lamp_on.png", f"{block_dir}/ruby_lamp.png", "ruby", gamma=0.7)
    ramp_recolor(f"{VT}/block/netherite_block.png", f"{block_dir}/endite_block.png", "endite", gamma=1.45)
    ramp_recolor(f"{VT}/block/polished_blackstone_bricks.png", f"{block_dir}/endite_plating.png", "endite", gamma=1.25)
    # Smithing template
    ramp_recolor(f"{VT}/item/netherite_upgrade_smithing_template.png", f"{item_dir}/endite_upgrade_smithing_template.png", "endite",
                 select=lambda c: c[3] > 10 and (c[0] > c[1] + 8 or abs(c[0] - c[1]) < 10 and c[0] < 120), gamma=1.0)
    # The gem in the middle of the template: from diamond cyan to an endite crystal
    ramp_recolor(f"{item_dir}/endite_upgrade_smithing_template.png", f"{item_dir}/endite_upgrade_smithing_template.png", "endite", select=cyanish, gamma=0.6)
    # Armor as worn
    eq = os.path.join(ASSETS, "textures", "entity", "equipment")
    for layer in ("humanoid", "humanoid_leggings"):
        ramp_recolor(f"{VT}/entity/equipment/{layer}/diamond.png", f"{eq}/{layer}/ruby.png", "ruby", select=cyanish, gamma=1.35)
        ramp_recolor(f"{VT}/entity/equipment/{layer}/netherite.png", f"{eq}/{layer}/endite.png", "endite", gamma=0.85)


# ------------------------------------------------------------------ content lists

TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]
SIMPLE_BLOCKS = ["ruby_ore", "deepslate_ruby_ore", "ruby_block", "ruby_bricks", "ruby_lamp", "endite_ore", "endite_block", "endite_plating"]
STAIRS = {"ruby_brick_stairs": "ruby_bricks", "endite_plating_stairs": "endite_plating"}
SLABS = {"ruby_brick_slab": "ruby_bricks", "endite_plating_slab": "endite_plating"}
FLAT_ITEMS = ["ruby", "endite_shard", "endite_ingot", "endite_upgrade_smithing_template"] + [f"{m}_{a}" for m in ("ruby", "endite") for a in ARMOR]
HAND_ITEMS = [f"{m}_{t}" for m in ("ruby", "endite") for t in TOOLS]

NAMES = {
    "ruby": "Ruby", "endite_shard": "Endite Shard", "endite_ingot": "Endite Ingot", "endite_upgrade_smithing_template": "Endite Upgrade",
    "ruby_ore": "Ruby Ore", "deepslate_ruby_ore": "Deepslate Ruby Ore", "ruby_block": "Block of Ruby", "ruby_bricks": "Ruby Bricks",
    "ruby_lamp": "Ruby Lamp", "endite_ore": "Endite Ore", "endite_block": "Block of Endite", "endite_plating": "Endite Plating",
    "ruby_brick_stairs": "Ruby Brick Stairs", "ruby_brick_slab": "Ruby Brick Slab", "endite_plating_stairs": "Endite Plating Stairs",
    "endite_plating_slab": "Endite Plating Slab",
}
for m in ("ruby", "endite"):
    for t in TOOLS + ARMOR:
        NAMES[f"{m}_{t}"] = f"{m.capitalize()} {t.capitalize()}"


# ------------------------------------------------------------------ client assets

def client_assets():
    for b in SIMPLE_BLOCKS:
        write(f"{ASSETS}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
        write(f"{ASSETS}/models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{b}"}})
        write(f"{ASSETS}/items/{b}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{b}"}})
    for s, base in STAIRS.items():
        tex = {"bottom": f"{NS}:block/{base}", "side": f"{NS}:block/{base}", "top": f"{NS}:block/{base}"}
        write(f"{ASSETS}/models/block/{s}.json", {"parent": "minecraft:block/stairs", "textures": tex})
        write(f"{ASSETS}/models/block/{s}_inner.json", {"parent": "minecraft:block/inner_stairs", "textures": tex})
        write(f"{ASSETS}/models/block/{s}_outer.json", {"parent": "minecraft:block/outer_stairs", "textures": tex})
        write(f"{ASSETS}/items/{s}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{s}"}})
        write(f"{ASSETS}/blockstates/{s}.json", stairs_state(s))
    for s, base in SLABS.items():
        tex = {"bottom": f"{NS}:block/{base}", "side": f"{NS}:block/{base}", "top": f"{NS}:block/{base}"}
        write(f"{ASSETS}/models/block/{s}.json", {"parent": "minecraft:block/slab", "textures": tex})
        write(f"{ASSETS}/models/block/{s}_top.json", {"parent": "minecraft:block/slab_top", "textures": tex})
        write(f"{ASSETS}/items/{s}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{s}"}})
        write(f"{ASSETS}/blockstates/{s}.json", {"variants": {
            "type=bottom": {"model": f"{NS}:block/{s}"}, "type=double": {"model": f"{NS}:block/{base}"}, "type=top": {"model": f"{NS}:block/{s}_top"}}})
    for i in FLAT_ITEMS + HAND_ITEMS:
        parent = "minecraft:item/handheld" if i in HAND_ITEMS else "minecraft:item/generated"
        write(f"{ASSETS}/models/item/{i}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{i}"}})
        write(f"{ASSETS}/items/{i}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{i}"}})
    for m in ("ruby", "endite"):
        write(f"{ASSETS}/equipment/{m}.json", {"layers": {"humanoid": [{"texture": f"{NS}:{m}"}], "humanoid_leggings": [{"texture": f"{NS}:{m}"}]}})
    lang_path = f"{ASSETS}/lang/en_us.json"
    lang = json.load(open(lang_path))
    for k, v in NAMES.items():
        kind = "block" if k in SIMPLE_BLOCKS or k in STAIRS or k in SLABS else "item"
        lang[f"{kind}.{NS}.{k}"] = v
    lang["item.abpsmod.endite_upgrade_smithing_template.desc"] = "Netherite gear + endite ingot"
    write(lang_path, lang)


def stairs_state(name):
    """The full stairs blockstate, in vanilla's own layout."""
    m = f"{NS}:block/{name}"
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    v = {}
    for facing, y in rot.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = m + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                yy = y
                if half == "bottom" and shape.endswith("left"):
                    yy = (y + 270) % 360
                if half == "top" and shape.endswith("right"):
                    yy = (y + 90) % 360
                entry = {"model": model}
                if half == "top":
                    entry["x"] = 180
                if yy:
                    entry["y"] = yy
                if half == "top" or yy:
                    entry["uvlock"] = True
                v[f"facing={facing},half={half},shape={shape}"] = entry
    return {"variants": v}


# ------------------------------------------------------------------ data

def recipe(name, obj, ns=NS):
    write(f"{DATA}/{ns}/recipe/{name}.json", obj)


def shaped(pattern, key, result, count=1, category="equipment"):
    r = {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern, "result": {"id": result}}
    if count > 1:
        r["result"]["count"] = count
    return r


def data():
    I = lambda n: f"{NS}:{n}"
    # Tool and armor recipes from rubies (endite gear only comes from smithing)
    tool_patterns = {
        "sword": ["X", "X", "#"], "pickaxe": ["XXX", " # ", " # "], "axe": ["XX", "X#", " #"], "shovel": ["X", "#", "#"], "hoe": ["XX", " #", " #"],
    }
    for t, pat in tool_patterns.items():
        recipe(f"ruby_{t}", shaped(pat, {"#": "minecraft:stick", "X": f"#{NS}:ruby_tool_materials"}, I(f"ruby_{t}")))
    armor_patterns = {"helmet": ["XXX", "X X"], "chestplate": ["X X", "XXX", "XXX"], "leggings": ["XXX", "X X", "X X"], "boots": ["X X", "X X"]}
    for a, pat in armor_patterns.items():
        recipe(f"ruby_{a}", shaped(pat, {"X": f"#{NS}:ruby_tool_materials"}, I(f"ruby_{a}")))
    # Netherite now upgrades from ruby gear: replace vanilla's own smithing recipes
    for t in TOOLS + ARMOR:
        recipe(f"netherite_{t}_smithing", {"type": "minecraft:smithing_transform", "addition": "#minecraft:netherite_tool_materials",
                                           "base": I(f"ruby_{t}"), "result": {"id": f"minecraft:netherite_{t}"},
                                           "template": "minecraft:netherite_upgrade_smithing_template"}, ns="minecraft")
        recipe(f"endite_{t}_smithing", {"type": "minecraft:smithing_transform", "addition": I("endite_ingot"),
                                        "base": f"minecraft:netherite_{t}", "result": {"id": I(f"endite_{t}")},
                                        "template": I("endite_upgrade_smithing_template")})
    # Gems, ingots and blocks
    recipe("ruby_block", shaped(["###", "###", "###"], {"#": I("ruby")}, I("ruby_block"), category="building"))
    recipe("ruby_from_block", {"type": "minecraft:crafting_shapeless", "ingredients": [I("ruby_block")], "result": {"count": 9, "id": I("ruby")}})
    recipe("endite_block", shaped(["###", "###", "###"], {"#": I("endite_ingot")}, I("endite_block"), category="building"))
    recipe("endite_ingot_from_block", {"type": "minecraft:crafting_shapeless", "ingredients": [I("endite_block")], "result": {"count": 9, "id": I("endite_ingot")}})
    recipe("endite_ingot", {"type": "minecraft:crafting_shapeless", "group": "endite_ingot",
                            "ingredients": [I("endite_shard")] * 4 + ["minecraft:netherite_ingot"] + ["minecraft:popped_chorus_fruit"] * 4,
                            "result": {"id": I("endite_ingot")}})
    recipe("endite_upgrade_smithing_template", shaped(["#S#", "#C#", "###"], {"#": I("endite_shard"), "C": "minecraft:end_stone",
                                                                             "S": I("endite_upgrade_smithing_template")}, I("endite_upgrade_smithing_template"), count=2))
    recipe("ruby_bricks", shaped(["##", "##"], {"#": I("ruby_block")}, I("ruby_bricks"), count=4, category="building"))
    recipe("ruby_brick_stairs", shaped(["#  ", "## ", "###"], {"#": I("ruby_bricks")}, I("ruby_brick_stairs"), count=4, category="building"))
    recipe("ruby_brick_slab", shaped(["###"], {"#": I("ruby_bricks")}, I("ruby_brick_slab"), count=6, category="building"))
    recipe("ruby_lamp", shaped([" R ", "RGR", " R "], {"R": I("ruby"), "G": "minecraft:glowstone"}, I("ruby_lamp"), category="building"))
    recipe("endite_plating", shaped(["##", "##"], {"#": I("endite_block")}, I("endite_plating"), count=4, category="building"))
    recipe("endite_plating_stairs", shaped(["#  ", "## ", "###"], {"#": I("endite_plating")}, I("endite_plating_stairs"), count=4, category="building"))
    recipe("endite_plating_slab", shaped(["###"], {"#": I("endite_plating")}, I("endite_plating_slab"), count=6, category="building"))
    for ore in ("ruby_ore", "deepslate_ruby_ore"):
        for kind, time in (("smelting", 200), ("blasting", 100)):
            recipe(f"ruby_from_{kind}_{ore}", {"type": f"minecraft:{kind}", "cookingtime": time, "experience": 1.0, "group": "ruby",
                                               "ingredient": I(ore), "result": {"id": I("ruby")}})
    for kind, time in (("smelting", 200), ("blasting", 100)):
        recipe(f"endite_shard_from_{kind}", {"type": f"minecraft:{kind}", "cookingtime": time, "experience": 2.0, "ingredient": I("endite_ore"),
                                             "result": {"id": I("endite_shard")}})

    # Loot tables (26.3 format)
    lt = f"{DATA}/{NS}/loot_table/blocks"
    for ore in ("ruby_ore", "deepslate_ruby_ore"):
        write(f"{lt}/{ore}.json", {"type": "minecraft:block", "pools": [{"entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": I(ore)},
            {"type": "minecraft:item", "modifier": [{"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                                                    {"type": "minecraft:explosion_decay"}], "name": I("ruby")}]}], "rolls": 1}],
            "random_sequence": f"{NS}:blocks/{ore}"})
    for b in ("ruby_block", "ruby_bricks", "ruby_lamp", "endite_ore", "endite_block", "endite_plating") + tuple(STAIRS):
        write(f"{lt}/{b}.json", {"type": "minecraft:block", "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                                                                       "entries": [{"type": "minecraft:item", "name": I(b)}], "rolls": 1}],
                                 "random_sequence": f"{NS}:blocks/{b}"})
    for s in SLABS:
        write(f"{lt}/{s}.json", {"type": "minecraft:block", "pools": [{"entries": [{"type": "minecraft:item", "modifier": [
            {"type": "minecraft:set_count", "condition": {"type": "minecraft:match_block", "blocks": I(s), "state": {"type": "double"}}, "count": 2},
            {"type": "minecraft:explosion_decay"}], "name": I(s)}], "rolls": 1}], "random_sequence": f"{NS}:blocks/{s}"})

    # Tags
    tag = lambda kind, ns, name, values: write(f"{DATA}/{ns}/tags/{kind}/{name}.json", {"replace": False, "values": values})
    all_blocks = [I(b) for b in SIMPLE_BLOCKS + list(STAIRS) + list(SLABS)]
    tag("block", "minecraft", "mineable/pickaxe", all_blocks)
    tag("block", "minecraft", "needs_diamond_tool", [I("ruby_ore"), I("deepslate_ruby_ore"), I("ruby_block")])
    tag("block", "minecraft", "needs_iron_tool", [I("ruby_bricks"), I("ruby_brick_stairs"), I("ruby_brick_slab"), I("ruby_lamp")])
    tag("block", NS, "needs_netherite_tool", [I("endite_ore"), I("endite_block")])
    tag("block", NS, "needs_ruby_tool", ["minecraft:ancient_debris", "minecraft:netherite_block"])
    # Diamond can't mine what needs ruby; nothing below netherite can mine what needs netherite
    tag("block", "minecraft", "incorrect_for_diamond_tool", [f"#{NS}:needs_ruby_tool", f"#{NS}:needs_netherite_tool"])
    for lower in ("wooden", "stone", "copper", "iron", "gold"):
        tag("block", "minecraft", f"incorrect_for_{lower}_tool", [f"#{NS}:needs_ruby_tool", f"#{NS}:needs_netherite_tool"])
    tag("block", NS, "incorrect_for_ruby_tool", [f"#{NS}:needs_netherite_tool"])
    tag("block", NS, "incorrect_for_endite_tool", [])
    tag("item", NS, "ruby_tool_materials", [I("ruby")])
    tag("item", NS, "endite_tool_materials", [I("endite_ingot")])
    tag("item", NS, "repairs_ruby_armor", [I("ruby")])
    tag("item", NS, "repairs_endite_armor", [I("endite_ingot")])
    for t, vt in (("sword", "swords"), ("pickaxe", "pickaxes"), ("axe", "axes"), ("shovel", "shovels"), ("hoe", "hoes")):
        tag("item", "minecraft", vt, [I(f"ruby_{t}"), I(f"endite_{t}")])
    for a, vt in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        tag("item", "minecraft", vt, [I(f"ruby_{a}"), I(f"endite_{a}")])
    tag("item", "minecraft", "trimmable_armor", [I(f"{m}_{a}") for m in ("ruby", "endite") for a in ARMOR])
    tag("item", "minecraft", "beacon_payment_items", [I("ruby"), I("endite_ingot")])
    tag("block", "minecraft", "beacon_base_blocks", [I("ruby_block"), I("endite_block")])
    tag("item", "minecraft", "stairs", [I(s) for s in STAIRS])
    tag("item", "minecraft", "slabs", [I(s) for s in SLABS])
    tag("block", "minecraft", "stairs", [I(s) for s in STAIRS])
    tag("block", "minecraft", "slabs", [I(s) for s in SLABS])

    # Worldgen (26.3 calls configured features just "feature")
    wg = f"{DATA}/{NS}/worldgen"
    ore_target = lambda tag_name: {"predicate_type": "minecraft:tag_match", "tag": tag_name}
    write(f"{wg}/feature/ore_ruby.json", {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.3, "size": 5, "targets": [
        {"state": I("ruby_ore"), "target": ore_target("minecraft:stone_ore_replaceables")},
        {"state": I("deepslate_ruby_ore"), "target": ore_target("minecraft:deepslate_ore_replaceables")}]})
    write(f"{wg}/placed_feature/ore_ruby.json", {"feature": I("ore_ruby"), "placement": [
        {"type": "minecraft:count", "count": 5}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "max_inclusive": {"absolute": 16}, "min_inclusive": {"above_bottom": -24}}},
        {"type": "minecraft:biome"}]})
    write(f"{wg}/feature/ore_endite.json", {"type": "minecraft:scattered_ore", "discard_chance_on_air_exposure": 0.8, "size": 3, "targets": [
        {"state": I("endite_ore"), "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"}}]})
    write(f"{wg}/placed_feature/ore_endite.json", {"feature": I("ore_endite"), "placement": [
        {"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": 90}, "min_inclusive": {"absolute": 10}}},
        {"type": "minecraft:biome"}]})


if __name__ == "__main__":
    textures()
    client_assets()
    data()
    print("done")

#!/usr/bin/env python3
"""Generates the AbpsMod enchantment definitions, tags and names.

The JSON only describes each enchantment (levels, cost, which items take it). What it actually does is
implemented in dev.abps.content.Enchants, so a bad effect definition can never stop a world from loading.

Run from the repo root: python3 tools/gen_enchants.py
"""
import json
import os

NS = "abpsmod"
DATA = "src/main/resources/data"
LANG = "src/client/resources/assets/abpsmod/lang/en_us.json"

# id: (name, max level, weight, supported items, primary items or None, slots, treasure, description)
ENCHANTS = {
    "lifesteal": ("Lifesteal", 3, 2, "#minecraft:enchantable/melee_weapon", "#minecraft:enchantable/melee_weapon", ["mainhand"], False,
                  "Melee hits heal you for part of the damage dealt."),
    "executioner": ("Executioner", 3, 3, "#minecraft:enchantable/melee_weapon", "#minecraft:enchantable/melee_weapon", ["mainhand"], False,
                    "More damage to enemies below 30% health."),
    "venom": ("Venom", 2, 3, "#minecraft:enchantable/melee_weapon", "#minecraft:enchantable/melee_weapon", ["mainhand"], False,
              "Melee hits poison the target."),
    "frostbite": ("Frostbite", 2, 3, "#minecraft:enchantable/melee_weapon", "#minecraft:enchantable/melee_weapon", ["mainhand"], False,
                  "Melee hits slow the target."),
    "thunderstrike": ("Thunderstrike", 2, 1, "#minecraft:enchantable/melee_weapon", "#minecraft:enchantable/melee_weapon", ["mainhand"], False,
                      "Melee hits sometimes call down lightning."),
    "dodge": ("Dodge", 3, 2, "#abpsmod:enchantable/dodge", "#abpsmod:enchantable/dodge", ["chest", "legs"], False,
              "A chance to avoid an attack completely."),
    "vein_miner": ("Vein Miner", 1, 1, "#minecraft:pickaxes", None, ["mainhand"], True,
                   "Mines a whole ore vein at once. Sneak to mine one block."),
    "timber": ("Timber", 1, 2, "#minecraft:axes", "#minecraft:axes", ["mainhand"], False,
               "Chops down a whole tree at once. Sneak to chop one log."),
    "excavator": ("Excavator", 2, 1, "#abpsmod:enchantable/excavator", "#abpsmod:enchantable/excavator", ["mainhand"], False,
                  "Digs 3x3 (two deep at level II). Sneak to dig one block."),
    "smelting_touch": ("Smelting Touch", 1, 2, "#abpsmod:enchantable/smelting", "#abpsmod:enchantable/smelting", ["mainhand"], False,
                       "Blocks drop what they smelt into."),
    "magnetic": ("Magnetic", 1, 3, "#minecraft:enchantable/mining", "#minecraft:enchantable/mining", ["mainhand"], False,
                 "Block drops go straight into your inventory."),
    "replanting": ("Replanting", 1, 3, "#minecraft:hoes", "#minecraft:hoes", ["mainhand"], False,
                   "Harvested crops replant themselves."),
    "explosive_shot": ("Explosive Shot", 2, 2, "#abpsmod:enchantable/ranged", "#abpsmod:enchantable/ranged", ["mainhand", "offhand"], False,
                       "Arrows burst on impact, hurting nearby enemies (never blocks)."),
    "homing": ("Homing", 1, 1, "#minecraft:enchantable/bow", None, ["mainhand", "offhand"], True,
               "Arrows curve toward the nearest enemy."),
    "leaping": ("Leaping", 3, 3, "#minecraft:enchantable/foot_armor", "#minecraft:enchantable/foot_armor", ["feet"], False,
                "Jump higher and fall further before taking damage."),
    "night_owl": ("Night Owl", 1, 2, "#minecraft:enchantable/head_armor", "#minecraft:enchantable/head_armor", ["head"], False,
                  "Night vision while you're somewhere dark."),
    "second_wind": ("Second Wind", 1, 2, "#minecraft:enchantable/chest_armor", "#minecraft:enchantable/chest_armor", ["chest"], False,
                    "Dropping below 30% health gives a burst of regeneration (2 min cooldown)."),
    "soulbound": ("Soulbound", 1, 1, "#minecraft:enchantable/durability", None, ["any"], True,
                  "You keep this item when you die."),
}

EXCLUSIVE = {  # mutually exclusive groups
    "excavator_vein": ["excavator", "vein_miner"],
    "venom_frost": ["venom", "frostbite"],
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    exclusive_of = {}
    for group, members in EXCLUSIVE.items():
        for m in members:
            exclusive_of[m] = group
        write(f"{DATA}/{NS}/tags/enchantment/exclusive_set/{group}.json", {"values": [f"{NS}:{m}" for m in members]})

    for eid, (name, max_level, weight, supported, primary, slots, treasure, _) in ENCHANTS.items():
        base = 25 if treasure else 10
        e = {
            "anvil_cost": 8 if treasure else {1: 8, 2: 4, 3: 2}.get(weight, 1),
            "description": {"translate": f"enchantment.{NS}.{eid}"},
            "effects": {},
            "max_cost": {"base": base + 50, "per_level_above_first": 10},
            "max_level": max_level,
            "min_cost": {"base": base, "per_level_above_first": 10},
            "slots": slots,
            "supported_items": supported,
            "weight": weight,
        }
        if primary:
            e["primary_items"] = primary
        if eid in exclusive_of:
            e["exclusive_set"] = f"#{NS}:exclusive_set/{exclusive_of[eid]}"
        write(f"{DATA}/{NS}/enchantment/{eid}.json", e)

    write(f"{DATA}/{NS}/tags/item/enchantable/dodge.json", {"values": ["#minecraft:enchantable/chest_armor", "#minecraft:enchantable/leg_armor"]})
    write(f"{DATA}/{NS}/tags/item/enchantable/excavator.json", {"values": ["#minecraft:pickaxes", "#minecraft:shovels"]})
    write(f"{DATA}/{NS}/tags/item/enchantable/smelting.json", {"values": ["#minecraft:pickaxes", "#minecraft:shovels", "#minecraft:axes"]})
    write(f"{DATA}/{NS}/tags/item/enchantable/ranged.json", {"values": ["#minecraft:enchantable/bow", "#minecraft:enchantable/crossbow"]})

    normal = [f"{NS}:{k}" for k, v in ENCHANTS.items() if not v[6]]
    treasure = [f"{NS}:{k}" for k, v in ENCHANTS.items() if v[6]]
    write(f"{DATA}/minecraft/tags/enchantment/non_treasure.json", {"replace": False, "values": normal})
    write(f"{DATA}/minecraft/tags/enchantment/treasure.json", {"replace": False, "values": treasure})
    for t in ("on_random_loot", "tradeable"):
        write(f"{DATA}/minecraft/tags/enchantment/{t}.json", {"replace": False, "values": treasure})

    lang = json.load(open(LANG))
    for eid, v in ENCHANTS.items():
        lang[f"enchantment.{NS}.{eid}"] = v[0]
        lang[f"enchantment.{NS}.{eid}.desc"] = v[7]
    write(LANG, lang)
    print(f"{len(ENCHANTS)} enchantments")


if __name__ == "__main__":
    main()

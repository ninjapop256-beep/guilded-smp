#!/usr/bin/env python3
"""
Writes the resource pack's JSON: pack.mcmeta, item models, item definitions,
sounds.json and subtitles.

How items are skinned (Minecraft 26.2, resource pack format 88):
  The plugin gives each custom item a custom_model_data string such as
  "guildedsmp:inferno_blade". The vanilla item definition of the base item
  (assets/minecraft/items/<base>.json) selects on that string and falls back to
  the normal vanilla model, so ordinary items look unchanged and players without
  the pack simply see the vanilla base item.

Usage: python tools/generate_pack_json.py
"""
import json
import os
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepack")
NS = "guildedsmp"

# id -> (vanilla base item, model parent). Must match CustomItem.java.
ITEMS = OrderedDict([
    ("inferno_blade", ("netherite_sword", "minecraft:item/handheld")),
    ("frostbite", ("netherite_sword", "minecraft:item/handheld")),
    ("void_reaper", ("netherite_sword", "minecraft:item/handheld")),
    ("inferno_core", ("magma_cream", "minecraft:item/generated")),
    ("ember_crystal", ("resin_brick", "minecraft:item/generated")),
    ("frost_core", ("heart_of_the_sea", "minecraft:item/generated")),
    ("frozen_crystal", ("prismarine_crystals", "minecraft:item/generated")),
    ("void_shard", ("echo_shard", "minecraft:item/generated")),
    ("void_heart", ("popped_chorus_fruit", "minecraft:item/generated")),
])

SOUNDS = OrderedDict([
    ("legendary.inferno.ignite", ("legendary/inferno_ignite", "Inferno Blade ignites")),
    ("legendary.frostbite.chill", ("legendary/frostbite_chill", "Frostbite chills")),
    ("legendary.void.drain", ("legendary/void_drain", "Void Reaper drains")),
    ("legendary.void.reap", ("legendary/void_reap", "Void Reaper reaps")),
    ("legendary.forge", ("legendary/forge", "Legendary forged")),
])


def write(rel, data):
    path = os.path.join(PACK, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    write("pack.mcmeta", {
        "pack": {
            "description": [
                {"text": "GUILDED", "color": "#00FFFF", "bold": True},
                {"text": " "},
                # Same brand colours as the TAB list; the light shadow keeps black readable.
                {"text": "SMP", "color": "#000000", "bold": True, "shadow_color": -6250336},
                {"text": "\nLegendary weapons & ingredients", "color": "gray"},
            ],
            "min_format": [88, 0],
            "max_format": 88,
        }
    })

    by_base = OrderedDict()
    for item_id, (base, parent) in ITEMS.items():
        write(f"assets/{NS}/models/item/{item_id}.json", {
            "parent": parent,
            "textures": {"layer0": f"{NS}:item/{item_id}"},
        })
        # Also usable directly via the item_model component (e.g. for previews).
        write(f"assets/{NS}/items/{item_id}.json", {
            "model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"},
        })
        by_base.setdefault(base, []).append(item_id)

    for base, ids in by_base.items():
        write(f"assets/minecraft/items/{base}.json", {
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:custom_model_data",
                "index": 0,
                "cases": [
                    {"when": f"{NS}:{i}", "model": {"type": "minecraft:model", "model": f"{NS}:item/{i}"}}
                    for i in ids
                ],
                "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{base}"},
            }
        })

    sounds = OrderedDict()
    lang = OrderedDict()
    for event, (file, subtitle) in SOUNDS.items():
        key = f"subtitles.{NS}.{event}"
        sounds[event] = {"subtitle": key, "sounds": [{"name": f"{NS}:{file}"}]}
        lang[key] = subtitle
    write(f"assets/{NS}/sounds.json", sounds)
    write(f"assets/{NS}/lang/en_us.json", lang)
    print("Resource pack JSON written to", PACK)


if __name__ == "__main__":
    main()

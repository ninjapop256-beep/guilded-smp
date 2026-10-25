#!/usr/bin/env python3
"""
Generates every Guilded SMP texture (original pixel art, made for this project).

Each sprite is either a hand-placed 16x16 pixel grid or a small procedural shape
(orbs, heart). Outlines are added automatically around filled pixels.

Usage:  python tools/generate_textures.py [--preview PATH]
Writes: resourcepack/assets/guildedsmp/textures/item/*.png (+ .png.mcmeta for animations)
        resourcepack/pack.png
"""
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "resourcepack", "assets", "guildedsmp", "textures", "item")


def c(hex_code, alpha=255):
    h = hex_code.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def from_grid(grid, palette, outline):
    assert len(grid) == 16, f"grid has {len(grid)} rows"
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    filled = set()
    for y, row in enumerate(grid):
        assert len(row) == 16, f"row {y} has length {len(row)}: {row!r}"
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
                filled.add((x, y))
    add_outline(img, filled, outline)
    return img


def add_outline(img, filled, outline):
    if outline is None:
        return
    for y in range(16):
        for x in range(16):
            if (x, y) in filled:
                continue
            if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), outline)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def ramp(stops, t):
    """stops: list of (position, color)."""
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        if t <= p1:
            return lerp(c0, c1, (t - p0) / (p1 - p0) if p1 > p0 else 0)
    return stops[-1][1]


# ----------------------------------------------------------------------------- swords

INFERNO_BLADE = [
    "................",
    "..............W.",
    ".............WY.",
    "............YOR.",
    "..........OYOR..",
    "..........YOR...",
    "........YYOD....",
    "...g....ORD.....",
    "...Gg.OORD......",
    "....GGRRD.......",
    ".....eED........",
    "....HhGG........",
    "...hH..Gg.......",
    "..PH.....g......",
    ".Pp.............",
    "................",
]
INFERNO_PAL = {
    "W": c("#FFF4C2"), "Y": c("#FFD23F"), "O": c("#FF8C1A"), "R": c("#E0461A"), "D": c("#9E2410"),
    "G": c("#4A3B36"), "g": c("#7A6158"), "E": c("#FF3300"), "e": c("#FFC14D"),
    "H": c("#3A2116"), "h": c("#B3541E"), "P": c("#5C463E"), "p": c("#FF7A21"),
}

FROSTBITE = [
    "................",
    "..............W.",
    ".............WI.",
    "............ICB.",
    "...........ICBC.",
    ".........WICB...",
    "..I......ICN....",
    "...g....ICBC....",
    "...Gg.IICN......",
    "....GGCBN.......",
    ".....eEN........",
    "....HhGG........",
    "...hH..Gg.......",
    "..PH.....g......",
    ".pP.......I.....",
    "................",
]
FROST_PAL = {
    "W": c("#FFFFFF"), "I": c("#DFF8FF"), "C": c("#9BE7FF"), "B": c("#4FB8E6"), "N": c("#2A6F9E"),
    "G": c("#5A6E7A"), "g": c("#A9C4D1"), "E": c("#7FE9FF"), "e": c("#E8FDFF"),
    "H": c("#1E3448"), "h": c("#6FB7D6"), "P": c("#3D5566"), "p": c("#BFF4FF"),
}

VOID_REAPER = [
    "................",
    "...........MMm..",
    ".............MS.",
    "............MvV.",
    "...........MvVV.",
    "..........MmV...",
    "..g......MvVV...",
    "...G....MvV.....",
    "...GG..MmVV.....",
    "....GGMvV.......",
    ".....eEV........",
    "....HhGG........",
    "...hH..GG.......",
    "..PH.....g......",
    ".Pp.............",
    "................",
]
VOID_PAL = {
    "m": c("#F2C9FF"), "M": c("#B44DFF"), "S": c("#7B3AAE"), "v": c("#4A1C66"), "V": c("#26093A"),
    "G": c("#2A1C33"), "g": c("#5B4769"), "E": c("#C04BFF"), "e": c("#FFD6FF"),
    "H": c("#160C1C"), "h": c("#6A2C91"), "P": c("#2E2038"), "p": c("#C04BFF"),
}

# ----------------------------------------------------------------------------- ingredient grids

EMBER_CRYSTAL = [
    "................",
    ".......W........",
    "......YWO.......",
    ".....YYWOR......",
    ".....YYOOR......",
    "....YYYOORD.....",
    "....YYOOORD.....",
    "....YYOWORD.....",
    ".W..YYOOORD.....",
    ".YO.YYOOORD.....",
    ".YO.YYOOORD.....",
    ".OR..YOORD......",
    "..D..YOORD......",
    "......ORD.......",
    ".......D........",
    "................",
]
EMBER_PAL = {
    "W": c("#FFF8DC"), "Y": c("#FFE38A"), "O": c("#FFB347"), "R": c("#F07A1A"), "D": c("#A8460F"),
}

FROZEN_CRYSTAL = [
    "................",
    ".........W......",
    "........WI......",
    "........ICB.....",
    "...W....ICB.....",
    "...IC...ICB.....",
    "...ICB..ICBN....",
    "...ICB.IICBN....",
    "....CB.ICCBN.W..",
    "....CBNICCBNIC..",
    "....CBNICBNICB..",
    ".....BNICBNCBN..",
    ".....NNCBBNBN...",
    "......NNBNNN....",
    "................",
    "................",
]
FROZEN_PAL = {
    "W": c("#FFFFFF"), "I": c("#E6FBFF"), "C": c("#A8EEFF"), "B": c("#66CCF0"), "N": c("#2F86B8"),
}

VOID_SHARD = [
    "................",
    "...........m....",
    "..........mM....",
    "...s.....mMS....",
    "........mMSv....",
    ".......mMSSvV...",
    "......mMMSv.V...",
    ".....mMSSvvVV...",
    "....mMSSvvVV..s.",
    "...mMSSvvVV.....",
    "...MSSvvVVV.....",
    "..MSSvvVV..MS...",
    "..SSvVVV...SV...",
    "...vVV......V...",
    "................",
    "................",
]
SHARD_PAL = {
    "m": c("#F0C8FF"), "M": c("#C46BFF"), "S": c("#8A3FD1"), "v": c("#5A2391"), "V": c("#2E0F4F"),
    "s": c("#FFFFFF"),
}


# ----------------------------------------------------------------------------- procedural sprites

def orb(stops, outline, cx=7.5, cy=8.0, r=5.6, glow=0.0, highlight=None, extra=None):
    """A shaded sphere. stops map 0 (centre) -> 1 (rim); glow brightens the centre."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    filled = set()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
            if d <= 1.0:
                # light comes from the upper-left
                lx = (x + 0.5 - (cx - r * 0.35)) / r
                ly = (y + 0.5 - (cy - r * 0.35)) / r
                shade = min(1.0, 0.55 * d + 0.45 * math.hypot(lx, ly) * 0.8)
                shade = max(0.0, shade - glow)
                img.putpixel((x, y), ramp(stops, shade))
                filled.add((x, y))
    if highlight:
        for (hx, hy), col in highlight:
            img.putpixel((hx, hy), col)
    if extra:
        for (ex, ey), col in extra:
            img.putpixel((ex, ey), col)
            filled.add((ex, ey))
    add_outline(img, filled, outline)
    return img


def inferno_core_frame(phase):
    stops = [(0.0, c("#FFF6C8")), (0.25, c("#FFD23F")), (0.55, c("#FF8C1A")), (0.8, c("#E0461A")), (1.0, c("#8F1D0A"))]
    glow = 0.12 + 0.12 * math.sin(phase * 2 * math.pi)
    flicker = int(phase * 4) % 4
    flames = {
        0: [((6, 1), c("#FFD23F")), ((6, 2), c("#FF8C1A")), ((9, 0), c("#FFF4C2")), ((9, 1), c("#FFD23F")), ((9, 2), c("#FF8C1A"))],
        1: [((7, 1), c("#FFD23F")), ((7, 2), c("#FF8C1A")), ((10, 1), c("#FFD23F")), ((10, 2), c("#FF8C1A"))],
        2: [((5, 2), c("#FF8C1A")), ((8, 0), c("#FFF4C2")), ((8, 1), c("#FFD23F")), ((8, 2), c("#FF8C1A"))],
        3: [((6, 2), c("#FF8C1A")), ((9, 1), c("#FFD23F")), ((9, 2), c("#FF8C1A")), ((11, 2), c("#FFD23F"))],
    }[flicker]
    highlight = [((5, 5), c("#FFFFFF")), ((6, 5), c("#FFF6C8")), ((5, 6), c("#FFF6C8"))]
    return orb(stops, c("#3A0D04"), cy=8.6, r=5.4, glow=glow, highlight=highlight, extra=flames)


def frost_core_frame(phase):
    stops = [(0.0, c("#FFFFFF")), (0.3, c("#C8F4FF")), (0.6, c("#6FD3F5")), (0.85, c("#2F86B8")), (1.0, c("#1B4F75"))]
    glow = 0.08 + 0.08 * math.sin(phase * 2 * math.pi)
    img = orb(stops, c("#0D2436"), glow=glow, highlight=[((5, 4), c("#FFFFFF")), ((5, 5), c("#FFFFFF"))])
    flake = c("#FFFFFF", 235)
    pts = [(8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (6, 8), (7, 8), (9, 8), (10, 8), (6, 6), (10, 10), (6, 10), (10, 6)]
    if int(phase * 4) % 2 == 1:
        pts += [(7, 7), (9, 9), (7, 9), (9, 7)]
    for p in pts:
        img.putpixel(p, flake)
    return img


VOID_HEART = [
    "................",
    "................",
    "..vvvv....vvvv..",
    ".vmmvvv..vvvvVv.",
    ".vmvvvvvvvvvvVV.",
    ".vvvvvvvvvvvvVV.",
    ".vvvvvvvvvvvVVV.",
    "..vvvvvvvvvvVV..",
    "...vvvvvvvvVV...",
    "....vvvvvvVV....",
    ".....vvvvVV.....",
    "......vvVV......",
    ".......VV.......",
    "................",
    "................",
    "................",
]


def void_heart_frame(phase):
    pulse = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cells = set()
    base = {"v": c("#3B0F5C"), "V": c("#22082F"), "m": c("#7A3AA8")}
    for y, row in enumerate(VOID_HEART):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), base[ch])
                cells.add((x, y))
    vein = lerp(c("#7A26B8"), c("#E3A8FF"), pulse)
    core = lerp(c("#C04BFF"), c("#FFFFFF"), pulse)
    veins = [(7, 5), (7, 6), (8, 7), (6, 4), (5, 5), (9, 6), (10, 5), (11, 4), (8, 8), (7, 9), (8, 10), (9, 9), (6, 8)]
    for p in veins:
        if p in cells:
            img.putpixel(p, vein)
    for p in [(7, 7), (8, 7), (7, 8)]:
        if p in cells:
            img.putpixel(p, core)
    add_outline(img, cells, c("#0E0216"))
    return img


def animated(frames_fn, count):
    sheet = Image.new("RGBA", (16, 16 * count), (0, 0, 0, 0))
    for i in range(count):
        sheet.paste(frames_fn(i / count), (0, 16 * i))
    return sheet


# ----------------------------------------------------------------------------- pack icon

G_GLYPH = [
    "..XXXXXX..",
    ".XXXXXXXX.",
    "XXX....XXX",
    "XX........",
    "XX........",
    "XX...XXXXX",
    "XX...XXXXX",
    "XX.....XXX",
    "XXX....XXX",
    ".XXXXXXXX.",
    "..XXXXXX..",
]


def pack_icon():
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    top, bottom = c("#0E2A30"), c("#04090B")
    for y in range(size):
        for x in range(size):
            inset = min(x, y, size - 1 - x, size - 1 - y)
            corner = (x < 2 or x > size - 3) and (y < 2 or y > size - 3)
            if corner and inset == 0:
                continue
            col = lerp(top, bottom, y / (size - 1))
            if inset == 0 or (inset == 1 and corner):
                col = c("#00FFFF")
            elif inset == 1:
                col = c("#007A80")
            img.putpixel((x, y), col)
    ox, oy = 11, 5
    for y, row in enumerate(G_GLYPH):
        for x, ch in enumerate(row):
            if ch == "X":
                img.putpixel((ox + x, oy + y), c("#00FFFF"))
                # 1px drop shadow
                sx, sy = ox + x + 1, oy + y + 1
                if img.getpixel((sx, sy))[:3] != (0, 255, 255):
                    img.putpixel((sx, sy), c("#003F44"))
    for i, col in enumerate([c("#FF6A1F"), c("#7FE3FF"), c("#A45CFF")]):
        cx = 10 + i * 6
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (0, -1), (1, -1), (-1, 0), (2, 0)):
            if dy == -1 or dx in (-1, 2):
                if dy != 0 and dx in (-1, 2):
                    continue
            img.putpixel((cx + dx, 22 + dy), col)
        img.putpixel((cx, 21), lerp(col, c("#FFFFFF"), 0.6))
    for x in range(8, 24):
        img.putpixel((x, 26), c("#00FFFF", 140))
    return img.resize((128, 128), Image.NEAREST)


# ----------------------------------------------------------------------------- main

def build():
    sprites = {
        "inferno_blade": (from_grid(INFERNO_BLADE, INFERNO_PAL, c("#2A0A05")), None),
        "frostbite": (from_grid(FROSTBITE, FROST_PAL, c("#0D2436")), None),
        "void_reaper": (from_grid(VOID_REAPER, VOID_PAL, c("#0E0216")), None),
        "inferno_core": (animated(inferno_core_frame, 8), {"animation": {"frametime": 3, "interpolate": True}}),
        "ember_crystal": (from_grid(EMBER_CRYSTAL, EMBER_PAL, c("#4A1A05")), None),
        "frost_core": (animated(frost_core_frame, 8), {"animation": {"frametime": 4, "interpolate": True}}),
        "frozen_crystal": (from_grid(FROZEN_CRYSTAL, FROZEN_PAL, c("#0D2436")), None),
        "void_shard": (from_grid(VOID_SHARD, SHARD_PAL, c("#14031F")), None),
        "void_heart": (animated(void_heart_frame, 10), {"animation": {"frametime": 2, "interpolate": True}}),
    }
    return sprites


def write_all():
    os.makedirs(TEX, exist_ok=True)
    for name, (img, meta) in build().items():
        img.save(os.path.join(TEX, name + ".png"), optimize=True)
        mcmeta = os.path.join(TEX, name + ".png.mcmeta")
        if meta:
            with open(mcmeta, "w", encoding="utf-8", newline="\n") as f:
                json.dump(meta, f, indent=2)
                f.write("\n")
        elif os.path.exists(mcmeta):
            os.remove(mcmeta)
    pack_icon().save(os.path.join(ROOT, "resourcepack", "pack.png"), optimize=True)


def preview(path):
    """A zoomed contact sheet (first frame of animations) on a checkerboard, for review."""
    sprites = build()
    scale = 12
    cell = 16 * scale + 16
    sheet = Image.new("RGBA", (cell * len(sprites) + 16, cell + 16 + 128 + 16), c("#2B2B2B"))
    for i, (name, (img, _)) in enumerate(sprites.items()):
        frame = img.crop((0, 0, 16, 16)).resize((16 * scale, 16 * scale), Image.NEAREST)
        bg = Image.new("RGBA", frame.size, c("#3C3C3C"))
        for y in range(16):
            for x in range(16):
                if (x + y) % 2 == 0:
                    for yy in range(scale):
                        for xx in range(scale):
                            bg.putpixel((x * scale + xx, y * scale + yy), c("#474747"))
        bg.alpha_composite(frame)
        sheet.paste(bg, (16 + i * cell, 16))
    sheet.paste(pack_icon(), (16, cell + 16))
    sheet.save(path)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
    else:
        write_all()
        print("Textures written to", TEX)

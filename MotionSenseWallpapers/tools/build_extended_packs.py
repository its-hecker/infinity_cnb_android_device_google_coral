#!/usr/bin/env python3
"""
Build the Sidekick asset set used by the unified PixelLiveWallpaper payload.

- Copies the eight original faceid Sidekick Lottie files.
- Adds semantic interaction markers used by the enhanced engine.
- Adds subtle depth lighting to the custom character scenes.
- Generates four optional fan-art character packs requested for the faceid build.
- Never edits Google's Pokemon Unity assets; those are handled separately by the
  vendor unification pipeline so Pokemon character artwork remains byte-for-byte intact.
"""

from __future__ import annotations

import argparse
import copy
import json
import math
from pathlib import Path

W, H, FPS, END = 432, 936, 60, 870

CORE = [
    "hecker", "mochi", "biscuit", "whisk_crumb",
    "pip", "drift", "orbit", "aurora",
]

GLOWS = {
    "hecker": "#8b7cf6",
    "mochi": "#b8a7ff",
    "biscuit": "#ffb45a",
    "whisk_crumb": "#ffca8c",
    "pip": "#57e4ff",
    "drift": "#70d8ff",
    "orbit": "#a892ff",
    "aurora": "#80e8d6",
}

PORTRAIT_LIGHTS = {
    "hecker": (216, 525, 250, 330),
    "mochi": (216, 535, 260, 345),
    "biscuit": (216, 555, 270, 330),
    "whisk_crumb": (220, 575, 300, 310),
    "pip": (216, 515, 235, 300),
}

BASE_MARKERS = [
    ("idle", 0, 240),
    ("wake", 240, 90),
    ("wave", 330, 120),
    ("left", 450, 60),
    ("right", 510, 60),
    ("nod", 570, 60),
    ("sleep", 630, 60),
    ("asleep", 690, 120),
    ("wakeup", 810, 60),
]

ALIASES = {
    "tap": "wave",
    "pet": "nod",
    "special": "wave",
    "up": "wake",
    "look_left": "left",
    "look_right": "right",
}


def rgb(hex_color: str):
    h = hex_color.lstrip("#")
    return [int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)] + [1.0]


def tr(pos=(0, 0), scale=(100, 100), rotation=0, opacity=100):
    return {
        "ty": "tr",
        "p": {"a": 0, "k": list(pos)},
        "a": {"a": 0, "k": [0, 0]},
        "s": {"a": 0, "k": list(scale)},
        "r": {"a": 0, "k": rotation},
        "o": {"a": 0, "k": opacity},
        "sk": {"a": 0, "k": 0},
        "sa": {"a": 0, "k": 0},
    }


def fill(color, opacity=100):
    return {
        "ty": "fl", "c": {"a": 0, "k": rgb(color)},
        "o": {"a": 0, "k": opacity}, "r": 1,
    }


def stroke(color, width=4, opacity=100):
    return {
        "ty": "st", "c": {"a": 0, "k": rgb(color)},
        "o": {"a": 0, "k": opacity}, "w": {"a": 0, "k": width},
        "lc": 2, "lj": 2,
    }


def ellipse(name, x, y, w, h, color, opacity=100, stroke_color=None, stroke_width=3):
    items = [
        {"ty": "el", "d": 1, "s": {"a": 0, "k": [w, h]},
         "p": {"a": 0, "k": [0, 0]}, "nm": name + " Path"},
        fill(color, opacity),
    ]
    if stroke_color:
        items.append(stroke(stroke_color, stroke_width))
    items.append(tr((x, y)))
    return {"ty": "gr", "nm": name, "it": items}


def rect(name, x, y, w, h, radius, color, opacity=100, stroke_color=None, stroke_width=3):
    items = [
        {"ty": "rc", "d": 1, "s": {"a": 0, "k": [w, h]},
         "p": {"a": 0, "k": [0, 0]}, "r": {"a": 0, "k": radius}, "nm": name + " Path"},
        fill(color, opacity),
    ]
    if stroke_color:
        items.append(stroke(stroke_color, stroke_width))
    items.append(tr((x, y)))
    return {"ty": "gr", "nm": name, "it": items}


def polygon(name, vertices, color, opacity=100, stroke_color=None, stroke_width=3):
    path = {
        "i": [[0, 0] for _ in vertices],
        "o": [[0, 0] for _ in vertices],
        "v": [list(v) for v in vertices],
        "c": True,
    }
    items = [
        {"ty": "sh", "ks": {"a": 0, "k": path}, "nm": name + " Path"},
        fill(color, opacity),
    ]
    if stroke_color:
        items.append(stroke(stroke_color, stroke_width))
    items.append(tr())
    return {"ty": "gr", "nm": name, "it": items}


def shape_layer(name, ind, shapes, opacity=100, animated=False, blend=0):
    if animated:
        ks = animated_transform()
    else:
        ks = {
            "o": {"a": 0, "k": opacity},
            "r": {"a": 0, "k": 0},
            "p": {"a": 0, "k": [0, 0, 0]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        }
    return {
        "ddd": 0, "ind": ind, "ty": 4, "nm": name, "sr": 1,
        "ks": ks, "ao": 0, "shapes": shapes,
        "ip": 0, "op": END, "st": 0, "bm": blend,
    }


def animated_transform():
    # Whole-character motion is deliberately subtle. Marker-specific renderer
    # dynamics add the stronger touch/Motion Sense response on top.
    return {
        "o": {"a": 0, "k": 100},
        "a": {"a": 0, "k": [216, 620, 0]},
        "p": {"a": 1, "k": [
            {"t": 0, "s": [216, 620, 0]},
            {"t": 120, "s": [216, 613, 0]},
            {"t": 240, "s": [216, 620, 0]},
            {"t": 270, "s": [216, 600, 0]},
            {"t": 330, "s": [216, 620, 0]},
            {"t": 390, "s": [225, 612, 0]},
            {"t": 450, "s": [216, 620, 0]},
            {"t": 480, "s": [198, 620, 0]},
            {"t": 510, "s": [216, 620, 0]},
            {"t": 540, "s": [234, 620, 0]},
            {"t": 570, "s": [216, 620, 0]},
            {"t": 600, "s": [216, 628, 0]},
            {"t": 630, "s": [216, 620, 0]},
            {"t": 675, "s": [216, 650, 0]},
            {"t": 690, "s": [216, 654, 0]},
            {"t": 810, "s": [216, 654, 0]},
            {"t": 850, "s": [216, 614, 0]},
            {"t": 870, "s": [216, 620, 0]},
        ]},
        "s": {"a": 1, "k": [
            {"t": 0, "s": [100, 100, 100]},
            {"t": 120, "s": [100.8, 101.2, 100]},
            {"t": 240, "s": [100, 100, 100]},
            {"t": 270, "s": [104, 104, 100]},
            {"t": 330, "s": [100, 100, 100]},
            {"t": 630, "s": [100, 100, 100]},
            {"t": 690, "s": [97, 97, 100]},
            {"t": 810, "s": [97, 97, 100]},
            {"t": 850, "s": [104, 104, 100]},
            {"t": 870, "s": [100, 100, 100]},
        ]},
        "r": {"a": 1, "k": [
            {"t": 0, "s": [0]},
            {"t": 330, "s": [0]},
            {"t": 360, "s": [-4]},
            {"t": 390, "s": [5]},
            {"t": 420, "s": [-3]},
            {"t": 450, "s": [0]},
            {"t": 570, "s": [0]},
            {"t": 600, "s": [3]},
            {"t": 630, "s": [0]},
            {"t": 870, "s": [0]},
        ]},
    }


def solid(name, ind, color):
    return {
        "ddd": 0, "ind": ind, "ty": 1, "nm": name, "sr": 1,
        "ks": {
            "o": {"a": 0, "k": 100},
            "r": {"a": 0, "k": 0},
            "p": {"a": 0, "k": [W / 2, H / 2, 0]},
            "a": {"a": 0, "k": [W / 2, H / 2, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        },
        "ao": 0, "sw": W, "sh": H, "sc": color,
        "ip": 0, "op": END, "st": 0, "bm": 0,
    }


def markers():
    out = [{"cm": n, "tm": t, "dr": d} for n, t, d in BASE_MARKERS]
    lookup = {n: (t, d) for n, t, d in BASE_MARKERS}
    for alias, source in ALIASES.items():
        t, d = lookup[source]
        out.append({"cm": alias, "tm": t, "dr": d})
    return out


def add_semantic_markers(doc):
    existing = {m.get("cm") for m in doc.get("markers", [])}
    base = {m["cm"]: m for m in doc.get("markers", [])}
    for alias, source in ALIASES.items():
        if alias in existing:
            continue
        src = base.get(source)
        if src:
            doc.setdefault("markers", []).append(
                {"cm": alias, "tm": src["tm"], "dr": src["dr"]}
            )


def add_depth_lighting(doc, key):
    # Add only light-weight overlays. They sit above the existing vector scene and
    # make the subject read less flat without redrawing its original geometry.
    max_ind = max((int(l.get("ind", 0)) for l in doc.get("layers", [])), default=0)
    color = GLOWS.get(key, "#8b7cf6")
    layers = []

    # Soft floor/contact shadow.
    layers.append(shape_layer(
        "sidekick-depth-shadow", max_ind + 1,
        [ellipse("contact shadow", 216, 790, 235, 48, "#05060a", 26)],
        opacity=100,
    ))

    if key in PORTRAIT_LIGHTS:
        x, y, w, h = PORTRAIT_LIGHTS[key]
        # Low-alpha key and rim lights add a small sense of volume.
        layers.append(shape_layer(
            "sidekick-soft-key-light", max_ind + 2,
            [ellipse("key light", x - w * .18, y - h * .20,
                     w * .62, h * .62, "#ffffff", 7)],
            opacity=100, blend=1,
        ))
        layers.append(shape_layer(
            "sidekick-rim-light", max_ind + 3,
            [ellipse("rim light", x + w * .28, y - h * .05,
                     w * .30, h * .76, color, 7)],
            opacity=100, blend=1,
        ))

    # Sparse atmosphere. It is intentionally subtle so it does not look like a
    # particle filter pasted on top of the character.
    motes = []
    for i in range(7):
        x = 32 + ((i * 79) % 360)
        y = 120 + ((i * 137) % 650)
        motes.append(ellipse("mote%d" % i, x, y, 3 + i % 3, 3 + i % 3,
                             color, 10 + (i % 2) * 4))
    layers.append(shape_layer("sidekick-atmosphere", max_ind + 4, motes, blend=1))

    # Insert at the front so lighting is visible, but keep original artwork unchanged.
    doc["layers"] = layers + doc.get("layers", [])


def write_core(source_raw: Path, out: Path):
    for key in CORE:
        src = source_raw / ("wallpaper_%s.json" % key)
        doc = json.loads(src.read_text(encoding="utf-8"))
        add_semantic_markers(doc)
        add_depth_lighting(doc, key)
        (out / src.name).write_text(
            json.dumps(doc, separators=(",", ":")), encoding="utf-8"
        )


def common_scene(bg, accent):
    layers = [solid("background", 90, bg)]
    layers.append(shape_layer("ambient glow", 89, [
        ellipse("glow", 110, 230, 420, 420, accent, 13),
        ellipse("glow2", 350, 650, 500, 500, accent, 8),
    ], blend=1))
    # Floor and distant lights.
    layers.append(shape_layer("depth", 88, [
        ellipse("floor shadow", 216, 825, 320, 60, "#000000", 28),
        ellipse("far light 1", 70, 330, 5, 5, "#ffffff", 38),
        ellipse("far light 2", 355, 235, 4, 4, "#ffffff", 34),
        ellipse("far light 3", 330, 520, 3, 3, "#ffffff", 24),
    ]))
    return layers


def compose(name, bg, accent, scene_shapes, character_shapes, arm_shapes=None):
    layers = common_scene(bg, accent)
    layers.append(shape_layer("environment", 70, scene_shapes))
    layers.append(shape_layer("character", 20, character_shapes, animated=True))
    if arm_shapes:
        arm = shape_layer("character-arm", 19, arm_shapes)
        arm["ks"]["a"] = {"a": 0, "k": [216, 620, 0]}
        arm["ks"]["p"] = {"a": 0, "k": [216, 620, 0]}
        arm["ks"]["r"] = {"a": 1, "k": [
            {"t": 0, "s": [0]}, {"t": 330, "s": [0]},
            {"t": 350, "s": [-20]}, {"t": 370, "s": [18]},
            {"t": 392, "s": [-24]}, {"t": 414, "s": [16]},
            {"t": 440, "s": [0]}, {"t": 870, "s": [0]},
        ]}
        layers.append(arm)
    layers.append(shape_layer("foreground light", 5, [
        ellipse("rim bloom", 300, 420, 210, 420, accent, 6),
    ], blend=1))
    return {
        "v": "5.12.2", "fr": FPS, "ip": 0, "op": END,
        "w": W, "h": H, "nm": name, "ddd": 0,
        "assets": [], "layers": layers, "markers": markers(),
    }


def doraemon():
    scene = [
        rect("floor", 216, 825, 500, 230, 0, "#162b4d"),
        rect("window", 325, 250, 150, 210, 18, "#254a78", 100, "#73c9ff", 4),
        ellipse("moon", 340, 220, 74, 74, "#d9f2ff", 88),
    ]
    char = [
        ellipse("body shadow", 220, 690, 250, 275, "#0b2c52", 55),
        ellipse("body", 216, 650, 245, 290, "#178bd6", 100, "#0a4f86", 5),
        ellipse("belly", 216, 682, 172, 180, "#f7fbff", 100, "#c6dce8", 3),
        ellipse("head shadow", 224, 475, 275, 260, "#0b2c52", 55),
        ellipse("head", 216, 460, 270, 255, "#188fdc", 100, "#0a4f86", 5),
        ellipse("face", 216, 485, 218, 195, "#f9fcff", 100, "#c6dce8", 3),
        ellipse("eye left", 190, 418, 52, 68, "#ffffff", 100, "#203246", 3),
        ellipse("eye right", 242, 418, 52, 68, "#ffffff", 100, "#203246", 3),
        ellipse("pupil left", 198, 430, 12, 19, "#15202c"),
        ellipse("pupil right", 234, 430, 12, 19, "#15202c"),
        ellipse("nose", 216, 475, 31, 31, "#e94a45", 100, "#a92c31", 2),
        rect("collar", 216, 578, 205, 25, 12, "#e74d48", 100, "#a32f34", 2),
        ellipse("bell", 216, 598, 34, 34, "#ffd45c", 100, "#b68a20", 3),
        ellipse("pocket", 216, 690, 100, 70, "#ffffff", 100, "#b9d2df", 3),
        ellipse("highlight", 170, 415, 42, 70, "#ffffff", 20),
    ]
    arm = [
        rect("arm", 306, 623, 40, 120, 20, "#188fdc", 100, "#0a4f86", 4),
        ellipse("hand", 314, 560, 54, 54, "#ffffff", 100, "#c6dce8", 3),
    ]
    return compose("Doraemon Sidekick", "#08182e", "#46b7ff", scene, char, arm)


def ben10():
    scene = [
        rect("ground", 216, 830, 500, 215, 0, "#0b1915"),
        rect("tower1", 65, 610, 105, 360, 8, "#10241e"),
        rect("tower2", 355, 650, 125, 315, 8, "#132a22"),
        ellipse("city glow", 220, 700, 500, 210, "#38f26b", 8),
    ]
    char = [
        ellipse("shadow", 222, 800, 200, 45, "#000000", 35),
        rect("leg left", 183, 735, 53, 180, 23, "#19221d", 100, "#050705", 4),
        rect("leg right", 249, 735, 53, 180, 23, "#19221d", 100, "#050705", 4),
        rect("torso shadow", 221, 580, 190, 245, 35, "#07110d", 55),
        rect("torso", 216, 565, 180, 235, 32, "#252d28", 100, "#070a08", 5),
        rect("shirt panel", 216, 565, 95, 210, 24, "#2fd75d", 100, "#166d30", 4),
        ellipse("neck", 216, 430, 60, 66, "#e7b58c", 100, "#a66e4a", 3),
        ellipse("head", 216, 365, 126, 145, "#e9b98f", 100, "#a66e4a", 4),
        polygon("hair", [(155, 350), (170, 285), (196, 308), (218, 270),
                         (236, 310), (276, 294), (270, 360)],
                "#2a1d17", 100, "#120c09", 3),
        ellipse("eye left", 191, 367, 23, 14, "#f7ffff", 100, "#2a332e", 2),
        ellipse("eye right", 241, 367, 23, 14, "#f7ffff", 100, "#2a332e", 2),
        ellipse("iris left", 194, 367, 8, 8, "#49e26b"),
        ellipse("iris right", 238, 367, 8, 8, "#49e26b"),
        ellipse("watch glow", 142, 585, 72, 72, "#55ff72", 24),
        rect("watch", 142, 585, 48, 60, 10, "#19231d", 100, "#050705", 4),
        ellipse("watch face", 142, 585, 32, 32, "#55ff72", 100, "#123d20", 3),
        ellipse("highlight", 187, 335, 26, 38, "#ffffff", 12),
    ]
    arm = [
        rect("raised arm", 142, 520, 48, 155, 20, "#e4b187", 100, "#9c6847", 4),
    ]
    return compose("Ben 10 Sidekick", "#07120e", "#45f16d", scene, char, arm)


def batman():
    scene = [
        rect("roof", 216, 835, 500, 205, 0, "#090c13"),
        ellipse("moon", 330, 210, 190, 190, "#d6e0f0", 80),
        rect("building1", 58, 620, 120, 365, 5, "#101622"),
        rect("building2", 365, 660, 115, 325, 5, "#121824"),
        rect("window1", 45, 570, 12, 24, 2, "#ffd46b", 55),
        rect("window2", 76, 640, 12, 24, 2, "#ffd46b", 42),
        rect("window3", 350, 590, 12, 24, 2, "#ffd46b", 52),
    ]
    char = [
        polygon("cape shadow", [(118, 805), (135, 500), (216, 465), (300, 505),
                                (328, 810), (275, 765), (235, 820), (195, 770),
                                (152, 820)], "#020305", 88),
        rect("body", 216, 615, 150, 300, 38, "#252c38", 100, "#080a0f", 5),
        ellipse("belt", 216, 710, 155, 26, "#d3a62c", 100, "#75540f", 3),
        ellipse("head", 216, 390, 130, 155, "#171c26", 100, "#05070b", 5),
        polygon("ear left", [(164, 340), (175, 245), (202, 337)], "#171c26", 100, "#05070b", 4),
        polygon("ear right", [(230, 337), (257, 245), (268, 340)], "#171c26", 100, "#05070b", 4),
        polygon("jaw", [(180, 405), (216, 438), (252, 405), (246, 455),
                        (216, 478), (186, 455)], "#d5a37f", 100, "#8e6249", 3),
        ellipse("eye left", 190, 385, 26, 12, "#ecf4ff", 95),
        ellipse("eye right", 242, 385, 26, 12, "#ecf4ff", 95),
        ellipse("emblem", 216, 555, 102, 52, "#d7ae35", 100, "#7b5a15", 3),
        polygon("bat mark", [(171, 555), (192, 545), (205, 553), (216, 535),
                             (227, 553), (240, 545), (261, 555), (240, 567),
                             (224, 564), (216, 577), (208, 564), (192, 567)],
                "#10141c"),
        ellipse("rim highlight", 188, 382, 28, 62, "#b8c9e5", 10),
    ]
    arm = [
        rect("forearm", 300, 570, 48, 180, 22, "#252c38", 100, "#080a0f", 4),
        polygon("gauntlet", [(278, 525), (314, 510), (326, 555), (299, 580)],
                "#171c26", 100, "#05070b", 3),
    ]
    return compose("Batman Sidekick", "#060810", "#8090ac", scene, char, arm)


def tom_jerry():
    scene = [
        rect("wall", 216, 405, 500, 810, 0, "#3d2f36"),
        rect("floor", 216, 825, 500, 220, 0, "#261d20"),
        rect("baseboard", 216, 725, 500, 22, 4, "#77545d"),
        ellipse("lamp glow", 85, 235, 260, 260, "#ffc979", 8),
    ]
    char = [
        ellipse("tom shadow", 160, 820, 190, 48, "#000000", 28),
        ellipse("tom body", 150, 665, 170, 250, "#65707b", 100, "#303841", 5),
        ellipse("tom belly", 150, 690, 105, 145, "#d8d9d8", 100, "#7f8588", 3),
        ellipse("tom head", 150, 475, 190, 180, "#68737e", 100, "#303841", 5),
        polygon("tom ear l", [(82, 430), (90, 340), (130, 418)], "#68737e", 100, "#303841", 4),
        polygon("tom ear r", [(170, 418), (210, 340), (218, 430)], "#68737e", 100, "#303841", 4),
        ellipse("tom face", 150, 500, 135, 112, "#d8d9d8", 100, "#7f8588", 3),
        ellipse("tom eye l", 122, 458, 36, 52, "#f5f4dc", 100, "#303841", 2),
        ellipse("tom eye r", 178, 458, 36, 52, "#f5f4dc", 100, "#303841", 2),
        ellipse("tom pupil l", 128, 465, 10, 18, "#1c2328"),
        ellipse("tom pupil r", 172, 465, 10, 18, "#1c2328"),
        ellipse("tom nose", 150, 515, 28, 22, "#d58a93", 100, "#7b4a52", 2),
        ellipse("jerry shadow", 326, 825, 100, 34, "#000000", 24),
        ellipse("jerry body", 326, 715, 92, 132, "#a96d43", 100, "#633c27", 4),
        ellipse("jerry head", 326, 620, 105, 98, "#b77949", 100, "#633c27", 4),
        ellipse("jerry ear l", 286, 582, 58, 58, "#bd7b4c", 100, "#633c27", 3),
        ellipse("jerry ear r", 366, 582, 58, 58, "#bd7b4c", 100, "#633c27", 3),
        ellipse("jerry inner l", 286, 582, 36, 36, "#d89a83", 90),
        ellipse("jerry inner r", 366, 582, 36, 36, "#d89a83", 90),
        ellipse("jerry muzzle", 326, 642, 64, 45, "#d7a77e", 100),
        ellipse("jerry nose", 326, 625, 17, 14, "#3a241c"),
        ellipse("jerry eye l", 308, 610, 12, 16, "#171512"),
        ellipse("jerry eye r", 344, 610, 12, 16, "#171512"),
        ellipse("highlight", 115, 440, 34, 50, "#ffffff", 9),
    ]
    arm = [
        rect("tom paw", 235, 620, 44, 145, 20, "#65707b", 100, "#303841", 4),
    ]
    return compose("Tom & Jerry Sidekick", "#21181d", "#ffc878", scene, char, arm)


def write_new(out: Path):
    generated = {
        "wallpaper_doraemon.json": doraemon(),
        "wallpaper_ben10.json": ben10(),
        "wallpaper_batman.json": batman(),
        "wallpaper_tom_jerry.json": tom_jerry(),
    }
    for filename, doc in generated.items():
        (out / filename).write_text(
            json.dumps(doc, separators=(",", ":")), encoding="utf-8"
        )


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--source-raw", required=True, type=Path)
    p.add_argument("--out", required=True, type=Path)
    args = p.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    write_core(args.source_raw, args.out)
    write_new(args.out)
    for f in sorted(args.out.glob("wallpaper_*.json")):
        json.loads(f.read_text(encoding="utf-8"))
        print(f.name)


if __name__ == "__main__":
    main()

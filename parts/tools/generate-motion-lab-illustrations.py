#!/usr/bin/env python3
"""Generate original, self-contained Lottie vectors using the existing illustration color tags."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BLUE = [0.23, 0.68, 1, 1]
PINK = [1, 0.35, 0.7, 1]
GREEN = [0.2, 0.9, 0.65, 1]
WHITE = [1, 1, 1, 1]
RED = [1, 0.35, 0.3, 1]

def prop(value):
    return {"a": 0, "k": value}

def animated(frames):
    keys = []
    for t, value in frames:
        key = {"t": t, "s": value if isinstance(value, list) else [value]}
        key.update({"o": {"x": [.3], "y": [0]}, "i": {"x": [.7], "y": [1]}})
        keys.append(key)
    return {"a": 1, "k": keys}

def transform():
    return {"ty": "tr", "p": prop([0, 0]), "a": prop([0, 0]),
            "s": prop([100, 100]), "r": prop(0), "o": prop(100), "sk": prop(0), "sa": prop(0)}

def group(name, shape, color=BLUE, stroke=0):
    paint = {"ty": "st" if stroke else "fl", "nm": "color", "c": prop(color), "o": prop(100)}
    if stroke:
        paint.update({"w": prop(stroke), "lc": 2, "lj": 2, "ml": 4})
    else:
        paint["r"] = 1
    return {"ty": "gr", "nm": name, "it": [shape, paint, transform()]}

def rect(name, w, h, color=BLUE, radius=8):
    return group(name, {"ty": "rc", "d": 1, "s": prop([w, h]), "p": prop([0, 0]), "r": prop(radius)}, color)

def circle(name, diameter, color=BLUE, stroke=0):
    return group(name, {"ty": "el", "d": 1, "s": prop([diameter, diameter]), "p": prop([0, 0])}, color, stroke)

def path(name, points, color=BLUE, stroke=4, closed=False):
    shape = {"ty": "sh", "ks": prop({"i": [[0, 0]]*len(points), "o": [[0, 0]]*len(points),
              "v": points, "c": closed})}
    return group(name, shape, color, stroke)

def layer(name, x, y, shapes, end=120, **animation):
    ks = {"o": prop(100), "r": prop(0), "p": prop([x, y, 0]), "a": prop([0, 0, 0]), "s": prop([100, 100, 100])}
    ks.update(animation)
    return {"ddd": 0, "ty": 4, "nm": name, "sr": 1, "ks": ks, "ao": 0,
            "shapes": list(reversed(shapes)), "ip": 0, "op": end, "st": 0, "bm": 0}

def phone(end=120):
    return [layer("backdrop", 240, 170, [circle(".illoBg2", 252, [.12, .16, .24, 1])], end),
            layer("phone", 240, 175, [rect(".illoFixedBlack", 156, 226, [.06, .08, .12, 1], 25)], end),
            layer("screen", 240, 179, [rect(".illoBg3", 134, 197, [.13, .17, .24, 1], 16)], end),
            layer("speaker", 238, 73, [rect(".illoBg2", 28, 3, [.3, .34, .42, 1], 2)], end),
            layer("radar", 278, 73, [circle(".illoCoreTheme2", 5)], end),
            layer("glow", 240, 91, [rect(".motionGlow", 112, 5, BLUE, 3)], end,
                  o=animated([(0, 45), (30, 100), (60, 65), (90, 100), (end, 45)]))]

def write(name, layers, end=120):
    # Drawing order is back-to-front; Lottie stores the top layer first.
    layers = list(reversed(layers))
    for index, item in enumerate(layers, 1):
        item["ind"] = index
    result = {"v": "5.9.0", "fr": 30, "ip": 0, "op": end, "w": 480, "h": 300,
              "nm": name, "ddd": 0, "assets": [], "markers": [], "layers": layers}
    (ROOT / "res/raw" / (name + ".json")).write_text(json.dumps(result, separators=(",", ":")) + "\n")

def main():
    scene = phone()
    scene += [layer("record", 240, 162, [circle(".illoCoreTheme2", 76), circle(".illoFixedBlack", 57, [.05,.07,.1,1], 2),
              circle(".illoCoreTertiary1", 28, PINK), circle(".illoFixedWhite", 7, WHITE)]),
              layer("record-mark", 240, 162, [path(".illoFixedWhite", [[0,-31],[11,-29]], WHITE, 3)],
                    r=animated([(0,0),(120,360)]))]
    for i, color in enumerate([BLUE,GREEN,[1,.65,.15,1]]):
        scene.append(layer("dj-mode-"+str(i), 210+i*30, 226, [circle(".airDjMode"+str(i),12,color)],
                           s=animated([(0,[100,100,100]),(20+i*25,[150,150,100]),(40+i*25,[100,100,100]),(120,[100,100,100])])))
    scene.append(layer("swipe",240,251,[path(".illoCoreSecondary1",[[-32,0],[32,0],[23,-8]],GREEN)],
                       p=animated([(0,[230,251,0]),(60,[250,251,0]),(120,[230,251,0])])))
    write("motion_lab_air_dj",scene)

    scene=phone()
    for x in [210,240,270]:
        scene.append(layer("lane",x,178,[rect(".illoBg2",21,142,[.2,.25,.34,1],6)]))
    scene.append(layer("obstacle",240,120,[rect(".arcadeHazard",16,22,RED,4)],
                       p=animated([(0,[240,112,0]),(70,[240,244,0]),(85,[240,244,0]),(120,[240,112,0])]),
                       o=animated([(0,100),(70,100),(80,0),(119,0),(120,100)])))
    scene.append(layer("player",240,230,[circle(".illoCoreSecondary1",18,GREEN),circle(".motionGlow",28,BLUE,2)],
                       p=animated([(0,[240,230,0]),(25,[210,230,0]),(65,[210,230,0]),(90,[270,230,0]),(120,[240,230,0])])))
    write("motion_lab_arcade",scene)

    scene=phone()
    for i in range(4):
        scene.append(layer("lesson-"+str(i),195+30*i,235,[circle(".illoCoreSecondary1",10,GREEN)],
                           o=animated([(0,30),(15+20*i,100),(35+20*i,30),(120,30)])))
    scene.append(layer("guided-swipe",240,156,[path(".illoCoreTheme2",[[-35,0],[35,0],[23,-12]],BLUE,7)],
                       p=animated([(0,[225,156,0]),(45,[255,156,0]),(90,[225,156,0]),(120,[225,156,0])])))
    scene.append(layer("success",240,190,[path(".illoCoreSecondary1",[[-14,0],[-4,10],[17,-12]],GREEN,6)],
                       o=animated([(0,0),(60,0),(75,100),(105,100),(120,0)])))
    write("motion_lab_training",scene)

    scene=phone()
    for i,(x,y) in enumerate([(213,136),(267,136),(213,188),(267,188),(213,240),(267,240)]):
        scene.append(layer("control-"+str(i),x,y,[rect(".illoCoreTheme3",40,40,[.24,.36,.54,1],10)]))
        points=[[-7,-9],[-7,9],[9,0]] if i==0 else [[-8,0],[8,0]]
        shapes=[path(".illoFixedWhite",points,WHITE,3,closed=i==0)]
        if i in [1,5]: shapes.append(path(".illoFixedWhite",[[0,-8],[0,8]],WHITE,3))
        if i==2: shapes=[path(".illoFixedWhite",[[5,-8],[-5,0],[5,8]],WHITE,3)]
        if i==3: shapes=[path(".illoFixedWhite",[[-5,-8],[5,0],[-5,8]],WHITE,3)]
        scene.append(layer("symbol-"+str(i),x,y,shapes))
    scene.append(layer("selected-control",213,136,[rect(".motionGlow",47,47,BLUE,12)],
                       p=animated([(0,[213,136,0]),(30,[267,136,0]),(60,[267,188,0]),(90,[213,188,0]),(120,[213,136,0])]),o=prop(30)))
    write("motion_lab_panel",scene)

    scene=phone()
    for i,(x,y,c) in enumerate([(126,138,BLUE),(354,138,PINK),(116,203,GREEN),(364,203,[.65,.4,1,1])]):
        scene.append(layer("palette-"+str(i),x,y,[circle(".illoCoreSecondary1" if i%2==0 else ".illoCoreTertiary1",24,c)],
                           p=animated([(0,[x,y,0]),(60,[x,y-9,0]),(120,[x,y,0])])))
    for i,c in enumerate([BLUE,PINK,GREEN,[.65,.4,1,1],[1,.7,.2,1]]):
        scene.append(layer("glow-style-"+str(i),240,125+i*27,[rect(".motionGlow" if i==0 else ".studioSample"+str(i),92,7,c,4)],
                           s=animated([(0,[75,100,100]),(20+i*12,[105,100,100]),(100,[75,100,100]),(120,[75,100,100])])))
    write("motion_lab_studio",scene)

    scene=phone()
    for i in reversed(range(9)):
        delay=i*3
        scene.append(layer("trail-"+str(i),190,165,[circle(".motionGlow",max(3,13-i),BLUE)],
                           p=animated([(0,[190,165,0]),(15+delay,[190,165,0]),(55+delay,[290,165,0]),(100,[190,165,0]),(120,[190,165,0])]),
                           o=prop(100-i*10)))
    scene.append(layer("direction",240,226,[path(".illoCoreSecondary1",[[-30,0],[30,0],[20,-10]],GREEN,4)]))
    write("motion_lab_trails",scene)

    scene=phone(240)
    scene[-1]["ks"]["o"]=animated([(0,0),(10,100),(210,100),(225,0),(240,0)])
    scene += [layer("preview-clock",240,171,[circle(".illoCoreTheme2",72,BLUE,4)],240),
              layer("preview-hand",240,171,[path(".illoCoreSecondary1",[[0,4],[0,-26]],GREEN,4)],240,
                    r=animated([(0,0),(210,360),(240,360)])),
              layer("preview-ends",240,238,[rect(".illoCoreSecondary1",78,6,GREEN,3)],240,
                    s=animated([(0,[100,100,100]),(210,[0,100,100]),(225,[0,100,100]),(240,[100,100,100])]))]
    write("motion_lab_preview",scene,240)

    scene=phone()
    colors=animated([(0,RED),(45,RED),(60,BLUE),(105,BLUE),(120,RED)])
    # Example album colors deliberately do not use .motionGlow, which follows the saved tint.
    scene[-1]["shapes"][0]["nm"]=".albumGlow"
    scene[-1]["shapes"][0]["it"][1]["c"]=colors
    cover=rect(".albumCover",86,86,RED,12);cover["it"][1]["c"]=colors
    scene += [layer("album-cover",240,158,[cover]),
              layer("album-moon",260,138,[circle(".illoFixedWhite",18,WHITE)]),
              layer("album-landscape",240,174,[path(".albumLandscape",[[-39,25],[-11,-18],[7,7],[18,-8],[39,25]], [.12,.18,.3,1],0,True)]),
              layer("track-name",232,222,[rect(".illoCoreTheme2",70,5,BLUE,3)]),
              layer("artist",224,238,[rect(".illoCoreSecondary1",54,4,GREEN,2)]),
              layer("play",278,231,[path(".illoCoreTheme2",[[-4,-7],[-4,7],[7,0]],BLUE,0,True)])]
    write("motion_lab_album",scene)

    scene=phone()
    for i,(x,y,c) in enumerate([(215,139,BLUE),(265,139,GREEN),(215,213,PINK),(265,213,[.65,.4,1,1])]):
        scene.append(layer("lab-feature-"+str(i),x,y,[rect(".illoCoreSecondary1" if i%2 else ".illoCoreTertiary1",34,40,c,9)],
                           s=animated([(0,[90,90,100]),(15+i*20,[115,115,100]),(35+i*20,[90,90,100]),(120,[90,90,100])])))
    scene.append(layer("lab-swipe",240,178,[path(".motionGlow",[[-37,0],[37,0],[27,-9]],BLUE,4)]))
    write("motion_lab_overview",scene)

if __name__ == "__main__":
    main()

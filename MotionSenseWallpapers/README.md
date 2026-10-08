# Project Infinity X — Unified Pokémon + Sidekick Live Wallpaper

_Last updated: October 8, 2026_

This document describes the live-wallpaper work on the **`faceid` branch** for Project Infinity X / Pixel 4 series.

> **Branch safety:** the stable `lineage-24.0` Motion Sense branches are not modified by this work. The unified wallpaper integration lives on `faceid`.

## Current status

The unified wallpaper code and build pipeline are implemented on `faceid`.

- Google Pokémon and Infinity Sidekick now ship inside **one `PixelLiveWallpaper.apk`**.
- Sidekick is **not** installed as a second wallpaper application.
- Google's original Pokémon character/model artwork is left untouched.
- Infinity Sidekicks use the same unified Motion Sense-aware interaction engine.
- Eight original Sidekicks are enhanced instead of being replaced.
- Eight extra packs are generated and included.
- Pokémon environment/effect textures can be enhanced without selecting Pokémon character textures.
- The Unity split-asset rewrite now preserves the original split boundaries, reloads the complete decoded APK tree, decodes every modified texture, and rolls back automatically if validation fails.
- The GitHub Actions unification workflow currently completes successfully and writes the generated unified APK back to the vendor `faceid` branch.

CI success proves the package can be rebuilt, signed for validation, inspected, and repacked. **Final behavior still needs real-device testing in a ROM build on Pixel 4 / Pixel 4 XL**, especially Motion Sense delivery, launcher/wallpaper-picker behavior, touch hitboxes and long-running animation stability.

## Architecture

```text
Google PixelLiveWallpaper.apk
│
├── Google's existing live wallpapers
│
├── Google's original Pokémon renderer
│   └── original Pokémon character/model assets
│
├── enhanced Pokémon environment/effect textures
│
└── Infinity Unified Sidekick
    ├── UnifiedSidekickWallpaper
    ├── OsloGestureClient
    ├── Lottie runtime
    └── assets/sidekick/
        ├── original Sidekicks
        ├── fan-art packs
        └── original premium packs
```

The vendor workflow builds the Sidekick payload separately, injects its DEX and assets into the decoded Google package, adds the wallpaper service to the existing manifest, rebuilds the APK, validates it, and stores the result back at:

```text
proprietary/product/priv-app/PixelLiveWallpaper/PixelLiveWallpaper.apk
```

The injected wallpaper service is:

```text
com.hecker.motionsense.wallpapers.UnifiedSidekickWallpaper
```

This keeps Google's Pokémon renderer and Infinity Sidekick in the same installed package instead of maintaining two separate live-wallpaper apps.

## Character set

The unified build currently contains the eight original Sidekicks:

- Hecker
- Mochi
- Biscuit
- Whisk & Crumb
- Pip
- Drift
- Orbit
- Aurora

It also prepares these additional packs:

- Doraemon
- Ben 10
- Batman
- Tom & Jerry
- Nova
- Kumo
- Ember
- Byte

Nova, Kumo, Ember and Byte are original Infinity Sidekick designs.

The original Sidekick JSON scenes are upgraded with semantic animation markers and subtle depth layers rather than replacing their base artwork. The enhancement pass adds elements such as contact shadows, soft key/rim light, haze, atmospheric motes and scene glow so the characters read less like flat stickers.

The generated extended packs use the same marker contract and therefore participate in the same touch and Motion Sense behavior as the original Sidekicks.

## Interaction system

| Input | Reaction |
| --- | --- |
| Tap character | `tap`, fallback to `wave` |
| Double tap character | Switch to the next available character and remember the selection |
| Hold character | `special`, fallback to `pet` / `nod` |
| Swipe left/right | Directional `left` / `right` reaction |
| Swipe up | `up`, fallback to `wake` |
| Swipe down | `sleep` |
| Background tap | Character looks toward the touch position |
| Two-finger tap | Celebration/special reaction |
| Motion Sense presence | Wake a sleeping character |
| Motion Sense reach | `pet`, fallback to `nod` |
| Motion Sense flick | Directional left/right reaction |
| Idle | Occasional micro-reactions before the normal sleep timeout |

The renderer also adds subtle parallax overscan, spring/tilt response and a vignette/depth treatment around the custom scenes.

Double-tap cycling is handled by the unified Sidekick engine, so every Sidekick pack in the generated asset set participates in one continuous character cycle.

## Animation marker contract

Every Sidekick pack must provide an `idle` marker.

The legacy/core marker set is:

```text
idle
wake
wave
left
right
nod
sleep
asleep
wakeup
```

The enhanced engine can also use:

```text
tap
pet
special
up
look_left
look_right
excited
peek
celebrate
jump
cuddle
surprised
```

Missing optional markers are safe. The pack builder aliases richer semantic actions back to existing timelines when necessary, so older animations still work with the newer interaction engine.

This marker contract is the integration point for future animation makers or newly-authored Lottie packs: a new character does not need its own Motion Sense implementation as long as its animations expose the expected semantic markers.

## Google Pokémon policy

Google's Pokémon characters are deliberately treated differently from Infinity Sidekicks.

### What we do not change

The Pokémon character/model artwork is not selected by the enhancement tool and is not redrawn by the Sidekick generator.

### What we can enhance

The current allowlist targets environment/effect textures such as:

```text
cloudc2
cloudc_light
sun pokemon
stars_twinkly
rainstar
waterdropparticle
watermistparticle
```

The pass uses small brightness, contrast, color and bloom adjustments to make the environment feel richer while leaving the Pokémon subjects alone.

## Unity split-asset repacking

PixelLiveWallpaper stores part of the Pokémon Unity data in Android split asset files. A normal single-file Unity rewrite is unsafe because related SerializedFiles and resource streams may be distributed across multiple files.

`tools/enhance_pokemon_backgrounds.py` therefore:

1. Loads the **complete decoded APK tree** with UnityPy.
2. Selects only exact allowlisted environment/effect Texture2D names.
3. Preserves the texture format and mip count.
4. Saves the owning Unity top-level stream.
5. Re-splits the rebuilt stream using the original Android split boundaries.
6. Reassembles the written split parts and verifies that they match the rebuilt stream.
7. Reloads the **entire decoded tree**.
8. Forces every modified texture to decode again and checks its dimensions.
9. Restores the original bytes automatically if any write or validation step fails.

This is the safeguard that allows enhanced Pokémon backgrounds to survive into the generated `PixelLiveWallpaper.apk` instead of being lost or corrupting the Unity asset set.

## Build pipeline

The vendor repository contains:

```text
.github/workflows/unify-wallpapers.yml
tools/unify_pixel_live_wallpaper.py
tools/enhance_pokemon_backgrounds.py
```

The device repository contains:

```text
MotionSenseWallpapers/
├── tools/build_extended_packs.py
├── unified_payload/UnifiedSidekickWallpaper.java
├── src/com/hecker/motionsense/wallpapers/OsloGestureClient.java
└── res/raw/wallpaper_*.json
```

The workflow performs these stages:

1. Checkout vendor `faceid`.
2. Checkout device `faceid`.
3. Build the extended Sidekick asset set.
4. Validate generated JSON packs.
5. Build the resource-independent Sidekick payload with Lottie.
6. Decode Google's `PixelLiveWallpaper.apk`.
7. Enhance allowlisted Pokémon environment textures.
8. Inject the Sidekick wallpaper service and assets.
9. Rebuild the Google APK.
10. Remove any previously injected Sidekick DEX to prevent duplicate classes.
11. Inject the freshly built Sidekick DEX.
12. Zipalign and sign a validation copy.
13. Verify the manifest, Sidekick assets and DEX package.
14. Store the generated unified APK and reports back on the vendor `faceid` branch.

The generated package is therefore reproducible from the source-side payload and tooling rather than being a manually edited APK.

## What happens when Google/ROM updates PixelLiveWallpaper?

An upstream PixelLiveWallpaper update **can** affect compatibility, but the integration is designed to fail visibly instead of silently damaging the APK.

The current injector first verifies that the decoded package name is:

```text
com.google.pixel.livewallpaper
```

If it is not, injection stops.

The Sidekick payload is deliberately resource-independent and is inserted as its own DEX/assets plus one wallpaper service entry. That makes ordinary internal resource changes in Google's APK less likely to affect Sidekick than a patch based on hard-coded resource IDs.

However, a new Google APK can still require review if it changes any of the following:

- package or manifest structure
- Android signing/privileged-permission requirements
- wallpaper service behavior
- DEX/class-loading assumptions
- Unity serialization/version/layout
- names of the allowlisted environment textures
- Android framework behavior used by the injected wallpaper service
- Motion Sense/Oslo interfaces used by the bridge/client

Whenever the base Google APK is updated, rerun the unification workflow and require all validation stages to pass before using it in a ROM build.

The Pokémon background enhancer is intentionally fail-safe: if a future Unity layout cannot be safely rewritten and reloaded, it restores the original Unity bytes instead of committing a broken enhancement.

## Motion Sense

`OsloGestureClient` is shared by the unified Sidekick implementation so the currently selected Sidekick can respond to the same Pixel 4 Motion Sense stack used elsewhere in Project Infinity.

The intended mapping is:

```text
Presence -> wake
Reach    -> pet / nod
Flick    -> left / right
```

Touch interactions still work independently of Motion Sense.

Google's own Pokémon renderer remains proprietary. The Infinity integration does not rewrite its character models; Motion Sense compatibility for the Google package is handled through the Pixel 4 Motion Sense bridge/vendor integration.

## Validation checklist

After a ROM build, verify on-device:

- [ ] Only one Pixel live-wallpaper package is installed for Pokémon + Sidekick.
- [ ] The Sidekick wallpaper appears in the wallpaper picker.
- [ ] All generated Sidekick characters load.
- [ ] Double tap cycles through every Sidekick and wraps back to the first.
- [ ] The selected Sidekick survives wallpaper recreation/reboot.
- [ ] Tap, hold, swipe, two-finger tap and background-look interactions work.
- [ ] Presence wakes a sleeping Sidekick.
- [ ] Reach triggers pet/nod.
- [ ] Flick triggers the correct left/right reaction.
- [ ] Google's Pokémon characters still render exactly as before.
- [ ] Enhanced Pokémon environment textures are visible where the allowlisted assets are used.
- [ ] No Unity asset errors appear in logcat.
- [ ] No duplicate Sidekick DEX/classes exist in the APK.
- [ ] Long-running wallpaper use does not leak/crash after screen-off/on cycles.

Useful APK checks:

```bash
apkanalyzer manifest print PixelLiveWallpaper.apk | grep UnifiedSidekickWallpaper
unzip -l PixelLiveWallpaper.apk | grep assets/sidekick
apkanalyzer dex packages PixelLiveWallpaper.apk | grep com.hecker.motionsense.wallpapers
```

## Adding another character

For a new Sidekick pack:

1. Create a Lottie JSON scene.
2. Include `idle` and as many semantic markers as possible.
3. Keep interaction names compatible with the marker contract above.
4. Add the pack to the generated asset set.
5. Re-run the unified wallpaper workflow.
6. Test touch, Motion Sense and character cycling on-device.

No new Motion Sense service should be required for each character.

## Main source locations

Device tree:

https://github.com/its-hecker/infinity_cnb_android_device_google_coral/tree/faceid/MotionSenseWallpapers

Vendor tree:

https://github.com/its-hecker/infinity_cnb_proprietary_vendor_google_coral/tree/faceid

Motion Sense / Oslo port:

https://github.com/its-hecker/infinity_OsloFeedback

## Notes on third-party characters

Doraemon, Ben 10, Batman and Tom & Jerry are third-party copyrighted characters. The current project code demonstrates optional fan-art/generated character packs, but public redistribution of third-party artwork should be reviewed separately from the technical implementation.

Google Pokémon assets remain proprietary Google/Nintendo/The Pokémon Company content from the original Pixel package and are not replaced by the Infinity Sidekick generator.

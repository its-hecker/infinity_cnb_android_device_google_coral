# Project Infinity X — Unified Pokémon + Sidekick Live Wallpaper

_Last updated: October 12, 2026_

This document describes the live-wallpaper work on the **`features-experimental` branch** for Project Infinity X / Pixel 4 series.

> **Branch safety:** the stable `lineage-24.0` Motion Sense branches are not modified by this work. The unified wallpaper integration lives on `features-experimental`.

## Current status

The unified wallpaper code and build pipeline are implemented on `features-experimental`.

- Google Pokémon and Infinity Sidekick now ship inside **one `PixelLiveWallpaper.apk`**.
- Sidekick is **not** installed as a second wallpaper application.
- The wallpaper packaging workflow preserves the existing Pokémon code and assets byte for byte.
- Infinity Sidekicks use the same unified Motion Sense-aware interaction engine.
- Eight original Sidekicks are enhanced instead of being replaced.
- Eight extra packs are generated and included.
- Sidekick is registered in the existing **Come Alive** catalog with a thumbnail and preview metadata.
- The workflow updates picker metadata by default and preserves all existing DEX, animation and native-library bytes.
- A manual `rebuild_payload: true` run rebuilds Sidekick code and animations when those sources change.
- The GitHub Actions workflow validates the compiled APK before writing it back to the selected vendor branch.

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
├── existing Pokémon environment/effect textures (preserved)
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

The vendor workflow registers the existing Sidekick payload in Pixel's Come Alive service catalog, places `live_categories` metadata on the application and adds preview resources. It rebuilds and validates the APK, then stores it back at:

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

The packaging workflow does not modify Pokémon backgrounds, environment textures, character models or renderer code. It compares the rebuilt APK against the checked-in baseline and requires every existing Google asset, native library and retained DEX file to have identical bytes.

The old background enhancement tool is no longer called by the workflow. This picker repair preserves the assets already present in the checked-in package.

## Picker discovery

Pixel's picker groups services from `com.google.pixel.livewallpaper` into built-in categories. Once that package belongs to a category, an unlisted service can be excluded from the generic live-wallpaper list.

The unified service must therefore appear exactly once in `come_alive_service_names`. Its Android wallpaper XML also supplies a thumbnail, description and author. After installing a ROM with the repaired package, look for **Wallpaper & style → Come Alive → Sidekick • Motion Sense**. One entry provides all 16 characters; double-tap the character to cycle.

## Build pipeline

The vendor repository contains:

```text
.github/workflows/unify-wallpapers.yml
tools/unify_pixel_live_wallpaper.py
tools/check_sidekick_picker.py
tools/apk_resource_reader.py
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

1. Check out the vendor branch selected by the event and the matching device branch.
2. Run picker registration and DEX replacement regression checks.
3. For a manual `rebuild_payload: true` run, generate the 16 Sidekick packs, build the separate payload and isolate its AndroidX dependencies.
4. Decode the checked-in PixelLiveWallpaper APK without disassembling its DEX.
5. Register Sidekick in the Come Alive catalog and add application-level category metadata and preview resources.
6. Rebuild the APK; replace Sidekick assets and DEX only when explicitly requested.
7. Zipalign and sign a validation copy. The ROM build signs the imported system APK with its platform certificate.
8. Verify the compiled catalog, service, thumbnail, preview text and unique payload classes.
9. Compare existing Google service declarations, category entries, DEX, native libraries and assets with the baseline.
10. Store the verified APK, validation report and source/hash build stamp on the same vendor branch.

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
- category resource names and picker metadata
- Android framework behavior used by the injected wallpaper service
- Motion Sense/Oslo interfaces used by the bridge/client

Whenever the base Google APK is updated, rerun the unification workflow and require all validation stages to pass before using it in a ROM build.

If the expected Come Alive catalog is missing, the registration script stops. The workflow does not publish an APK unless compiled-resource and byte-preservation checks pass.

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
- [ ] Sidekick • Motion Sense appears inside Come Alive with a preview thumbnail.
- [ ] All generated Sidekick characters load.
- [ ] Double tap cycles through every Sidekick and wraps back to the first.
- [ ] The selected Sidekick survives wallpaper recreation/reboot.
- [ ] Tap, hold, swipe, two-finger tap and background-look interactions work.
- [ ] Presence wakes a sleeping Sidekick.
- [ ] Reach triggers pet/nod.
- [ ] Flick triggers the correct left/right reaction.
- [ ] Google's Pokémon characters still render exactly as before.
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
5. Re-run the unified wallpaper workflow with `rebuild_payload: true`.
6. Test touch, Motion Sense and character cycling on-device.

No new Motion Sense service should be required for each character.

## Main source locations

Device tree:

https://github.com/its-hecker/infinity_cnb_android_device_google_coral/tree/features-experimental/MotionSenseWallpapers

Vendor tree:

https://github.com/its-hecker/infinity_cnb_proprietary_vendor_google_coral/tree/features-experimental

Motion Sense / Oslo port:

https://github.com/its-hecker/infinity_OsloFeedback

## Notes on third-party characters

Doraemon, Ben 10, Batman and Tom & Jerry are third-party copyrighted characters. The current project code demonstrates optional fan-art/generated character packs, but public redistribution of third-party artwork should be reviewed separately from the technical implementation.

Google Pokémon assets remain proprietary Google/Nintendo/The Pokémon Company content from the original Pixel package and are not replaced by the Infinity Sidekick generator.

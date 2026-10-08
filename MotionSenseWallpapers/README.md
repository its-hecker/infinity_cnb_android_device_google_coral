# Sidekick character packs

The custom Sidekick wallpapers use one Motion Sense-aware Lottie renderer.

## Built-in interactions

- tap: `tap` marker, falling back to `wave`
- double tap: cycle to the next available character and remember the selection
- hold: `special`, falling back to `pet` / `nod`
- horizontal swipe: `left` / `right`
- swipe up: `up`, falling back to `wake`
- swipe down: `sleep`
- Motion Sense presence: wake a sleeping character
- Motion Sense reach: `pet`, falling back to `nod`
- Motion Sense flick/swipe: directional left/right reaction
- idle: occasional micro-reactions before the normal sleep timeout

The renderer adds subtle parallax overscan and a vignette to give the custom scenes more depth without changing their animation geometry.

## Marker contract

Every pack must provide `idle`. Existing Sidekick packs also provide:

`wake`, `wave`, `left`, `right`, `nod`, `sleep`, `asleep`, `wakeup`

Optional richer markers are:

`tap`, `pet`, `special`, `up`, `look_left`, `look_right`

Missing optional markers are safe; the engine falls back to the standard markers.

## Optional character slots

The engine automatically discovers these raw-resource names if assets are added later:

- `wallpaper_doraemon`
- `wallpaper_ben10`
- `wallpaper_batman`
- `wallpaper_tom_jerry`

Those third-party character assets are not bundled here. Adding a compatible Lottie JSON under `res/raw/` with one of those names makes it join the double-tap cycle automatically.

## Google Pokémon

Google Pokémon remains the original `PixelLiveWallpaper` renderer and original character artwork. The Sidekick About screen links directly to Google's Pokémon wallpaper component. Motion Sense compatibility for that proprietary renderer is supplied separately by the Pixel 4 Motion Sense bridge in the vendor tree.

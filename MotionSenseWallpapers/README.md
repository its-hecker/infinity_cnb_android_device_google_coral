# Unified Pokemon + Sidekick live wallpaper

On the `faceid` branch, Sidekick is not shipped as a second wallpaper APK. The build
injects the Sidekick engine, character assets and Motion Sense client directly into
Google's `PixelLiveWallpaper` package. Google's original Pokemon renderer remains in
that same APK, so the picker gets one Pixel live-wallpaper package rather than two
separate apps.

The custom Sidekicks use one Motion Sense-aware Lottie renderer.

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
- background tap: character looks toward the touch
- two-finger tap: celebration/special reaction
- idle: occasional micro-reactions before the normal sleep timeout

The renderer adds subtle parallax overscan and a vignette to give the custom scenes more depth without changing their animation geometry.

## Marker contract

Every pack must provide `idle`. Existing Sidekick packs also provide:

`wake`, `wave`, `left`, `right`, `nod`, `sleep`, `asleep`, `wakeup`

Optional richer markers are:

`tap`, `pet`, `special`, `up`, `look_left`, `look_right`,
`excited`, `peek`, `celebrate`, `jump`, `cuddle`, `surprised`

Missing optional markers are safe; the engine falls back to the standard markers.

## Extended character set

The unified build currently prepares these extra packs in addition to the eight original
Sidekicks:

- Doraemon
- Ben 10
- Batman
- Tom & Jerry
- Nova
- Kumo
- Ember
- Byte

Nova, Kumo, Ember and Byte are original Sidekick designs. The generated scenes use
layered lighting, contact shadows, rim light, haze and foreground atmosphere so they
read less like flat stickers. All extended packs participate in the same double-tap
cycle and Motion Sense interaction system.

## Google Pokémon

Google Pokémon remains the original `PixelLiveWallpaper` renderer and the Pokémon
character/model artwork is not rewritten. The vendor build applies a narrowly allowlisted,
fail-safe Unity texture pass to environment/effect textures (clouds, sun, stars and weather)
for a slightly richer background presentation. If a Unity bundle cannot be safely rebuilt,
the script restores the original bytes.

Motion Sense compatibility for Google's proprietary renderer is supplied by the Pixel 4
Motion Sense bridge in the vendor tree.

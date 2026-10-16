# Experimental stock Google Camera for Flame

This branch preinstalls Google Camera **8.7.250.494820638.44** (`65822832`),
package `com.google.android.GoogleCamera`, with Google's original signing
certificate (`f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83`).
It reuses the exact signed base and nine common feature APKs from the Coral
`gcam-experimental` payload. Tensor-specific modules are excluded.

Only `lineage_flame.mk` inherits `camera.mk`. The vendor module is
`GoogleCameraFlame`, installed under `/product/app/GoogleCameraFlame/`.
The shared device checkout stays at **device/google/coral**; the matching vendor
checkout is **vendor/google/flame**. Use `gcam-experimental-flame` in both
repositories and in `its-hecker/infinity_cnb_flame_local_mainfest`.

The tested Flame Face ID and Motion Sense implementation is inherited from
`infinity-4.1-flame`. No camera HAL, firmware, kernel, Face ID or Motion Sense
runtime code is changed. PixelCameraServicesFlame and the existing camera app
remain available. Camera, microphone and photo/video grants are revocable;
location remains a user choice. The original Google certificate is scoped only
to this package for the existing `google_camera_app` SELinux domain.

## Build and phone checks

Follow `docs/GCAM_EXPERIMENTAL.md` in the Flame local-manifest repository.
Before building:

```bash
python3 vendor/google/flame/camera/tools/prepare_camera.py --check-hashes
```

After flashing:

```bash
adb shell dumpsys package com.google.android.GoogleCamera
adb shell am start -n com.google.android.GoogleCamera/com.android.camera.CameraLauncher
adb shell ps -AZ | grep com.google.android.GoogleCamera
```

Expect version 8.7.250.494820638.44, the base plus nine feature splits, and the
`google_camera_app` process domain. A newer installed Camera update in /data
can override this version; uninstall that app's updates in Settings before
testing the bundled payload.

Test rear 1x/2x and front photos, HDR+, Portrait, Night Sight, video with audio,
slow motion, panorama/Photo Sphere, preview and lock-screen launch. Check Face ID
before and after Camera, including model removal and re-enrollment, and check
Motion Sense/glow/Air DJ. A full Flame ROM build and physical GCam testing are
still required; signature and packaging CI do not establish runtime success.

```bash
adb logcat -c
# Reproduce a failure, then:
adb logcat -b all -d > flame-gcam-logcat.txt
adb logcat -b crash -d > flame-gcam-crashes.txt
```

# Stock Google Camera for Flame

Flame stable (`infinity-4.1-flame`) preinstalls Google Camera
**8.7.250.494820638.44** (`65822832`), package
`com.google.android.GoogleCamera`, with Google's original signing certificate
(`f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83`).

The maintainer confirmed GCam works on Flame on **2026-10-17** and approved
promotion from `gcam-experimental-flame`. The original signed base and nine
common feature APKs are retained unchanged; Tensor-specific modules are excluded.

Only `lineage_flame.mk` inherits `camera.mk`. `GoogleCameraFlame` is installed
under `/product/app/GoogleCameraFlame/`. The shared device checkout remains at
**device/google/coral**, with the matching vendor checkout at **vendor/google/flame**.
Use `infinity-4.1-flame` in both repositories and `main` in
`its-hecker/infinity_cnb_flame_local_mainfest`.

`PRODUCT_NO_CAMERA := true` is set before Infinity's common product is inherited,
so Aperture is omitted while `GoogleCameraFlame` remains the bundled camera.
PixelCameraServicesFlame and the tested Face ID/Motion Sense implementation are
retained. Camera, microphone and photo/video grants are revocable; location is a
user choice. The original Google certificate is scoped only to this package for
the existing `google_camera_app` SELinux domain.

## Build and regression checks

Follow `docs/GOOGLE_CAMERA.md` in the Flame local-manifest repository.
Before building:

```bash
python3 vendor/google/flame/camera/tools/prepare_camera.py --check-hashes
```

After flashing:

```bash
adb shell dumpsys package com.google.android.GoogleCamera
adb shell am start -n com.google.android.GoogleCamera/com.android.camera.CameraLauncher
adb shell ps -AZ | grep com.google.android.GoogleCamera
adb shell pm path org.lineageos.aperture
```

Expect Camera 8.7.250.494820638.44, the base plus nine feature splits, and the
`google_camera_app` process domain. Aperture should have no package path on a
clean installation. A newer Camera update in /data can override the bundled
version; uninstall that app's updates in Settings when testing this payload.

For future changes, test rear 1x/2x and front photos, HDR+, Portrait, Night Sight,
video/audio, slow motion, panorama/Photo Sphere, preview and lock-screen launch.
Recheck Face ID removal, re-enrollment and unlock, plus Motion Sense/glow/Air DJ.

```bash
adb logcat -c
# Reproduce a failure, then:
adb logcat -b all -d > flame-gcam-logcat.txt
adb logcat -b crash -d > flame-gcam-crashes.txt
```

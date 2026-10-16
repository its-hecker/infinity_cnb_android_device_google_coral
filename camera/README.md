# Experimental stock Google Camera for coral

Target: **8.7.250.494820638.44** (`65822832`), package
`com.google.android.GoogleCamera`, signed with Google's original release key
(`f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83`).
This is the stock 8.7 generation used by Pixel 4/4 XL. 8.9 is not the last
supported stock version for this hardware. Android 17 operation is experimental
until a ROM build and physical-device test pass.

The matching `gcam-experimental` vendor branch defines `GoogleCameraCoral` as
an `android_app_set`. It installs the untouched base and nine common feature
APKs together in `/product/app/GoogleCameraCoral/`. The six Tensor-specific
`p21`, `p22` and `cheetah` modules are omitted. The native startup, portrait,
panorama and other common modules are included; installing only the base APK
would omit essential code/assets. APKs are neither merged nor re-signed.

Only `lineage_coral.mk` inherits `camera.mk`. Flame's product and the stable
branches do not select this package. The existing camera app remains available
during the experiment. No camera HAL, kernel, Face ID or Motion Sense code is
changed. The stock Pixel Camera Services and experimental2019 library already
come from the vendor tree.

The device policy binds **only this package with Google's signing certificate**
to the existing `google_camera_app` domain. Its DSP/Airbrush rules already exist.
Camera, microphone and photo/video permissions are revocable; location remains
an in-app user choice. The app also receives its legacy hidden-API allowlist.

## Build and verify

Use `its-hecker/local_manifests` branch `gcam-experimental` and follow
`docs/GCAM_EXPERIMENTAL.md`. Both device and vendor must use the matching branch;
the kernel remains on `cnb-Latest-stable`.

The vendor CI checks every installed APK's signature, package/version, SHA-256
and native startup payload. It does not build or run Android. A local preflight:

```bash
python3 vendor/google/coral/camera/tools/prepare_camera.py --check-hashes
```

For independent signature/manifest verification, use `--check` with `aapt2` and
`apksigner` from Android Build Tools in `PATH`, or supply `--aapt2`/`--apksigner`.

After boot, verify the selected app and SELinux domain:

```bash
adb shell dumpsys package com.google.android.GoogleCamera
adb shell am start -n com.google.android.GoogleCamera/com.android.camera.CameraLauncher
adb shell ps -AZ | grep com.google.android.GoogleCamera
```

Expect version `8.7.250.494820638.44`, the base plus nine split names, and the
`google_camera_app` process domain. An existing newer Camera update in `/data`
can override the preinstalled version; uninstall that app's updates in Settings
before evaluating this payload.

Test rear 1x/2x and front photos, HDR+, Portrait, Night Sight, video with audio,
slow motion, panorama/Photo Sphere, the photo preview and lock-screen launch.
Check Face ID before/after using Camera. These are test targets, not claims that
all modes have already passed on Android 17. Capture a log if anything crashes:

```bash
adb logcat -c
# Reproduce the failure, then:
adb logcat -b all -d > coral-gcam-logcat.txt
```

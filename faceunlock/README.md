# Pixel 4 XL Face HAL compatibility

The stock Android 13 Face HAL uses native_handle_t camera windows and the
HIDL camera service. The Android 17 vendor Camera NDK uses ANativeWindow
and AIDL. The private libcamera2ndk_v33_face module installs
/vendor/lib64/libcamera2ndk_face.so and retains the stock camera interface.

## Camera callbacks

The Face HAL allocates a 104-byte CallbackHandler with a 96-byte AHandler
base. Android 17 extends AHandler with delivery-tracking state, which
overlaps the HAL's private callback pointer. Keep both Face HAL and its
legacy camera NDK linked to libstagefright_foundation-v33 and compile the
camera code with compat/AHandler.h. The header checks the 96-byte layout.

The October 12 logs from the pre-revert build reach IR capture and
enrollment frame processing. The old image instead times out dequeuing
camera buffers at frame 12. That callback compatibility path is restored
here based on the successful boot and frame processing.

## Calibration dependency

Face HAL dynamically opens libautocalsolvers.so and looks up Autocalibrate.
This dependency must be packaged even though it is absent from DT_NEEDED.
coral/proprietary-files-vendor.txt includes it for future stock extractions;
the vendor prebuilt and install list ship it at /vendor/lib64.

Use the blob from coral TP1A.221005.002.B2 / 9382335. The vendor validation
workflow pins its Git blob hash, checks its exported function and runtime
dependencies, and verifies the stock and packaged Face HAL executable
sections match. The stock library has the unusual SONAME
libautocalsolvers.so-Wl; the prebuilt disables Soong's filename/SONAME
check specifically for this verified blob. No calibration values or
enrollment checks are bypassed.

The uploaded again logs show the missing solver causing a null function
call in AutoCalibrationInvocation::Run. Face HAL then restarts and crashes
in setActiveUser, leaving challenge generation queued and the enrollment
consent button disabled.

## Runtime status and challenge queue fix

Enrollment and Face Unlock now work on the phone after the camera, calibration
and Keymaster compatibility repairs. The new October 12 process map includes
libcrypto-v33.so, and faceid-fixed-logcat.txt contains no native crash markers.
The earlier Citadel export_key_der abort had a separate direct dependency repair;
keymaster/README.md describes it.

Opening Face Unlock settings can still stall authentication and model removal.
The new log shows FaceRevokeChallengeClient stuck after an ignored error from
the HIDL adapter's shared-challenge path. The watchdog later clears its queue.
patches/README.md documents the targeted completion fix and regression checks.
The user subsequently confirmed Face Unlock works after this challenge fix.
Apply the guarded framework fixes from the ROM root before building:

    python3 device/google/coral/faceunlock/tools/apply_framework_fixes.py --apply

This guarded framework patch must be applied separately from syncing the device
and vendor trees. Its source and regression validation do not establish phone
runtime success; repeat the Settings, unlock and model removal flow after flashing.

## Enrollment poses and stock-style guidance

The current Settings enrollment screen uses a circular preview and progress ring.
The moving pose boxes from stock Pixel enrollment have not been ported.

The Android 17 framework requests REQUIRE_DIVERSE_POSES for ordinary enrollment.
The unpatched HIDL adapter incorrectly adds that enabled feature to the native
disabledFeatures list. The second framework patch preserves the requested pose
requirement and keeps the native attention default. It also honors the framework's
reduced-pose request for limited mobility.

The apply script checks both known adapter methods and the incoming feature
builder in FaceEnrollClient.java before writing. It retains an existing challenge
fix, backs up the current adapter, and refuses unfamiliar source. See
patches/README.md for the interface evidence and regression validation.

This change applies to new enrollments after rebuilding and flashing. It does
not add the moving box overlay or establish stock-equivalent pose capture on a
phone; those need the corresponding UI/pose feedback integration and runtime
validation. Existing enrolled models are kept.

The earlier logcat/dmesg files capture the booted fallback image, not the
failed boot itself. They do not establish why the first flash failed to
boot. The successful again run uses slot B and reaches boot_completed.

Before flashing, verify the build outputs:

    readelf -d out/target/product/coral/vendor/bin/hw/android.hardware.biometrics.face@1.0-service.google | rg 'libcamera2ndk_face|libstagefright_foundation-v33'
    readelf -d out/target/product/coral/vendor/lib64/libcamera2ndk_face.so | rg 'SONAME|libstagefright_foundation-v33'
    readelf -Ws out/target/product/coral/vendor/lib64/libautocalsolvers.so | rg Autocalibrate

The HAL init service declares its HIDL interface so hwservicemanager can
request startup after a crash. Keep raw enrollment logs from all buffers
and the crash buffer immediately after the next enrollment attempt.


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

## Remaining validation

Rebuild both features-experimental trees and test enrollment on the phone.
Source, ELF and header checks do not establish runtime enrollment success.
The same logs also contain a separate Citadel Keymaster abort in
export_key_der. That failure is still under investigation.

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

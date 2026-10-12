# Pixel 4 XL Face HAL camera compatibility

The Android 13 Face HAL uses native_handle_t camera windows. The current
Android 17 vendor Camera NDK uses ANativeWindow and the AIDL camera service.
Loading the current library into the stock Face HAL can crash in
CameraDevice::configureStreamsLocked.

The libcamera2ndk_v33_face module installs /vendor/lib64/libcamera2ndk_face.so.
The Face HAL's DT_NEEDED entry names that library explicitly; it does not
depend on LD_LIBRARY_PATH or a duplicate libcamera2ndk_vendor.so filename.
coral/extract-files.py applies the same dependency replacement to future
factory-image extractions. The vendor prebuilt's shared_libs entry must
name libcamera2ndk_v33_face.

The Face HAL init service declares its HIDL interface so hwservicemanager
can request startup after a crash. Existing permissions and firmware remain
unchanged.

After updating both the device and vendor features-experimental branches,
build lineage_coral-userdebug again and reboot into the updated ROM. A stuck
biometric scheduler can leave the enrollment consent button disabled until
the reboot.

Check the build outputs before flashing:

    readelf -d out/target/product/coral/vendor/bin/hw/android.hardware.biometrics.face@1.0-service.google | rg 'libcamera2ndk'
    readelf -d out/target/product/coral/vendor/lib64/libcamera2ndk_face.so | rg SONAME

Both should name libcamera2ndk_face.so. The HAL must not need
libcamera2ndk_vendor.so. If enrollment still fails, collect logcat from all
buffers and the crash buffer immediately after the attempt.

## Android 13 callback ABI

Android 17's AHandler adds a mutex and delivery-tracking fields after the
Android 13 object layout. The stock Face HAL's CameraHelper::CallbackHandler
is built with the smaller Android 13 base, so the modern deliverMessage can
access its derived fields as a mutex before delivering image callbacks. A
capture session can open successfully, then fill its image buffers and fail
with Camera3-OutputStream dequeue timeout (-110).

The Face HAL and its camera compatibility library both link directly to
libstagefright_foundation-v33.so from hardware/lineage/compat. The camera
compatibility library uses the Apache-2.0 Android 13 AHandler definition in
compat/AHandler.h; the ALooper and AMessage object layouts used by this
library remain compatible with v33. Current framework and system media
libraries continue using their normal foundation library.

The extractor applies the foundation dependency replacement alongside the
camera dependency replacement. Check the built Face HAL and
libcamera2ndk_face.so with readelf -d: both must need
libstagefright_foundation-v33.so. After boot, inspect the Face HAL process
maps to confirm the versioned vendor library is loaded.

This addresses the callback ABI mismatch; complete enrollment still needs
validation on the phone. The separate Citadel Keymaster exportKey crash is
not changed by this fix.

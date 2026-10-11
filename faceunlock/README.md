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

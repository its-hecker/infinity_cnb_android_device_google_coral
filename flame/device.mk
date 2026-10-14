#
# SPDX-FileCopyrightText: 2016 The Android Open Source Project
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

PRODUCT_HARDWARE := flame

include device/google/coral/device-common.mk

# Audio
PRODUCT_COPY_FILES += \
    device/google/coral/audio/audio_policy_volumes_flame.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml

# Bluetooth
PRODUCT_COPY_FILES += \
    device/google/coral/configs/bluetooth/bluetooth_power_limits_flame.csv:$(TARGET_COPY_OUT_VENDOR)/etc/bluetooth_power_limits.csv

# Boot Animation
TARGET_SCREEN_HEIGHT := 2280
TARGET_SCREEN_WIDTH := 1080

# CHRE
$(call soong_config_set,chre,chre_daemon_dsp_library,//vendor/google/flame:libsdsprpc)

# Display
PRODUCT_COPY_FILES += \
    device/google/coral/configs/displayconfig/display_19261132550654593.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/displayconfig/display_19261132550654593.xml

# Properties
TARGET_PRODUCT_PROP += $(LOCAL_PATH)/flame/product.prop

# Overlays
DEVICE_PACKAGE_OVERLAYS += $(LOCAL_PATH)/flame/overlay

PRODUCT_PACKAGES += \
    SettingsOverlayG020I \
    SettingsOverlayG020I_VN \
    SettingsOverlayG020M \
    SettingsOverlayG020N

# HIDL vendor interfaces required by the camera and face HALs.
# thermal@2.0 stopped being built implicitly after Android 14 QPR1; without it
# the Google camera provider and the face HAL both crash (front and rear camera).
# See LineageOS 3ec0957b ("Build android.hardware.thermal@2.0").
PRODUCT_PACKAGES += \
    android.hardware.thermal@2.0.vendor:64 \
    android.hardware.biometrics.face@1.0.vendor:64 \
    libcamera2ndk_v33_face \
    libstagefright_foundation-v33

# Android 13 BoringSSL ABI required by the Citadel Keymaster used by Face Unlock.
PRODUCT_PACKAGES += \
    libcrypto-v33

# Face unlock (3D, android.hardware.biometrics.face@1.0-service.google)
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.biometrics.face.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.biometrics.face.xml

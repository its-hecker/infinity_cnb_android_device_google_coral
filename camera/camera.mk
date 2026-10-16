# Original Google Camera 8.7.250.494820638.44 with its signed feature splits.
# Defined in vendor/google/flame/camera; only lineage_flame inherits this file.
PRODUCT_PACKAGES += \
    GoogleCameraFlame

PRODUCT_COPY_FILES += \
    device/google/coral/camera/default-permissions-google-camera.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/default-permissions/default-permissions-google-camera-flame.xml \
    device/google/coral/camera/google-camera-flame.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/google-camera-flame.xml

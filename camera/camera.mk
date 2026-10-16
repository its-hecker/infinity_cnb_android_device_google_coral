# Original Google Camera 8.7.250.494820638.44, with its signed feature splits.
# Defined in vendor/google/coral/camera; only lineage_coral inherits this file.
PRODUCT_PACKAGES += \
    GoogleCameraCoral

PRODUCT_COPY_FILES += \
    device/google/coral/camera/default-permissions-google-camera.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/default-permissions/default-permissions-google-camera-coral.xml \
    device/google/coral/camera/google-camera-coral.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/google-camera-coral.xml

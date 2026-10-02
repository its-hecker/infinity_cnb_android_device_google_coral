#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
# Infinity-X Build Flags
INFINITY_BUILD := coral
INFINITY_MAINTAINER := "Hecker"
TARGET_HAS_UDFPS := false
WITH_GAPPS := true

# Build mainline modules from source and ignore apex contributions
PRODUCT_BUILD_IGNORE_APEX_CONTRIBUTION_CONTENTS := true
PRODUCT_MODULE_BUILD_FROM_SOURCE := true

# Infinity-X About Phone Definitions
PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.marketname=Google Pixel 4 XL \
    ro.infinity.soc=Snapdragon 855 \
    ro.infinity.camera=12.2MP + 16MP

# Inherit some common Lineage stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# Enable mainline checking
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

#
# All components inherited here go to system_ext image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)

#
# All components inherited here go to vendor image
#
# TODO(b/136525499): move *_vendor.mk into the vendor makefile later
TARGET_SUPPORTS_OMX_SERVICE := false
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_vendor.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_vendor.mk)

include device/google/coral/coral/device.mk

# Device identifier. This must come after all inclusions
PRODUCT_BRAND := google
PRODUCT_DEVICE := coral
PRODUCT_MANUFACTURER := Google
PRODUCT_MODEL := Pixel 4 XL
PRODUCT_NAME := lineage_coral

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="coral-user 13 TP1A.221005.002.B2 9382335 release-keys" \
    BuildFingerprint=google/coral/coral:13/TP1A.221005.002.B2/9382335:user/release-keys \
    DeviceProduct=coral

$(call inherit-product, vendor/google/coral/coral-vendor.mk)

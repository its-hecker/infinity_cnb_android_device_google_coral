#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# The reference Java method is Copyright (C) 2023 The Android Open Source Project.
"""Apply guarded HIDL Face challenge and enrollment pose compatibility fixes."""

import argparse
import hashlib
import os
from pathlib import Path
import stat
import sys
import tempfile


TARGET = Path("frameworks/base/services/core/java/com/android/server/biometrics/"
              "sensors/face/hidl/HidlToAidlSessionAdapter.java")
ORIGINAL_METHOD = """    @Override
    public void revokeChallenge(long challenge) throws RemoteException {
        final boolean shouldRevoke = decrementChallengeCount() == 0;
        if (!shouldRevoke) {
            Slog.w(TAG, "scheduleRevokeChallenge skipped - challenge still in use: "
                    + mGeneratedChallengeCount);
            mHidlToAidlCallbackConverter.onError(0 /* deviceId */, mUserId,
                    BiometricFaceConstants.FACE_ERROR_UNABLE_TO_PROCESS, 0 /* vendorCode */);
            return;
        }
        mGenerateChallengeCreatedAt = INVALID_VALUE;
        mGenerateChallengeResult = INVALID_VALUE;
        mSession.get().revokeChallenge();
        mHidlToAidlCallbackConverter.onChallengeRevoked(0L);
    }
"""
ORIGINAL_CALLBACK = """            mHidlToAidlCallbackConverter.onError(0 /* deviceId */, mUserId,
                    BiometricFaceConstants.FACE_ERROR_UNABLE_TO_PROCESS, 0 /* vendorCode */);"""
FIXED_CALLBACK = """            // Release this client's reference without invalidating another client's challenge.
            // FaceRevokeChallengeClient needs its completion callback to advance the queue.
            mHidlToAidlCallbackConverter.onChallengeRevoked(challenge);"""
FIXED_METHOD = ORIGINAL_METHOD.replace(ORIGINAL_CALLBACK, FIXED_CALLBACK)

AIDL_CLIENT = Path("frameworks/base/services/core/java/com/android/server/biometrics/"
                   "sensors/face/aidl/FaceEnrollClient.java")
ORIGINAL_ENROLL_METHOD = """    @Override
    public ICancellationSignal enroll(HardwareAuthToken hat, byte type, byte[] features,
            NativeHandle previewSurface) throws RemoteException {
        final ArrayList<Byte> token = new ArrayList<>();
        final byte[] hardwareAuthTokenArray = HardwareAuthTokenUtils.toByteArray(hat);
        for (byte b : hardwareAuthTokenArray) {
            token.add(b);
        }
        final ArrayList<Integer> disabledFeatures = new ArrayList<>();
        for (byte b: features) {
            disabledFeatures.add(AidlConversionUtils.convertAidlToFrameworkFeature(b));
        }
        mSession.get().enroll(token, ENROLL_TIMEOUT_SEC, disabledFeatures);
        return new Cancellation();
    }
"""
ORIGINAL_ENROLL_FEATURES = """        final ArrayList<Integer> disabledFeatures = new ArrayList<>();
        for (byte b: features) {
            disabledFeatures.add(AidlConversionUtils.convertAidlToFrameworkFeature(b));
        }
"""
FIXED_ENROLL_FEATURES = """        final ArrayList<Integer> disabledFeatures = new ArrayList<>();
        boolean requireDiversePoses = false;
        for (byte b: features) {
            final int feature = AidlConversionUtils.convertAidlToFrameworkFeature(b);
            if (feature == BiometricFaceConstants.FEATURE_REQUIRE_REQUIRE_DIVERSITY) {
                requireDiversePoses = true;
            }
        }
        // AIDL requests enabled features; HIDL expects features to disable.
        if (!requireDiversePoses) {
            disabledFeatures.add(BiometricFaceConstants.FEATURE_REQUIRE_REQUIRE_DIVERSITY);
        }
        // Preserve HIDL's attention default: the AIDL client does not request it here.
"""
FIXED_ENROLL_METHOD = ORIGINAL_ENROLL_METHOD.replace(
    ORIGINAL_ENROLL_FEATURES, FIXED_ENROLL_FEATURES)
AIDL_FEATURE_BUILDER = """            List<Byte> featureList = new ArrayList<Byte>();
            if (mDebugConsent) {
                featureList.add(Feature.DEBUG);
            }

            boolean shouldAddDiversePoses = true;
            for (int disabledFeature : mDisabledFeatures) {
                if (AidlConversionUtils.convertFrameworkToAidlFeature(disabledFeature)
                        == Feature.REQUIRE_DIVERSE_POSES) {
                    shouldAddDiversePoses = false;
                }
            }

            if (shouldAddDiversePoses) {
                featureList.add(Feature.REQUIRE_DIVERSE_POSES);
            }

            final byte[] features = new byte[featureList.size()];
            for (int i = 0; i < featureList.size(); i++) {
                features[i] = featureList.get(i);
            }
"""


class UnsupportedSource(ValueError):
    pass


def replace_method(content, original, fixed, name):
    """Accept one exact known method; preserve every other byte and newline style."""
    newline = b"\r\n" if b"\r\n" in content else b"\n"
    before = original.replace("\n", newline.decode()).encode()
    after = fixed.replace("\n", newline.decode()).encode()
    before_count, after_count = content.count(before), content.count(after)
    if before_count == 0 and after_count == 1:
        return content, False
    if before_count != 1 or after_count != 0:
        raise UnsupportedSource(
            f"Unrecognized {name} method; no files changed. "
            "Provide HidlToAidlSessionAdapter.java for review instead of forcing the patch.")
    return content.replace(before, after, 1), True


def transform(content):
    updated, challenge_changed = replace_method(
        content, ORIGINAL_METHOD, FIXED_METHOD, "revokeChallenge")
    updated, enroll_changed = replace_method(
        updated, ORIGINAL_ENROLL_METHOD, FIXED_ENROLL_METHOD, "enroll")
    return updated, "patch available" if challenge_changed or enroll_changed else "already applied"


def validate_client(content):
    normalized = content.replace(b"\r\n", b"\n")
    if normalized.count(AIDL_FEATURE_BUILDER.encode()) != 1:
        raise UnsupportedSource(
            "Unrecognized AIDL enrollment feature builder; no files changed. "
            "Provide FaceEnrollClient.java for review instead of forcing the patch.")


def apply(rom_root, write=False):
    target = rom_root / TARGET
    original = target.read_bytes()
    updated, status = transform(original)
    validate_client((rom_root / AIDL_CLIENT).read_bytes())
    if status == "already applied" or not write:
        return status, None

    # Keep backups outside Java source globs, and name them by original content.
    backup_dir = rom_root / "out/face-framework-patches"
    backup_dir.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256(original).hexdigest()
    backup = backup_dir / f"HidlToAidlSessionAdapter.{digest}.java.original"
    try:
        with backup.open("xb") as handle:
            handle.write(original)
    except FileExistsError:
        if backup.read_bytes() != original:
            raise UnsupportedSource("Existing backup has different content; no source changed.")

    mode = stat.S_IMODE(target.stat().st_mode)
    descriptor, temporary_name = tempfile.mkstemp(prefix=".face-fix-", dir=target.parent)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(updated)
            handle.flush()
            os.fsync(handle.fileno())
        os.chmod(temporary_name, mode)
        if target.read_bytes() != original:
            raise UnsupportedSource("Source changed during patch preparation; no source replaced.")
        os.replace(temporary_name, target)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)
    return "applied", backup


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom-root", type=Path, default=Path.cwd(),
                        help="ROM checkout root (default: current directory)")
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--check", action="store_true", help="Validate without writing")
    action.add_argument("--apply", action="store_true", help="Back up and patch the known methods")
    args = parser.parse_args()
    try:
        status, backup = apply(args.rom_root.resolve(), write=args.apply)
    except (OSError, UnsupportedSource) as error:
        print(f"Face framework fixes: {error}", file=sys.stderr)
        return 1
    print(f"Face framework fixes: {status}")
    if backup:
        print(f"Backup: {backup}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

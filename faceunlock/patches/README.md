# Face HIDL framework compatibility

The guarded apply script covers shared challenge completion and enrollment pose
requirements. Both changes target the same adapter, and are reviewed as separate
unified diffs. Each known method and the AIDL enrollment feature builder must match
before any source is written. An existing challenge fix is kept while the pose
fix is added.

## Shared challenge queue completion

After working enrollment, opening Face Unlock settings can leave authentication
and face removal waiting behind a `FaceRevokeChallengeClient`.

The October 12 `faceid-fixed-logcat.txt` records this sequence:

- 19:18:52.803: the HIDL adapter skips native revocation because other clients
  still use its cached challenge.
- It sends an error, which `AidlResponseHandler` ignores because the current
  revoke client does not implement `ErrorConsumer`.
- Face removal and authentication accumulate in the scheduler queue.
- 19:25:10.492: the watchdog cancels the stuck revoke and queued operations.

These logs contain no native crash markers. Watchdog recovery is consistent with
the reported return of Face Unlock; the log does not establish that attaching
USB caused recovery or that the cancelled removal requests deleted a model.

The patch completes the requesting client's reference release with
`onChallengeRevoked(challenge)`. Other clients keep their cached challenge. The
challenge reference count, expiry, last-reference native revocation, face models
and authentication checks retain their existing behavior. The separate original
last-reference `onChallengeRevoked(0L)` completion remains unchanged.

## Apply before the next ROM build

From the ROM checkout root, after syncing the experimental device/vendor trees:

```sh
python3 device/google/coral/faceunlock/tools/apply_framework_fixes.py --check
python3 device/google/coral/faceunlock/tools/apply_framework_fixes.py --apply
```

The script checks the known `revokeChallenge` and `enroll` methods and the AIDL
feature builder before changing the adapter. Unknown methods are refused. It preserves unrelated source bytes and
file permissions, stores the original under `out/face-framework-patches/`, and
does not change an already-patched file. The unified diff is included for review.
If the script refuses a different method, provide the indicated adapter or
FaceEnrollClient.java source file for review.
Re-run it after syncing `frameworks/base`; the device repository cannot make a
local framework change persist across an upstream framework reset by itself.

The ROM uses Project Infinity-X's Gitea framework tree. Its exact server checkout
has not been inspected remotely. The checked compatibility reference is
[LineageOS Android 17 HIDL adapter](https://github.com/LineageOS/android_frameworks_base/blob/6cdc375a23b72398dbe0358832c9305d31b802aa/services/core/java/com/android/server/biometrics/sensors/face/hidl/HidlToAidlSessionAdapter.java),
whose logged branch and error behavior match the failure. The exact-method guards
check compatibility on the build server instead of assuming its source revision.

## Enrollment pose requirement

The current Settings UI shows a preview circle and progress ring. Stock Pixel's
moving pose boxes are separate UI work. The HIDL callback converter forwards
enrollment completion counts and acquisition/help codes; the detailed AIDL
enrollment interface supports additional pose-cell feedback. A stock-style grid
needs its corresponding pose feedback mapping, rather than treating a count as
measured head position.

There is also a functional feature translation error in the reference framework:

- `FaceEnrollClient.startHalOperation()` adds the enabled
  `Feature.REQUIRE_DIVERSE_POSES` flag by default.
- The original adapter converts every requested flag into a HIDL
  `disabledFeatures` entry, disabling the very pose requirement being requested.
- The HIDL interface specifies that features are enabled by default and its
  enrollment parameter lists features to disable.

The correction keeps HIDL's diversity requirement enabled when requested, and
disables it when the framework explicitly omits it for reduced-pose enrollment.
The AIDL client does not include attention in this enrollment feature builder, so
the adapter preserves HIDL's attention default. Explicit positive attention flags
also keep that feature enabled. Unsupported feature behavior, the HAT bytes,
timeout and cancellation path retain their existing behavior.

| Framework enrollment request | HIDL disabled features |
| --- | --- |
| Default diverse poses | Empty; native diversity stays enabled |
| Reduced poses for limited mobility | Diversity only |
| Positive attention and diverse-pose flags | Empty |

The guard checks the actual incoming feature builder, as well as the complete
known adapter method. The code fix is established for that matched source;
whether it explains a particular phone's quick enrollment remains a runtime
inference. The correction changes future enrollments after a ROM rebuild and
flash. It does not retrofit a stored face model or restore the stock box overlay.

Primary references:

- [Framework enabled-feature builder](https://github.com/LineageOS/android_frameworks_base/blob/6cdc375a23b72398dbe0358832c9305d31b802aa/services/core/java/com/android/server/biometrics/sensors/face/aidl/FaceEnrollClient.java)
- [Feature conversion](https://github.com/LineageOS/android_frameworks_base/blob/6cdc375a23b72398dbe0358832c9305d31b802aa/services/core/java/com/android/server/biometrics/sensors/face/aidl/AidlConversionUtils.java)
- [HIDL enrollment parameter contract](https://github.com/LineageOS/android_hardware_interfaces/blob/lineage-24.0/biometrics/face/1.0/IBiometricsFace.hal)
- [HIDL attention/diversity defaults](https://github.com/LineageOS/android_hardware_interfaces/blob/lineage-24.0/biometrics/face/1.0/types.hal)

## Validation

`.github/workflows/face-framework.yml` fetches the adapter and revoke client at
the pinned reference commit and verifies four Git blob hashes. Ten Python
checks cover the guarded application, untouched bytes, repeated application,
unknown source refusal, backup location, newline format, file permissions and
preserving the already-applied challenge fix.

The Java harness executes extracted original and patched adapter methods,
including the real reference-count/expiry method and revoke client's completion
callback. The original shared-challenge method is a negative control: its error
leaves the client unfinished. The patched method completes that client, keeps the
other client's nonce, and does not call native revocation. Empty/final references
and stale-reference expiry exercise the unchanged native revocation path.
Android transport and callback scheduling use test doubles; this is not a full
framework build or phone test.

The enrollment harness executes the extracted real framework feature builder,
both converters and original/patched adapter enrollment methods. Its original
source negative control shows default diverse poses becoming a disabled feature.
The corrected method preserves that requirement, the reduced-pose option and
native attention default. Test doubles check the forwarded token bytes, timeout,
cancellation and rejection of unsupported features; they do not validate secure
hardware token verification or actual captured pose coverage.

After rebuilding and flashing, test repeated visits to Face Unlock settings,
subsequent lock-screen authentication, and model removal/re-enrollment. Capture
all logcat buffers if the scheduler stops completing requests again.


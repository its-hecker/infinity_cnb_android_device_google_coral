# Face challenge queue completion

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

The script checks the entire known `revokeChallenge` method before changing its
callback. Unknown methods are refused. It preserves unrelated source bytes and
file permissions, stores the original under `out/face-framework-patches/`, and
does not change an already-patched file. The unified diff is included for review.
If the script refuses a different method, provide that source file for review.
Re-run it after syncing `frameworks/base`; the device repository cannot make a
local framework change persist across an upstream framework reset by itself.

The ROM uses Project Infinity-X's Gitea framework tree. Its exact server checkout
has not been inspected remotely. The checked compatibility reference is
[LineageOS Android 17 HIDL adapter](https://github.com/LineageOS/android_frameworks_base/blob/6cdc375a23b72398dbe0358832c9305d31b802aa/services/core/java/com/android/server/biometrics/sensors/face/hidl/HidlToAidlSessionAdapter.java),
whose logged branch and error behavior match the failure. The exact-method guard
checks compatibility on the build server instead of assuming its source revision.

## Validation

`.github/workflows/face-framework.yml` fetches the adapter and revoke client at
the pinned reference commit and verifies both Git blob hashes. Seven Python
checks cover the guarded application, untouched bytes, repeated application,
unknown source refusal, backup location, newline format and file permissions.

The Java harness executes extracted original and patched adapter methods,
including the real reference-count/expiry method and revoke client's completion
callback. The original shared-challenge method is a negative control: its error
leaves the client unfinished. The patched method completes that client, keeps the
other client's nonce, and does not call native revocation. Empty/final references
and stale-reference expiry exercise the unchanged native revocation path.
Android transport and callback scheduling use test doubles; this is not a full
framework build or phone test.

After rebuilding and flashing, test repeated visits to Face Unlock settings,
subsequent lock-screen authentication, and model removal/re-enrollment. Capture
all logcat buffers if the scheduler stops completing requests again.

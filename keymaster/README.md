# Citadel Keymaster crypto compatibility

The Pixel 4 stock NOS Keymaster implementation allocates its crypto objects
using the Android 13 BoringSSL ABI. Android 17's current libcrypto is not a
replacement for that private object layout.

The October 12 process maps showed libcrypto.so and libcrypto_shim.so in the
running Citadel service, with no libcrypto-v33.so. The earlier
export_key_der crash trips the stack protector. The function places its CBB
at SP+16 and its canary at SP+56; the v33 CBB_init_fixed implementation
initializes a 32-byte object. The source pairing is required even when C
function names remain compatible.

The repair gives the service a first DT_NEEDED entry for libcrypto-v33.so.
The NOS implementation also directly needs libcrypto-v33.so and no longer
needs libcrypto.so or libcrypto_shim.so. All 47 imported crypto functions
exist in the pinned compatibility library.

coral/device.mk installs libcrypto-v33 from hardware/lineage/compat.
coral/extract-files.py preserves both explicit dependencies when extracting
a stock build. The init service retains its hsm user and drmrpc group and
does not depend on an LD_PRELOAD environment variable.

Bionic ignores LD_PRELOAD when AT_SECURE is set:
https://android.googlesource.com/platform/bionic/+/refs/heads/main/linker/linker_main.cpp
The maps alone do not establish whether secure launch handling or an older
installed init file prevented the previous preload.

Vendor tools/check_keymaster_crypto.py verifies the dependency order,
crypto exports, Soong entries and extraction hooks. It compares the patched
binaries to their pinned pre-fix versions and requires unchanged executable
mappings, code, constants, call stubs, unwind data, build IDs and dynamic
symbol semantics. Patchelf may move ELF note/section metadata.

These are ELF/source checks. Phone boot, Keymaster export and Face ID
enrollment still require runtime testing.

After flashing a rebuilt ROM, verify libcrypto-v33.so appears in the
Citadel process maps and collect a fresh crash buffer plus a full logcat
from an enrollment attempt. Other dependencies can still map modern crypto;
the direct v33 binding and first dependency order are the relevant checks.

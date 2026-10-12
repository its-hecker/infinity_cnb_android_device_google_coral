#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Test safe application and execute extracted framework challenge methods with Java."""

import argparse
import difflib
import hashlib
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


FACE_DIR = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "face_fixes", FACE_DIR / "tools/apply_framework_fixes.py")
fixes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixes)

# LineageOS/android_frameworks_base lineage-24.0 at this exact commit.
REFERENCE_COMMIT = "6cdc375a23b72398dbe0358832c9305d31b802aa"
REFERENCE_BLOBS = {
    "HidlToAidlSessionAdapter.java": "45d0cfec9fb2f060cae4b9de509b02ccef005d83",
    "FaceRevokeChallengeClient.java": "0d6143a7d0f080eba9ddafed5cd973f93beb8960",
}
reference = None


def git_blob(content):
    return hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()


def extract_method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    position = opening + 1
    while depth and position < len(source):
        depth += (source[position] == "{") - (source[position] == "}")
        position += 1
    if depth:
        raise ValueError("Unclosed Java method")
    return source[start:position]


class PatchApplicationTests(unittest.TestCase):
    def test_only_known_callback_changes(self):
        updated, status = fixes.transform(reference)
        self.assertEqual(status, "patch available")
        self.assertEqual(updated, reference.replace(
            fixes.ORIGINAL_CALLBACK.encode(), fixes.FIXED_CALLBACK.encode(), 1))
        self.assertEqual(fixes.transform(updated), (updated, "already applied"))

    def test_refuses_modified_method(self):
        unknown = reference.replace(b"decrementChallengeCount() == 0",
                                    b"decrementChallengeCount() <= 0", 1)
        with self.assertRaises(fixes.UnsupportedSource):
            fixes.transform(unknown)

    def test_refuses_duplicate_or_mixed_methods(self):
        patched = fixes.transform(reference)[0]
        for unknown in (reference + fixes.ORIGINAL_METHOD.encode(),
                        patched + fixes.ORIGINAL_METHOD.encode(),
                        patched + fixes.FIXED_METHOD.encode()):
            with self.subTest(source=len(unknown)), self.assertRaises(fixes.UnsupportedSource):
                fixes.transform(unknown)

    def test_preserves_crlf_and_unrelated_customizations(self):
        customized = b"// ROM-local header\n" + reference
        customized = customized.replace(b"\n", b"\r\n")
        updated, _ = fixes.transform(customized)
        self.assertEqual(updated, customized.replace(
            fixes.ORIGINAL_CALLBACK.replace("\n", "\r\n").encode(),
            fixes.FIXED_CALLBACK.replace("\n", "\r\n").encode(), 1))

    def test_check_apply_backup_permissions_and_repeat(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            target = root / fixes.TARGET
            target.parent.mkdir(parents=True)
            target.write_bytes(reference)
            target.chmod(0o640)
            self.assertEqual(fixes.apply(root), ("patch available", None))
            self.assertEqual(target.read_bytes(), reference)
            self.assertFalse((root / "out").exists())
            status, backup = fixes.apply(root, write=True)
            self.assertEqual(status, "applied")
            self.assertEqual(backup.read_bytes(), reference)
            self.assertEqual(backup.parent, root / "out/face-framework-patches")
            self.assertEqual(target.stat().st_mode & 0o777, 0o640)
            self.assertEqual(target.read_bytes(), fixes.transform(reference)[0])
            first_mtime = target.stat().st_mtime_ns
            self.assertEqual(fixes.apply(root, write=True), ("already applied", None))
            self.assertEqual(target.stat().st_mtime_ns, first_mtime)
            self.assertEqual(len(list(backup.parent.iterdir())), 1)

    def test_unknown_source_cli_leaves_checkout_unchanged(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            target = root / fixes.TARGET
            target.parent.mkdir(parents=True)
            target.write_bytes(b"// Unknown framework revision\n")
            result = subprocess.run([
                "python3", str(FACE_DIR / "tools/apply_framework_fixes.py"),
                "--rom-root", str(root), "--apply"], capture_output=True, text=True)
            self.assertEqual(result.returncode, 1)
            self.assertIn("Unrecognized revokeChallenge method", result.stderr)
            self.assertEqual(target.read_bytes(), b"// Unknown framework revision\n")
            self.assertFalse((root / "out").exists())

    def test_review_patch_matches_actual_transformation(self):
        updated = fixes.transform(reference)[0]
        path = str(fixes.TARGET.relative_to("frameworks/base"))
        expected = "".join(difflib.unified_diff(
            reference.decode().splitlines(keepends=True),
            updated.decode().splitlines(keepends=True),
            fromfile="a/" + path, tofile="b/" + path))
        self.assertEqual((FACE_DIR / "patches/0001-face-complete-shared-challenge-revoke.patch")
                         .read_text(), expected)


def run_java(client_source):
    source = reference.decode()
    patched = fixes.transform(reference)[0].decode()
    markers = {
        "@@CLIENT_CALLBACK@@": extract_method(client_source,
            "    void onChallengeRevoked(int sensorId, int userId, long challenge)"),
        "@@DECREMENT_METHOD@@": extract_method(source, "    private int decrementChallengeCount()"),
        "@@ORIGINAL_REVOKE@@": extract_method(source,
            "    public void revokeChallenge(long challenge)"),
        "@@FIXED_REVOKE@@": extract_method(patched,
            "    public void revokeChallenge(long challenge)"),
    }
    java = (FACE_DIR / "tests/FaceChallengeQueueRegression.java.in").read_text()
    for marker, method in markers.items():
        java = java.replace(marker, method)
    if "@@" in java:
        raise ValueError("Unexpanded Java template marker")
    if not shutil.which("javac") or not shutil.which("java"):
        raise RuntimeError("Java is required for the regression harness (CI uses Temurin 17)")
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "FaceChallengeQueueRegression.java"
        path.write_text(java)
        subprocess.run(["javac", "-d", folder, str(path)], check=True)
        subprocess.run(["java", "-cp", folder, "FaceChallengeQueueRegression"], check=True)


def main():
    global reference
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-dir", required=True, type=Path)
    parser.add_argument("--python-only", action="store_true",
                        help="Run application tests only; full validation also requires Java")
    args = parser.parse_args()
    sources = {}
    for name, expected in REFERENCE_BLOBS.items():
        content = (args.reference_dir / name).read_bytes()
        if git_blob(content) != expected:
            raise ValueError(f"Reference Git blob mismatch: {name}")
        sources[name] = content
    reference = sources["HidlToAidlSessionAdapter.java"]
    tests = unittest.defaultTestLoader.loadTestsFromTestCase(PatchApplicationTests)
    if not unittest.TextTestRunner(verbosity=2).run(tests).wasSuccessful():
        return 1
    if not args.python_only:
        run_java(sources["FaceRevokeChallengeClient.java"].decode())
    print(f"PASS: pinned framework source {REFERENCE_COMMIT}; source application checks complete")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

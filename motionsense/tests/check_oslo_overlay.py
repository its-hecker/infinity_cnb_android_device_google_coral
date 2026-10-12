#!/usr/bin/env python3
"""Check the compiled Oslo RRO, its production allowlist and ROM packaging."""

from __future__ import annotations

import argparse
import importlib.util
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OVERLAY = ROOT / "rro_overlays/OsloSystemUIOverlay"
ANDROID = "{http://schemas.android.com/apk/res/android}"
COMPONENTS = {
    "com.google.oslo/com.google.oslo.OsloOverlay",
    "com.google.oslo/com.google.oslo.OsloSensorManager",
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--reader", required=True, type=Path)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("apk_resource_reader", args.reader)
    reader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(reader)

    blueprint = (OVERLAY / "Android.bp").read_text()
    for pattern in (r'name:\s*"GoogleOsloSystemUIOverlay"',
                    r'certificate:\s*"platform"', r'system_ext_specific:\s*true'):
        assert re.search(pattern, blueprint), "Missing RRO packaging rule: " + pattern
    assert "GoogleOsloSystemUIOverlay" in (ROOT / "device-common.mk").read_text()

    previous = ET.parse(ROOT / "overlay-oslo/frameworks/base/packages/SystemUI/res/values/config.xml")
    original = {n.text for n in previous.findall(".//string-array[@name='config_pluginAllowlist']/item")}
    expected = (original - {"com.google.oslo"}) | COMPONENTS
    with zipfile.ZipFile(args.apk) as package:
        assert not any(re.fullmatch(r"classes\d*\.dex", n) for n in package.namelist())
        manifest = reader.xml(package.read("AndroidManifest.xml"))
        assert manifest.get("package") == "org.lineageos.overlay.oslo.systemui"
        overlay = manifest.find("overlay")
        assert overlay is not None
        assert overlay.get(ANDROID + "targetPackage") == "com.android.systemui"
        assert overlay.get(ANDROID + "isStatic") == "true"
        assert overlay.get(ANDROID + "priority") == "999"
        table = reader.resources(package.read("resources.arsc"))
        arrays = [values for typ, name, values in table.values()
                  if typ == "array" and name == "config_pluginAllowlist"]
        assert len(arrays) == 1, "Expected one default compiled allowlist"
        items = [value[1] for _, value in arrays[0]]
        assert len(items) == len(set(items)), "Duplicate privileged entries"
        assert set(items) == expected, "Compiled allowlist differs from reviewed entries"
    print("Compiled static SystemUI overlay: PASS")
    print("system_ext install and platform signing rules: PASS")
    print("Existing Infinity plugin entries retained: PASS")
    print("Oslo overlay and sensor components included: PASS")
    print("No executable payload or custom wallpaper components: PASS")
    print("Phone overlay activation and Oslo behavior remain to be verified.")


if __name__ == "__main__":
    main()

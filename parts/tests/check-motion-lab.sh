#!/usr/bin/env bash
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_JAR:?Set ANDROID_JAR to the Android SDK android.jar}"
: "${AAPT2:?Set AAPT2 to the SDK build-tools aapt2}"
: "${R8_JAR:?Set R8_JAR to R8 8.7.18}"
LAB_WORK="$(mktemp -d)"
trap 'rm -rf "$LAB_WORK"' EXIT
mkdir -p "$LAB_WORK/tests" "$LAB_WORK/res/values" "$LAB_WORK/gen" "$LAB_WORK/classes" "$LAB_WORK/dex"
java com.sun.tools.javac.Main -d "$LAB_WORK/tests" \
    "$LAB_ROOT/src/org/lineageos/settings/motionsense/lab/LabEngine.java" \
    "$LAB_ROOT/tests/LabEngineTest.java"
java -cp "$LAB_WORK/tests" org.lineageos.settings.motionsense.lab.LabEngineTest
cp "$LAB_ROOT/res/values/motion_lab_strings.xml" "$LAB_WORK/res/values/"
cat > "$LAB_WORK/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.lineageos.settings">
    <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="35" />
    <application android:theme="@android:style/Theme.DeviceDefault.DayNight">
        <activity android:name=".motionsense.lab.ArcadeActivity" android:exported="false" android:label="@string/motion_lab_arcade" />
        <activity android:name=".motionsense.lab.TrainingActivity" android:exported="false" android:label="@string/motion_lab_training" />
        <activity android:name=".motionsense.lab.ControlPanelActivity" android:exported="false" android:label="@string/motion_lab_panel" />
        <activity android:name=".motionsense.lab.GlowStudioActivity" android:exported="false" android:label="@string/motion_lab_studio" />
    </application>
</manifest>
XML
"$AAPT2" compile --dir "$LAB_WORK/res" -o "$LAB_WORK/resources.zip"
"$AAPT2" link -I "$ANDROID_JAR" --manifest "$LAB_WORK/AndroidManifest.xml" \
    --java "$LAB_WORK/gen" -o "$LAB_WORK/resources.apk" "$LAB_WORK/resources.zip"
java com.sun.tools.javac.Main -source 8 -target 8 -classpath "$ANDROID_JAR" \
    -d "$LAB_WORK/classes" "$LAB_WORK/gen/org/lineageos/settings/R.java" \
    "$LAB_ROOT"/src/org/lineageos/settings/motionsense/lab/*.java
java -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 30 --lib "$ANDROID_JAR" \
    --output "$LAB_WORK/dex" "$LAB_WORK"/classes/org/lineageos/settings/motionsense/lab/*.class
test -s "$LAB_WORK/dex/classes.dex"
echo 'Motion Lab pages: resources, public Android API compilation and DEX generation passed'

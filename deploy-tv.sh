#!/bin/bash
set -e

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
DEVICE_IP="192.168.1.128:5555"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "=== Building debug APK ==="
./gradlew assembleDebug

echo "=== Connecting to Mi Box ==="
$ADB connect $DEVICE_IP
sleep 2

echo "=== Installing on device ==="
$ADB -s $DEVICE_IP install -r "$APK"

echo "=== Launching app ==="
$ADB -s $DEVICE_IP shell monkey -p lol.omnius.android -c android.intent.category.LEANBACK_LAUNCHER 1

echo "=== Done! ==="

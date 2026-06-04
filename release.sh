#!/bin/bash
# Build the Omnius (Kali) app and publish it so Negenet boxes auto-update it:
# the launcher reads apps.json from the release channel and silently installs a
# newer Omnius (root install, no dialog) — same mechanism as the launcher itself.
#
#   ./release.sh
set -e

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
RELEASES_REPO="negenet/negenet-launcher-releases"
APK_URL="https://github.com/$RELEASES_REPO/releases/download/apps/omnius.apk"

VER=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
VC=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
[ -z "$VER" ] || [ -z "$VC" ] && { echo "could not read version"; exit 1; }
echo "== building Omnius $VER (versionCode $VC) =="
./gradlew :app:assembleRelease -q
APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "no APK produced"; exit 1; }

echo "== uploading omnius.apk to 'apps' release =="
gh release view apps -R "$RELEASES_REPO" >/dev/null 2>&1 || \
  gh release create apps -R "$RELEASES_REPO" --latest=false -t "Bundled apps" -n "Omnius app APK."
cp "$APK" /tmp/omnius.apk
gh release upload apps /tmp/omnius.apk -R "$RELEASES_REPO" --clobber

echo "== updating apps.json manifest =="
printf '{\n  "omnius": { "versionCode": %s, "versionName": "%s", "apk": "%s" }\n}\n' \
  "$VC" "$VER" "$APK_URL" > /tmp/apps.json
SHA=$(gh api "repos/$RELEASES_REPO/contents/apps.json" --jq .sha 2>/dev/null || true)
gh api --method PUT "repos/$RELEASES_REPO/contents/apps.json" \
  -f message="omnius -> $VER (vc $VC)" \
  -f content="$(base64 -i /tmp/apps.json)" ${SHA:+-f sha="$SHA"} >/dev/null

echo "Done. Boxes auto-update Omnius to $VER (vc $VC) on next check."

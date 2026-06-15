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
APP_KEY="omnius"
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

echo "== merging '$APP_KEY' into apps.json (preserving other apps) =="
# Read the current manifest via the API (no CDN cache) and update ONLY our key, so
# this never clobbers negenet-tv (or any other managed app) already in the manifest.
CONTENT_B64=$(gh api "repos/$RELEASES_REPO/contents/apps.json" --jq '.content' 2>/dev/null | tr -d '\n' || true)
SHA=$(gh api "repos/$RELEASES_REPO/contents/apps.json" --jq .sha 2>/dev/null || true)
APP_KEY="$APP_KEY" VC="$VC" VER="$VER" APK_URL="$APK_URL" \
  python3 - "$CONTENT_B64" > /tmp/apps.json <<'PY'
import base64, json, os, sys
b64 = sys.argv[1] if len(sys.argv) > 1 else ""
try:
    cur = base64.b64decode(b64).decode("utf-8") if b64 else "{}"
    data = json.loads(cur) if cur.strip() else {}
    if not isinstance(data, dict):
        data = {}
except Exception:
    data = {}
data[os.environ["APP_KEY"]] = {
    "versionCode": int(os.environ["VC"]),
    "versionName": os.environ["VER"],
    "apk": os.environ["APK_URL"],
}
print(json.dumps(data, indent=2))
PY

gh api --method PUT "repos/$RELEASES_REPO/contents/apps.json" \
  -f message="$APP_KEY -> $VER (vc $VC)" \
  -f content="$(base64 -i /tmp/apps.json)" ${SHA:+-f sha="$SHA"} >/dev/null

echo "Done. Boxes auto-update Omnius to $VER (vc $VC) on next check."

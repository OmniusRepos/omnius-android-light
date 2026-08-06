#!/usr/bin/env bash
# Publish a build to Aptoide.
#
# Aptoide is how Omnius reaches boxes: nothing in the Negenet platform ships it,
# and this project's own OTA feed was removed rather than run a second update
# path. So this script is the release channel.
#
#   ./publish-aptoide.sh                    publish the built release APK
#   APK=path/to.apk ./publish-aptoide.sh    publish a specific file
#
# Needs an Aptoide Connect API key in ~/base-al/creds/aptoide.env:
#   APTOIDE_API_KEY=...
set -euo pipefail

ENV_FILE="${APTOIDE_ENV:-$HOME/base-al/creds/aptoide.env}"
[ -f "$ENV_FILE" ] || { echo "missing $ENV_FILE (needs APTOIDE_API_KEY=...)"; exit 1; }
set -a; . "$ENV_FILE"; set +a
: "${APTOIDE_API_KEY:?APTOIDE_API_KEY is not set in $ENV_FILE}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="${APK:-$HERE/app/build/outputs/apk/release/app-release.apk}"

# The file is uploaded directly rather than fetched from a URL. Aptoide supports
# both, but a URL would mean hosting the APK somewhere public - and the only
# public host this project had was Negenet's release repo, which is exactly the
# dependency Omnius is being separated from.
[ -f "$APK" ] || { echo "no APK at $APK - run ./release.sh or ./gradlew :app:assembleRelease first"; exit 1; }

AAPT="$(ls "$HOME/Library/Android/sdk/build-tools"/*/aapt2 2>/dev/null | tail -1)"
if [ -n "$AAPT" ]; then
  echo "==> submitting $("$AAPT" dump badging "$APK" | grep -oE "versionName='[^']*'" | head -1)"
fi

out=$(curl -sS -w '\n%{http_code}' -X POST 'https://uploader.catappult.io/api' \
        -H "Api-Key: $APTOIDE_API_KEY" \
        -F "apk=@$APK")
status=$(printf '%s' "$out" | tail -1)
body=$(printf '%s' "$out" | sed '$d')

echo "  HTTP $status"
printf '%s\n' "$body" | head -20

if [ "$status" = "200" ]; then
  # A 200 means accepted, NOT live. Saying "published" here would have someone
  # checking a box for an update still sitting in a review queue.
  echo "==> accepted - PENDING ADMIN APPROVAL at Aptoide. It reaches devices only once approved."
else
  echo "==> submission failed" >&2
  exit 1
fi

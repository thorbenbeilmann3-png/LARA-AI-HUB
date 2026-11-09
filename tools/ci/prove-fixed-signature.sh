#!/bin/sh
# End-to-end proof in CI with a throwaway key created here and thrown away after
# the job: two builds with the same key get its certificate, a build without it
# gets another one. Never uses the real secret.
set -eu

apk=app/build/outputs/apk/debug/app-debug.apk
apksigner=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -n 1)
store="${ANDROID_USER_HOME:-$HOME/.android}/debug.keystore"

work=$(mktemp -d)
trap 'rm -rf "$work"; rm -f "$store"' EXIT

cert_of_apk() {
    "$apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n 1
}

rm -f "$store"
mkdir -p "$work/home"
HOME="$work/home" sh tools/erzeuge-lara-signatur.sh >/dev/null
LARA_CI_KEYSTORE_B64=$(cat "$work/home/lara-signatur/lara-ci-keystore.b64") GITHUB_OUTPUT="$work/output" \
    sh tools/ci/install-signing-key.sh
expected=$(sed -n 's/^expected_cert=//p' "$work/output")
[ -n "$expected" ]

gradle --no-daemon :app:assembleDebug
first=$(cert_of_apk)
gradle --no-daemon clean :app:assembleDebug
second=$(cert_of_apk)

rm -f "$store"
gradle --no-daemon clean :app:assembleDebug
without_key=$(cert_of_apk)

echo "expected:    $expected"
echo "first:       $first"
echo "second:      $second"
echo "without key: $without_key"

[ "$first" = "$expected" ]
[ "$second" = "$expected" ]
[ "$without_key" != "$expected" ]
echo "::notice title=Signing proof::Same key, same certificate over two builds; without the key the certificate differs."

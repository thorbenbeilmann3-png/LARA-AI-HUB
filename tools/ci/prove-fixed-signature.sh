#!/bin/sh
# End-to-end proof in CI with a throwaway key created here and thrown away after
# the job: two builds with the same key get its certificate, a build without it
# gets another one. Never uses the real secret.
set -eu

apk=app/build/outputs/apk/debug/app-debug.apk
apksigner=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -n 1)

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cert_of_apk() {
    "$apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n 1
}

mkdir -p "$work/home"
HOME="$work/home" sh tools/erzeuge-lara-signatur.sh >/dev/null
LARA_CI_KEYSTORE_B64=$(cat "$work/home/lara-signatur/lara-ci-keystore.b64") \
    RUNNER_TEMP="$work" GITHUB_OUTPUT="$work/output" GITHUB_ENV="$work/env" \
    sh tools/ci/install-signing-key.sh
expected=$(sed -n 's/^expected_cert=//p' "$work/output")
LARA_CI_KEYSTORE_PATH=$(sed -n 's/^LARA_CI_KEYSTORE_PATH=//p' "$work/env")
[ -n "$expected" ]
[ -s "$LARA_CI_KEYSTORE_PATH" ]
export LARA_CI_KEYSTORE_PATH

gradle --no-daemon :app:assembleDebug
first=$(cert_of_apk)
gradle --no-daemon clean :app:assembleDebug
second=$(cert_of_apk)

unset LARA_CI_KEYSTORE_PATH
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

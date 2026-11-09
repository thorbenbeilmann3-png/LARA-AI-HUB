#!/bin/sh
# Installs the fixed CI signing key from the secret LARA_CI_KEYSTORE_B64 as the
# debug keystore, so every build gets the same certificate. Without a usable
# secret nothing changes and the build goes on with a generated debug key.
# Prints only status text and the public certificate digest, never the secret.
set -eu
umask 077

# Standard values of an Android debug key. The keystore file is the secret.
key_alias=androiddebugkey
key_pass=android

dest_dir="${ANDROID_USER_HOME:-$HOME/.android}"
dest="$dest_dir/debug.keystore"
output="${GITHUB_OUTPUT:-/dev/null}"

finish() {
    printf 'signing=%s\nexpected_cert=%s\n' "$1" "$2" >>"$output"
    exit 0
}

reject() {
    echo "::warning title=Signatur::$1 Der Build läuft mit wechselnder Debug-Signatur weiter. Anleitung: docs/FESTE-SIGNATUR.md"
    finish debug ""
}

if [ -z "${LARA_CI_KEYSTORE_B64:-}" ]; then
    echo "::notice title=Signatur::Kein Secret LARA_CI_KEYSTORE_B64 gesetzt. Der Build nutzt die wechselnde Debug-Signatur."
    finish debug ""
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
store="$work/keystore"

# Pasting on a phone can add line breaks, so whitespace is dropped first.
if ! printf '%s' "$LARA_CI_KEYSTORE_B64" | tr -d ' \t\r\n' | base64 -d >"$store" 2>/dev/null; then
    reject "Das Secret ist kein gültiger Base64-Text."
fi

if ! info=$(LC_ALL=C keytool -J-Duser.language=en -list -v -alias "$key_alias" \
    -keystore "$store" -storepass "$key_pass" 2>/dev/null); then
    reject "Das Secret enthält keinen lesbaren Schlüssel mit den erwarteten Standardwerten."
fi

# Listing does not touch the private key, signing a request does.
if ! LC_ALL=C keytool -J-Duser.language=en -certreq -alias "$key_alias" \
    -keystore "$store" -storepass "$key_pass" -keypass "$key_pass" \
    -file "$work/request" >/dev/null 2>&1; then
    reject "Der private Schlüssel im Secret lässt sich nicht benutzen."
fi

cert=$(printf '%s\n' "$info" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 | tr -d ':' | tr 'A-F' 'a-f')
if [ -z "$cert" ]; then
    reject "Das Zertifikat im Secret ließ sich nicht auslesen."
fi

mkdir -p "$dest_dir"
cp "$store" "$dest"

echo "::notice title=Signatur::Feste Signatur aktiv. Zertifikat SHA-256: $cert"
finish fixed "$cert"

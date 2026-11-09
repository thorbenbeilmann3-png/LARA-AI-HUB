#!/bin/sh
# Erzeugt einmalig den festen Signaturschluessel fuer die LARA-Testbuilds.
# Laeuft im Alpine-Terminal und schickt nichts ins Netz.
# Anleitung: docs/FESTE-SIGNATUR.md
#
# Alias und Passwort sind die oeffentlich bekannten Standardwerte eines
# Android-Debug-Schluessels. Geheim ist allein die Schluesseldatei.
set -eu
umask 077

dir="$HOME/lara-signatur"
p12="$dir/lara-ci-keystore.p12"
b64="$dir/lara-ci-keystore.b64"

if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl fehlt. Bitte zuerst eingeben: apk add --no-cache openssl" >&2
    exit 1
fi

if [ -e "$p12" ] || [ -e "$b64" ]; then
    echo "In $dir liegt schon ein Schluessel. Es wurde nichts geaendert." >&2
    echo "Ein neuer Schluessel wuerde Updates wieder unterbrechen." >&2
    exit 1
fi

mkdir -p "$dir"
tmp=$(mktemp -d "$dir/tmp.XXXXXX")
trap 'rm -rf "$tmp"' EXIT

# P-256 statt RSA: Der Text wird deutlich kuerzer und passt beim Kopieren
# auf einen Handybildschirm. LARA braucht mindestens Android 8 (minSdk 26).
openssl ecparam -name prime256v1 -genkey -noout -out "$tmp/key.pem" 2>/dev/null
openssl req -new -x509 -sha256 -key "$tmp/key.pem" -days 10950 \
    -subj "/CN=LARA Test/O=LARA AI HUB/C=DE" -out "$tmp/cert.pem" 2>/dev/null
openssl pkcs12 -export -inkey "$tmp/key.pem" -in "$tmp/cert.pem" \
    -name androiddebugkey -passout pass:android -out "$tmp/ks.p12" 2>/dev/null
openssl base64 -A -in "$tmp/ks.p12" -out "$tmp/ks.b64"

cert=$(openssl x509 -in "$tmp/cert.pem" -noout -fingerprint -sha256 \
    | sed 's/.*=//' | tr -d ':' | tr 'A-F' 'a-f')

mv "$tmp/ks.p12" "$p12"
mv "$tmp/ks.b64" "$b64"

echo "LARA-Signatur erzeugt."
echo "Zertifikat (nicht geheim): $cert"
echo "Diese Ausgabe darfst du in den Chat kopieren."
echo "Den geheimen Text zeigt erst der naechste Befehl aus der Anleitung."

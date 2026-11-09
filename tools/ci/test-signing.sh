#!/bin/sh
# Tests for the fixed CI signature. Uses throwaway keys in a sandbox only.
set -u

root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
install="$root/tools/ci/install-signing-key.sh"
generate="$root/tools/erzeuge-lara-signatur.sh"

sandbox=$(mktemp -d)
trap 'rm -rf "$sandbox"' EXIT
passed=0
failed=0

check() {
    description=$1
    shift
    if "$@" >/dev/null 2>&1; then
        passed=$((passed + 1))
        echo "ok   - $description"
    else
        failed=$((failed + 1))
        echo "FAIL - $description"
    fi
}

value_of() { sed -n "s/^$2=//p" "$1" | tail -n 1; }
same_file() { cmp -s "$1" "$2"; }
absent() { [ ! -e "$1" ]; }
mode_is() { [ "$(stat -c %a "$1")" = "$2" ]; }
log_has() { grep -q -- "$2" "$1"; }
log_lacks() { ! grep -q -- "$2" "$1"; }
equal() { [ "$1" = "$2" ]; }
one_line() { [ "$(wc -l <"$1")" -le 1 ]; }

# run_case <name>: runs the installer in a fresh home with $SECRET, if set.
run_case() {
    case_home="$sandbox/$1"
    mkdir -p "$case_home"
    case_output="$case_home/github_output"
    case_log="$case_home/log"
    case_store="$case_home/.android/debug.keystore"
    if [ "${SECRET+set}" = set ]; then
        HOME="$case_home" ANDROID_USER_HOME='' GITHUB_OUTPUT="$case_output" \
            LARA_CI_KEYSTORE_B64="$SECRET" sh "$install" >"$case_log" 2>&1
    else
        env -u LARA_CI_KEYSTORE_B64 HOME="$case_home" ANDROID_USER_HOME='' GITHUB_OUTPUT="$case_output" \
            sh "$install" >"$case_log" 2>&1
    fi
    case_rc=$?
}

expect_rejected() {
    check "$1: build is not blocked" equal "$case_rc" 0
    check "$1: falls back to debug signing" equal "$(value_of "$case_output" signing)" debug
    check "$1: no certificate expected" equal "$(value_of "$case_output" expected_cert)" ""
    check "$1: installs no key" absent "$case_store"
    check "$1: warns" log_has "$case_log" '::warning title=Signatur::'
}

fixture() { # <name> <keytool options...>: creates a JKS throwaway store, prints its Base64
    name=$1
    shift
    keytool -genkeypair -keyalg EC -groupname secp256r1 -dname "CN=Fixture" -validity 365 \
        -keystore "$sandbox/$name.jks" -storetype JKS "$@" >/dev/null 2>&1
    openssl base64 -A -in "$sandbox/$name.jks"
}

# --- Generator ---
gen_home="$sandbox/generator"
mkdir -p "$gen_home"
HOME="$gen_home" sh "$generate" >"$gen_home/stdout" 2>"$gen_home/stderr"
check "generator succeeds" equal "$?" 0
p12="$gen_home/lara-signatur/lara-ci-keystore.p12"
b64="$gen_home/lara-signatur/lara-ci-keystore.b64"
check "generator writes the keystore" test -s "$p12"
check "generator writes the Base64 text" test -s "$b64"
check "Base64 text is one line" one_line "$b64"
check "keystore is private" mode_is "$p12" 600
check "Base64 text is private" mode_is "$b64" 600
check "folder is private" mode_is "$gen_home/lara-signatur" 700
check "no temporary files stay behind" equal "$(find "$gen_home/lara-signatur" -mindepth 1 | wc -l)" 2
real_secret=$(cat "$b64")
secret_piece=$(printf '%s' "$real_secret" | cut -c1-40)
check "chat-safe output does not contain the secret" log_lacks "$gen_home/stdout" "$secret_piece"
generated_cert=$(sed -n 's/^Zertifikat (nicht geheim): //p' "$gen_home/stdout")
check "generator reports a SHA-256 digest" sh -c "printf '%s' '$generated_cert' | grep -Eq '^[0-9a-f]{64}$'"
cp "$p12" "$sandbox/original.p12"
HOME="$gen_home" sh "$generate" >"$gen_home/stdout2" 2>"$gen_home/stderr2"
second_rc=$?
check "generator refuses a second key" test "$second_rc" -ne 0
check "existing key stays unchanged" same_file "$p12" "$sandbox/original.p12"

# --- No secret ---
unset SECRET
run_case unset
check "unset secret: build is not blocked" equal "$case_rc" 0
check "unset secret: debug signing" equal "$(value_of "$case_output" signing)" debug
check "unset secret: installs no key" absent "$case_store"
check "unset secret: says so" log_has "$case_log" 'Kein Secret'

SECRET=''
run_case empty
check "empty secret: build is not blocked" equal "$case_rc" 0
check "empty secret: debug signing" equal "$(value_of "$case_output" signing)" debug
check "empty secret: installs no key" absent "$case_store"

# --- Valid key ---
SECRET=$real_secret
run_case valid
check "valid key: rc 0" equal "$case_rc" 0
check "valid key: fixed signing" equal "$(value_of "$case_output" signing)" fixed
check "valid key: expected certificate is the generated one" equal "$(value_of "$case_output" expected_cert)" "$generated_cert"
check "valid key: file is the keystore" same_file "$case_store" "$p12"
check "valid key: file is private" mode_is "$case_store" 600
check "valid key: secret is not logged" log_lacks "$case_log" "$secret_piece"
first_cert=$(value_of "$case_output" expected_cert)

run_case valid_again
check "same secret again: same certificate" equal "$(value_of "$case_output" expected_cert)" "$first_cert"

SECRET=$(printf '%s' "$real_secret" | fold -w 48; printf '\r\n ')
run_case wrapped
check "pasted with line breaks: fixed signing" equal "$(value_of "$case_output" signing)" fixed
check "pasted with line breaks: same certificate" equal "$(value_of "$case_output" expected_cert)" "$first_cert"

SECRET=$real_secret
custom_home="$sandbox/custom-user-home"
HOME="$sandbox/custom-home" ANDROID_USER_HOME="$custom_home" GITHUB_OUTPUT="$sandbox/custom-output" \
    LARA_CI_KEYSTORE_B64="$SECRET" sh "$install" >/dev/null 2>&1
check "ANDROID_USER_HOME is respected" test -s "$custom_home/debug.keystore"
check "ANDROID_USER_HOME: home stays empty" absent "$sandbox/custom-home/.android/debug.keystore"

# --- Bad secrets ---
SECRET='das ist kein schluessel'
run_case garbage
expect_rejected "garbage"
check "garbage: secret is not logged" log_lacks "$case_log" 'kein schluessel'

SECRET='nicht*base64*text'
run_case invalid_base64
expect_rejected "invalid Base64"
check "invalid Base64: names the problem" log_has "$case_log" 'kein gültiger Base64-Text'

SECRET=$(printf '%s' "$real_secret" | cut -c1-200)
run_case truncated
expect_rejected "truncated paste"
check "truncated paste: secret is not logged" log_lacks "$case_log" "$secret_piece"

SECRET=$(fixture wrong_alias -alias somebodyelse -storepass android -keypass android)
run_case wrong_alias
expect_rejected "wrong alias"

SECRET=$(fixture wrong_store -alias androiddebugkey -storepass notandroid -keypass notandroid)
run_case wrong_store
expect_rejected "wrong store password"

SECRET=$(fixture wrong_key -alias androiddebugkey -storepass android -keypass notandroid)
run_case wrong_key
expect_rejected "wrong key password"

keytool -exportcert -alias androiddebugkey -keystore "$sandbox/wrong_key.jks" -storepass android \
    -file "$sandbox/only.crt" >/dev/null 2>&1
keytool -importcert -noprompt -alias androiddebugkey -file "$sandbox/only.crt" \
    -keystore "$sandbox/only_cert.jks" -storetype JKS -storepass android >/dev/null 2>&1
SECRET=$(openssl base64 -A -in "$sandbox/only_cert.jks")
run_case only_certificate
expect_rejected "certificate without private key"

# A key made with keytool and the standard values is fine as well.
SECRET=$(fixture jks_ok -alias androiddebugkey -storepass android -keypass android)
run_case jks_ok
check "JKS with standard values: fixed signing" equal "$(value_of "$case_output" signing)" fixed

# A bad secret must not damage a keystore that is already there.
preset_home="$sandbox/preset"
mkdir -p "$preset_home/.android"
printf 'vorhanden' >"$preset_home/.android/debug.keystore"
cp "$preset_home/.android/debug.keystore" "$sandbox/preset.copy"
HOME="$preset_home" ANDROID_USER_HOME='' GITHUB_OUTPUT="$preset_home/out" \
    LARA_CI_KEYSTORE_B64='das ist kein schluessel' sh "$install" >/dev/null 2>&1
check "bad secret keeps an existing keystore" same_file "$preset_home/.android/debug.keystore" "$sandbox/preset.copy"

echo "$passed passed, $failed failed"
[ -n "${GITHUB_ACTIONS:-}" ] && echo "::notice title=Signing tests::$passed passed, $failed failed"
[ "$failed" -eq 0 ]

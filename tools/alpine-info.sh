#!/bin/sh
# LARA Alpine-Info, Version 1
#
# Nur lesen: installiert nichts, aendert nichts, schreibt keine Dateien.
# Gibt keine Token aus: keine Werte von Umgebungsvariablen (ausser OLLAMA_HOST),
# keine Programm-Argumente, keine Konfigurationsdateien und keine Antworten
# des MCP-Dienstes ausser Statuszeile und drei unkritischen Kopfzeilen.
#
# Aufruf im Alpine-Terminal, Ausgabe danach in den Chat kopieren.

have() { command -v "$1" >/dev/null 2>&1; }
section() { printf '\n== %s ==\n' "$1"; }
limited() { if have timeout; then timeout 20 "$@"; else "$@"; fi; }

echo "=== LARA-ALPINE-INFO v1 BEGINN ==="
date -u '+Zeit (UTC): %Y-%m-%d %H:%M:%S' 2>/dev/null

section "System"
echo "Alpine: $(cat /etc/alpine-release 2>/dev/null || echo unbekannt)"
echo "Architektur: $(uname -m)"
echo "Kernel: $(uname -r)"
echo "Benutzer-ID: $(id -u)"

section "Laufzeit (PRoot oder VM)"
echo "TracerPid: $(sed -n 's/^TracerPid:[[:space:]]*//p' /proc/self/status 2>/dev/null)"
echo "PRoot-Variablen (nur Namen): $(env | cut -d= -f1 | grep -E '^(PROOT_|LD_PRELOAD$)' | tr '\n' ' ')"
echo "Android-App-Ordner:"
sed -n 's#^.* \(/data/[^ ]*\)$#\1#p' /proc/self/maps 2>/dev/null \
  | sed -E 's#^(/data/(data|user/[0-9]+)/[^/]+).*#  \1#' | sort -u | head -n 5
echo "Android-App-Prozess:"
for p in /proc/[0-9]*; do
  name=$(tr '\0' '\n' < "$p/cmdline" 2>/dev/null | head -n 1)
  case "$name" in
    /*|*[!a-zA-Z0-9._:]*) ;;
    *.*.*) echo "  $name" ;;
  esac
done | sort -u | head -n 5

section "Prozesse (nur Namen)"
ps -o comm 2>/dev/null | sed 1d | sort | uniq -c | sort -rn | head -n 30

section "Offene Ports"
if have netstat; then
  netstat -ltnp 2>&1 | head -n 25
elif have ss; then
  ss -ltn 2>&1 | head -n 25
else
  echo "netstat und ss fehlen"
fi

section "Werkzeuge"
for t in python3 pip3 node git curl; do
  if have "$t"; then
    printf '%s: ' "$t"
    limited "$t" --version </dev/null 2>&1 | head -n 1
  else
    echo "$t: fehlt"
  fi
done
if have ollama; then
  printf 'ollama: '
  limited ollama --version </dev/null 2>&1 | tail -n 1
else
  echo "ollama: fehlt"
fi

section "Ollama"
case "${OLLAMA_HOST:-}" in
  "") echo "OLLAMA_HOST: nicht gesetzt" ;;
  *@*) echo "OLLAMA_HOST: gesetzt (nicht angezeigt)" ;;
  *) echo "OLLAMA_HOST: $OLLAMA_HOST" ;;
esac
echo "GET /api/version: HTTP $(curl -s -m 8 -o /dev/null -w '%{http_code}' http://127.0.0.1:11434/api/version </dev/null)"
curl -s -m 8 http://127.0.0.1:11434/api/version </dev/null | head -c 200
echo
echo "Modelle:"
curl -s -m 15 http://127.0.0.1:11434/api/tags </dev/null \
  | tr ',{' '\n\n' | sed -n 's/^"name":"\(.*\)"$/  \1/p' | head -n 30

section "MCP-Endpunkt (ohne Token, nur Kopfzeilen)"
answer=$(curl -s -m 8 -o /dev/null -D - http://127.0.0.1:8765/mcp </dev/null | tr -d '\r' \
  | grep -iE '^(HTTP/|www-authenticate:|server:|content-type:)' | cut -c1-120)
if [ -n "$answer" ]; then echo "$answer"; else echo "keine Antwort"; fi

section "Speicher"
free -m 2>/dev/null | head -n 3
df -h / 2>/dev/null | tail -n 1

section "Projektordner und Programme"
for d in /root/projects /root/LARA/projects; do
  if [ -d "$d" ]; then
    echo "$d: vorhanden, $(ls -1A "$d" 2>/dev/null | wc -l) Eintraege"
  else
    echo "$d: fehlt"
  fi
done
echo "/usr/local/bin: $(ls -1 /usr/local/bin 2>/dev/null | head -n 40 | tr '\n' ' ')"
echo "/opt: $(ls -1 /opt 2>/dev/null | head -n 20 | tr '\n' ' ')"

printf '\n=== LARA-ALPINE-INFO ENDE ===\n'
echo "Bitte alles von BEGINN bis ENDE kopieren und in den Chat einfuegen."

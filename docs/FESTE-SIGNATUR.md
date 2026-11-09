# Feste CI-Signatur und Installationsfehler (Paket A, F10, F17)

Stand: 09.11.2026. Zentrale Statusquelle bleibt `PROJECT-STATUS.md`.

## Kurzfassung

Bisher bekam jeder GitHub-Build ein anderes Zertifikat. Android lässt ein Update aber nur mit demselben Zertifikat zu. Deshalb musste die alte Testversion jedes Mal erst weg, und ihre Daten gingen verloren. Mit einem festen Schlüssel als GitHub-Secret bleibt das Zertifikat gleich. Die Vorbereitung ist fertig, aktiv wird sie erst, wenn Thorben das Secret einträgt.

## Was Thorben tun muss (einmalig, etwa 5 Minuten)

**Wichtig:** Der Text aus Schritt 2 ist geheim wie ein Passwort. Er gehört nur in das GitHub-Feld, nie in einen Chat, auch nicht zu Claude oder ChatGPT, und nicht auf einen Screenshot.

**Schritt 1: Schlüssel erzeugen.** Im Alpine-Terminal einfügen:

```sh
apk add --no-cache openssl && curl -fsSL https://raw.githubusercontent.com/thorbenbeilmann3-png/LARA-AI-HUB/f62e983f17c7d4e541a635e8d9f9150c6eee8f6f/tools/erzeuge-lara-signatur.sh | sh
```

Die Ausgabe enthält nur den öffentlichen Fingerabdruck und darf in den Chat. So kann Claude später prüfen, ob der richtige Schlüssel benutzt wird.

**Schritt 2: Geheimen Text anzeigen und komplett kopieren.**

```sh
cat ~/lara-signatur/lara-ci-keystore.b64; echo
```

Das sind etwa 1.600 Zeichen in einem Stück, ohne Leerzeichen.

**Schritt 3: Bei GitHub eintragen.** Diese Seite im Browser öffnen, angemeldet bei GitHub:

<https://github.com/thorbenbeilmann3-png/LARA-AI-HUB/settings/secrets/actions/new>

- Feld **Name:** `LARA_CI_KEYSTORE_B64`
- Feld **Secret:** den kopierten Text einfügen
- **Add secret** drücken

Falls der Link nicht öffnet: im Repository **Settings → Secrets and variables → Actions → New repository secret** ([GitHub-Anleitung](https://docs.github.com/actions/security-guides/using-secrets-in-github-actions)).

**Schritt 4:** Claude oder ChatGPT schreiben: „Secret ist eingetragen.“ Dann wird ein neuer Build gestartet und geprüft, ob er das Zertifikat aus Schritt 1 trägt.

**Schritt 5: Einmal noch deinstallieren.** Die installierte „LARA Test“ hat noch ein altes Zertifikat. Deshalb muss sie vor dem ersten Build mit festem Schlüssel ein letztes Mal entfernt werden. Das löscht ihre lokalen Daten (Schlüssel, Chat). Danach sollten Updates ohne Deinstallation laufen.

Der Ordner `~/lara-signatur/` kann bleiben oder nach dem ersten erfolgreichen Update gelöscht werden. GitHub zeigt ein Secret nicht mehr an. Ohne Sicherung wäre bei einem Verlust ein neuer Schlüssel nötig, und damit einmal mehr Deinstallieren.

**Falls der Text doch im Chat oder auf einem Screenshot gelandet ist:** Der Schlüssel gilt als verbrannt. Dann `~/lara-signatur/` löschen, ab Schritt 1 neu erzeugen und das Secret bei GitHub ersetzen.

## Verhalten des Builds

| Zustand | Was passiert | Signatur |
|---|---|---|
| Kein Secret | Build läuft wie bisher, Hinweis im Lauf | wechselnd |
| Gültiges Secret | Schlüssel wird genutzt, die APK wird gegen das Zertifikat geprüft | fest |
| Ungültiges Secret (abgeschnitten, falsche Datei) | Warnung im Lauf, Build läuft weiter | wechselnd |
| Secret gültig, APK trägt anderes Zertifikat | Build bricht ab | – |

Die Notiz am Release-Entwurf nennt das Zertifikat und ob die Signatur fest oder wechselnd ist.

## Technik in Kürze

- `tools/ci/install-signing-key.sh` schreibt das Secret als Datei in den temporären Ordner des Build-Laufs und meldet den Pfad in `LARA_CI_KEYSTORE_PATH`. Nur wenn dieser Wert gesetzt ist, nimmt `app/build.gradle.kts` die Datei; ohne Secret ändert sich nichts. Alias und Passwort sind die Standardwerte eines Android-Debug-Schlüssels (`androiddebugkey`, `android`), deshalb genügt ein einziges Secret. Geheim ist nur die Datei.
- Der erste Ansatz (Datei einfach als `~/.android/debug.keystore` ablegen) fiel im CI-Beweis durch: Die APK trug ein anderes Zertifikat. Deshalb jetzt der ausdrückliche Pfad.
- Der Schlüssel ist für Testbuilds gedacht. Für eine Veröffentlichung im Play Store gehört ein eigener Schlüssel her.
- Base64 ist keine Verschlüsselung ([GitHub](https://docs.github.com/actions/security-guides/using-secrets-in-github-actions)). Der Schutz kommt vom Secret-Speicher. Ein Secret ist auf 48 KB begrenzt ([GitHub](https://docs.github.com/en/actions/reference/security/secrets)), der Text hat rund 1,6 KB.
- Elliptische Kurve P-256 statt RSA, damit der Text kürzer ist (rund 1.600 statt 3.600 Zeichen). APK-Signaturschema v2 unterstützt ECDSA mit P-256 seit Android 7.0 ([AOSP](https://source.android.com/docs/security/features/apksigning/v2)); LARA braucht mindestens Android 8.
- Geändert sind `android-branch-check.yml` (Testbuilds) und `android-debug.yml`. Letzteres wirkt erst, wenn es im Branch `feature/android-secure-api-key-storage` ankommt. `android-apk.yml` bleibt gleich, weil daraus nur ein nicht installiertes Artefakt entsteht.

## Installationsfehler „App konnte nicht installiert werden“ (F17)

**Belegt:** Sechs geprüfte Testbuilds trugen unterschiedliche Zertifikate, unter anderem `b84e63b0…` (`4c4a194`) und `f7d0fff7…` (`64fe56d`). Android vergleicht beim Update die Zertifikate und lässt es nur bei Übereinstimmung zu ([Android](https://developer.android.com/studio/publish/app-signing)). Eine neue Testversion lässt sich darum nicht über eine ältere installieren.

**Nicht belegt:** Ob genau das die Ursache der Meldung vom 08.11., 22:20 Uhr, war. Welche Datei damals installiert werden sollte, ist nicht bekannt. Zwei Minuten später meldete Thorben einen Browser-Fehler beim Herunterladen einer anderen APK, es kann also auch um eine andere Datei gegangen sein. `LARA-Test-4c4a194.apk` ließ sich danach installieren. F17 bleibt deshalb „Ursache wahrscheinlich, nicht bewiesen“.

**Lösung:** feste Signatur wie oben. Bis sie aktiv ist: alte „LARA Test“ vor der Installation deinstallieren.

**Update ohne Deinstallation:** Es muss dasselbe Zertifikat sein und die Paketnamen müssen übereinstimmen. Android blockiert laut offizieller Seite nur eine niedrigere `versionCode` ([Android](https://developer.android.com/studio/publish/versioning)). LARA hat bei allen Builds `versionCode = 1`. Zu gleicher `versionCode` sagt die Seite nichts. Nach meinem Kenntnisstand ist sie erlaubt, belegt wird das erst durch das erste echte Update am Gerät.

**Bleibt bestehen:** Die normale LARA (`de.laraai.hub`) und die Testversion (`de.laraai.hub.alpinetest`) sind getrennte Apps. Auch die normale LARA braucht einmal eine Neuinstallation, sobald sie mit festem Schlüssel gebaut wird.

## Teststand

| Prüfung | Stand |
|---|---|
| Skript-Tests `tools/ci/test-signing.sh` | Lokal PASS: 70 Prüfungen unter `dash` und `bash --posix`, 13 absichtlich eingebaute Fehler erkannt. CI: Ergebnis steht aus |
| Zwei echte Builds, gleicher Schlüssel, gleiches Zertifikat | Ergebnis steht aus |
| Build ohne Secret läuft weiter | Ergebnis steht aus |
| Build mit echtem Secret | NICHT GETESTET (Secret noch nicht eingetragen) |
| Schritte 1 und 2 im Alpine-Terminal | NICHT GETESTET |
| Update ohne Deinstallation am Pixel | NICHT GETESTET |

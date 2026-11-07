# LARA – App-Sperre und geschützte API-Schlüssel

## Ablauf

1. Beim Start fragt LARA über die Android-Systemabfrage (`BiometricPrompt`) nach Fingerabdruck, Gesicht oder Geräte-PIN/-Muster/-Passwort. **LARA erhält und speichert keine biometrischen Daten**, nur „erfolgreich“ oder „abgebrochen“.
2. Nach dem Entsperren entschlüsselt LARA die gespeicherten API-Schlüssel **einmal** und hält sie nur im Arbeitsspeicher (`SessionVault`). Auf dem Speicher liegen sie weiterhin nur verschlüsselt.
3. Ist LARA länger als **5 Minuten** im Hintergrund, wird der Arbeitsspeicher geleert und beim Zurückkehren erneut gefragt (`LockPolicy`).
4. **Schlüssel speichern oder entfernen** verlangt immer eine erneute Anmeldung.

## Technik

- Neuer Keystore-Schlüssel `lara_api_key_aes_v2_auth` (AES-GCM) mit `setUserAuthenticationRequired(true)`. Er ist nach einer Anmeldung 300 s nutzbar. Ab Android 11 sind „starke“ Biometrie oder die Geräte-Anmeldung erlaubt.
- `setInvalidatedByBiometricEnrollment(true)`: Wird ein **neuer Fingerabdruck** hinzugefügt, macht Android den Schlüssel ungültig. LARA meldet das, entfernt die nicht mehr lesbaren Daten, und du gibst die API-Schlüssel neu ein. Das ist so gewollt.
- **Übernahme:** Schlüssel aus der früheren Speicherung (v1, ohne Anmeldepflicht) werden beim ersten Entsperren neu verschlüsselt. Den alten Schlüssel und die alten Daten löscht LARA **erst, wenn alle Einträge erfolgreich übertragen wurden**. Bei einem Fehler bleibt v1 unverändert.
- Gesichtserkennung: LARA lässt nur zu, was Android als „starke“ Biometrie einstuft. Ob das Gesichtsentsperren des Pixel 9a dazugehört, ist **nicht geprüft**. Andernfalls bietet die Abfrage Fingerabdruck oder PIN an.
- Android 9/10: Dort ist die Kombination „starke Biometrie oder PIN“ nicht verfügbar. LARA fragt dann nach „Biometrie oder PIN“, und für die geschützten Schlüssel gilt die PIN.

## Ohne Displaysperre

Ohne eingerichtete Displaysperre lassen sich keine geschützten Schlüssel anlegen oder verwenden. LARA bietet dann „Ohne API-Schlüssel fortfahren“ an. Das lokale Modell funktioniert weiter, Cloud-Anbieter nicht.

## Tests

- Unit-Tests: `SessionLockTest` (Leeren beim Sperren, Ersetzen der Sitzung, 5-Minuten-Grenze, Uhr läuft rückwärts → sperren).
- **Nicht automatisch testbar, muss auf dem Pixel geprüft werden:** Systemabfrage erscheint; Abbrechen lässt LARA gesperrt; Entsperren per Finger/PIN; vorhandener Schlüssel funktioniert nach der Übernahme; Speichern und Entfernen fragt erneut; nach mehr als 5 min im Hintergrund wird erneut gefragt.

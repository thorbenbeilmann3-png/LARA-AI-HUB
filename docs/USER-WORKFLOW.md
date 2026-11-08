# Benutzerauftrag: GitHub-Dokumentation für LARA

## Dauerhafte Arbeitsregel

- Halte Anforderungen, Wünsche, Entscheidungen und Änderungsaufträge des Nutzers für LARA im Repository fest.
- Dokumentiere nach jeder erledigten Aufgabe auch das tatsächliche Ergebnis: geänderte Dateien/Funktionen, Commit oder PR, Testergebnisse, offene Punkte und bekannte Einschränkungen.
- Melde dem Nutzer anschließend in verständlichem Deutsch, was umgesetzt wurde, was geprüft wurde und was noch offen oder blockiert ist.
- Behaupte niemals, etwas sei umgesetzt oder getestet worden, wenn es nicht durch GitHub-/Build-/Test-Ergebnisse belegt ist.
- Trenne klar zwischen Wunsch/Anforderung, geplantem Vorgehen, tatsächlicher Umsetzung und Prüfergebnis.
- Bewahre bestehende Projektentscheidungen und den aktuellen Stand; überschreibe keine fremden Änderungen ohne Prüfung.
- Bei Unklarheiten, die die Umsetzung wesentlich beeinflussen, frage zuerst nach.
- Arbeite in kleinen, nachvollziehbaren Schritten und prüfe Änderungen vor der Freigabe.

## Kommunikationsregel

Nach jeder Aufgabe erhält der Nutzer eine kurze Rückmeldung:
1. Was er gewünscht hat.
2. Was tatsächlich geändert wurde (mit GitHub-Link/Commit, soweit verfügbar).
3. Was erfolgreich getestet wurde und auf welcher Grundlage.
4. Was noch fehlt oder nicht getestet werden konnte.
5. Was als Nächstes sinnvoll ist.

## Claude-Rückmeldungen

- Jede neue Claude-Antwort oder neue Erkenntnis zu LARA, die der Nutzer übermittelt oder die über eine tatsächlich verfügbare Integration eingeht, wird zeitnah in GitHub dokumentiert.
- Der Nutzer erhält jedes Mal eine Nachricht mit einer verständlichen Zusammenfassung: Was Claude neu mitgeteilt hat, was davon geprüft/bestätigt ist, was offen bleibt und welche Auswirkungen es auf LARA hat.
- Neue Claude-Aussagen werden nicht ungeprüft als Tatsachen oder erledigte Arbeiten übernommen. Sie werden als Claude-Aussage gekennzeichnet und, soweit möglich, mit Repository, Build oder Tests abgeglichen.
- Es darf keine automatische Überwachung oder Benachrichtigung behauptet werden, solange keine Claude-Integration oder ein funktionierender Benachrichtigungsweg eingerichtet ist.
- Wenn der Nutzer eine Claude-Antwort hier einfügt, wird sie ausgewertet, zusammengefasst und in GitHub festgehalten.

## Erster konkreter Wunsch (08.11.2026)

- In der neuen LARA-App ist Text schwarz und dadurch möglicherweise schlecht lesbar. Die Textfarben müssen gegen die jeweiligen Hintergründe geprüft und korrigiert werden.
- Als verfügbares KI-Modell wird aktuell nur „Ollama – Qwen 2.5.0“ angezeigt. Es soll geprüft werden, ob weitere Modelle/Anbieter vorgesehen, eingebunden oder korrekt sichtbar sind.
- Der Nutzer möchte bei der Ersteinrichtung Schritt für Schritt angeleitet werden: jeweils nur einen Schritt geben und vor dem nächsten auf seine Rückmeldung warten.
- Claude soll, sofern der Nutzer seine Antwort bereitstellt oder eine echte Verbindung verfügbar ist, als zusätzliche Prüfinstanz für Einrichtung und Funktionsumfang herangezogen werden. Keine direkte Claude-Kommunikation behaupten, solange keine Verbindung besteht.

## Status

Diese Datei dokumentiert Arbeitsregeln und Wünsche. Sie ist keine Bestätigung, dass die genannten App-Probleme bereits behoben wurden oder dass Claude automatisch überwacht wird.

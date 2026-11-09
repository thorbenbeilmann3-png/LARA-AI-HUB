# Automate und LARA: Schnittstellen (F8, F9)

Stand: 09.11.2026. Am Gerät getestet: NEIN.

## Zwei Richtungen, zwei Aktionen

| Richtung | Aktion | Gehört | Stand |
|---|---|---|---|
| Automate startet LARA | `de.laraai.hub.action.START_FLOW` | LARA | hier verbindlich festgelegt |
| LARA startet einen Flow | `com.llamalab.automate.intent.action.START_FLOW` | Automate | **nicht offiziell dokumentiert** (siehe unten) |

Offiziell festlegen kann LARA nur die eigene Aktion. Die Automate-Aktion gehört LlamaLab.

## Automate startet LARA

| Feld | Wert |
|---|---|
| Art | Activity-Intent, kein Broadcast. LARA muss sichtbar werden, damit eine Bestätigung möglich ist |
| Package | `de.laraai.hub.alpinetest` (Testversion) oder `de.laraai.hub` (normale App) |
| Activity class | `de.laraai.hub.MainActivity` |
| Action | `de.laraai.hub.action.START_FLOW` |
| Extra `anbieter` (Text, optional) | `ollama`, `claude`, `chatgpt` oder `freellmapi`. Ohne Angabe bleibt der aktuelle Anbieter |
| Extra `aktion` (Text, optional) | `chat` (Standard) oder `verbindungstest` |
| Berechtigungen bei LARA | keine. Andere Extras werden ignoriert |

**Sicherheit:** Jede App auf dem Handy kann diesen Intent senden, LARA kann den Absender nicht prüfen. Deshalb kann er nur einen Anbieter wählen und einen Bildschirm öffnen. Adresse, Modell, Nachricht und Schlüssel lassen sich nicht mitgeben. Automate ruft nie selbst einen KI-Anbieter auf, Schlüssel bleiben im ApiKeyStore.

| Fall | Was LARA tut |
|---|---|
| `aktion` = `chat` | Wählt den Anbieter und öffnet den Chat. Es wird **keine** Nachricht gesendet |
| `aktion` = `verbindungstest` | Wählt den Anbieter und liest nur die Modellliste |
| `claude`, `chatgpt` oder ein kostenpflichtiger aktueller Anbieter | Fragt zuerst in LARA. Ohne „Erlauben“ bleibt alles, wie es war |
| `ollama`, `freellmapi` | Keine Rückfrage |
| Unbekannter Anbieter oder unbekannte Aktion | Meldung in der Statuszeile, nichts wird geändert |
| Zweiter gültiger Start innerhalb einer Minute | Abgewiesen, Meldung in der Statuszeile |
| App ist gesperrt | Erst nach dem Entsperren |

Adresse und Modell kommen aus den gemerkten Eingaben des Anbieters (`docs/FREELLMAPI.md`).

## Beispiel-Flow in Automate

1. In Automate einen neuen Flow anlegen. Der Block **Flow beginning** ist schon vorhanden.
2. Den Block **App start** hinzufügen und mit dem Ausgang von **Flow beginning** verbinden.
3. Im Block **App start** eintragen:
   - **Package:** `de.laraai.hub.alpinetest`
   - **Activity class:** `de.laraai.hub.MainActivity`
   - **Action:** `de.laraai.hub.action.START_FLOW`
   - **Extras:** `{ "anbieter": "ollama", "aktion": "verbindungstest" }`
   - Alle übrigen Felder bleiben leer.
4. Flow speichern und starten.
5. LARA öffnet die Tools-Seite, und die Statuszeile zeigt das Ergebnis des Verbindungstests.

Felder und Dictionary-Schreibweise stammen aus der offiziellen Doku ([App start](https://llamalab.com/automate/doc/block/activity_start.html), [Values](https://llamalab.com/automate/doc/value.html)). Startet der Flow aus dem Hintergrund, kann Android den Start verhindern, wenn Automate nicht über anderen Apps einblenden darf ([Android](https://developer.android.com/guide/components/activities/background-starts)).

## F8: Autostart und Rate-Begrenzung

Vorher feuerte der Autostart bei jedem Neuaufbau der Hauptansicht, etwa beim Entsperren und Drehen. Jetzt gilt für automatische Starts:

- LARA startet den eingetragenen Flow höchstens einmal pro App-Start.
- Höchstens ein Start pro Minute, auch über einen App-Neustart hinweg.
- Automate → LARA und LARA → Automate werden getrennt gezählt. Eine Schleife zwischen beiden läuft so nicht hoch.
- Die Knöpfe in LARA, die du selbst drückst, sind nicht begrenzt.

## F9: LARA startet einen Flow

Die offizielle Doku nennt beim Block **Flow beginning** nur die „Flow URI“ und eine „Payload“ vom startenden Programm, aber keine Intent-Aktion ([Flow beginning](https://llamalab.com/automate/doc/block/flow_beginning.html)). `START_FLOW` fand ich nur in Nutzerbeiträgen von 2017 und 2019 ([Forum](https://groups.google.com/g/automate-user/c/wlu_Gdykst8)) und in einem Gist von 2020 ([Gist](https://gist.github.com/Ben-Kaniobi/5accd187f7716d909a0718e87cbeda23)).

Der Aufruf bleibt unverändert: Klappt er nicht, öffnet LARA nur Automate. Er widerspricht aber der Regel „keine undokumentierten Intent-Aktionen“ aus `docs/ALPINE-OLLAMA-MCP-INTEGRATION.md`. Ob er bleiben darf, entscheidet Thorben.

## Teststand

| Prüfung | Stand |
|---|---|
| Unit-Tests `AutomateInterfaceTest` | siehe `PROJECT-STATUS.md` |
| Echter Flow auf dem Pixel, Automate → LARA | NICHT GETESTET |
| LARA → Automate mit `START_FLOW` | NICHT GETESTET |
| Bestätigungsdialog, Statusmeldungen | NICHT GETESTET |

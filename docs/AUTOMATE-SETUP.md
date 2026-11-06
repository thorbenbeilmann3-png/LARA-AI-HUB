# Automate-Anbindung für LARA AI HUB

## Zweck

Beim Start von LARA kann ein gespeicherter Automate-Flow aufgerufen werden. Der Flow soll die auf dem Handy installierte Ollama-/LLM-Server-App starten und anschließend prüfen, ob der lokale API-Endpunkt erreichbar ist.

## Flow in Automate anlegen

1. In Automate einen neuen Flow erstellen.
2. Als ersten Block **Flow beginning** verwenden.
3. Dahinter **Apps → App start** setzen und über **Pick activity** die tatsächlich installierte Ollama-/LLM-Server-App auswählen. Keine Paket-ID raten.
4. Danach einen kurzen **Delay**-Block (z. B. 3 Sekunden) einfügen.
5. Danach **Connectivity → HTTP request** mit GET auf den Server-Endpunkt setzen:
   - OpenAI-kompatibel: `http://10.179.130.230:12434/v1/models`
   - native Ollama-API: `http://10.179.130.230:12434/api/tags`
6. Den Flow so verbinden, dass bei einem nicht erfolgreichen HTTP-Status nach einer kurzen Verzögerung erneut geprüft wird. Keine Endlosschleife ohne Wartezeit verwenden.
7. Den Flow speichern und den **Flow beginning**-Block öffnen. Unter **Current values** die dort angezeigte Flow URI kopieren.
8. In LARA unter **PC → Automate von LlamaLab** die Flow URI einfügen.
9. **Automate-Flow beim Start von LARA automatisch ausführen** aktivieren und die Einstellungen speichern.

## Wichtige Grenzen

- Die Flow URI ist installationsgebunden. Sie darf nicht als feste URI in den Quellcode eingebaut werden.
- Der App-Start-Block kann nur eine App-Aktivität starten, die Android öffentlich starten lässt.
- LARA übergibt den Flow-Aufruf an Automate. Android bzw. Automate kann den direkten Start ablehnen; in diesem Fall öffnet LARA Automate als Rückfall und zeigt den Hinweis an.
- Der HTTP-Test beweist nur die Erreichbarkeit der API, nicht dass ein bestimmtes Modell korrekt antwortet. LARA prüft die Modellliste zusätzlich selbst.
- Die angegebene IP-Adresse ist die bisher vom Nutzer genannte Adresse. Falls sie sich im WLAN ändert, muss die Adresse in LARA und im Flow angepasst werden.
- Lokale HTTP-Verbindungen sind für den lokalen Server vorgesehen. Gespeicherte API-Schlüssel werden von LARA ausschließlich über HTTPS übertragen.

## Dokumentationsquellen

- Automate App start: https://llamalab.com/automate/doc/block/activity_start.html
- Automate Flow beginning und installationsgebundene Flow URI: https://llamalab.com/automate/doc/block/flow_beginning.html
- Automate HTTP request / verfügbare Blöcke: https://llamalab.com/automate/doc/block/index.html

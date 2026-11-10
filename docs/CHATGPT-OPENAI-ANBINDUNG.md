# ChatGPT / OpenAI in LARA über Python

Stand: 2026-11-06

## Architektur

LARA Android → Python Control Plane → OpenAI Responses API → Antwort zurück an LARA.

Automate soll den Python-Dienst starten bzw. dessen Start überwachen. Automate ist nicht selbst das KI-Modell und ersetzt keinen API-Zugang.

## Serverseitige OpenAI-Anbindung

Der Python-Control-Plane-Endpunkt `POST /v1/chat` akzeptiert:

```json
{
  "provider": "openai",
  "model": "HIER_EIN_VERFÜGBARES_MODELL",
  "prompt": "Deine Nachricht"
}
```

Der Server ruft `https://api.openai.com/v1/responses` mit Bearer-Authentifizierung auf. Der API-Schlüssel wird ausschließlich aus der Server-Umgebungsvariable `OPENAI_API_KEY` gelesen. Er gehört weder in GitHub noch in den Android-Quellcode oder in Automate-Flow-Text.

## Einrichtung des API-Schlüssels

1. Einen OpenAI Platform API-Key für das Projekt LARA AI HUB erstellen.
2. Den Schlüssel auf dem Gerät/Server einrichten, auf dem der Python-Control-Plane-Prozess läuft, als Umgebungsvariable `OPENAI_API_KEY`.
3. Den Python-Dienst neu starten.
4. `GET /health` aufrufen. `openai_configured` muss `true` melden.
5. Mit einem tatsächlich freigeschalteten Modellnamen einen Chat-Test ausführen.

Der Schlüssel darf nicht in Chatnachrichten, GitHub-Dateien, Screenshots oder Logs kopiert werden.

## Automate

Der Automate-Flow muss den Python-Dienst über den auf dem Gerät vorhandenen Python-Runner starten. Der konkrete App-Start-Block hängt davon ab, ob Python in Pydroid 3, Termux oder einer anderen Laufzeit ausgeführt wird. Keine Activity- oder Intent-Adresse wird geraten.

## Noch offene Integration

- Android-App muss den Python-Control-Plane-Endpunkt `/v1/chat` aufrufen können.
- Ein separater, sicher gespeicherter LARA-Automation-Token muss die Verbindung zwischen Android-App und Python-Dienst absichern.
- Automate-Flow muss mit der tatsächlich installierten Python-Laufzeit erstellt und auf dem Gerät getestet werden.
- OpenAI-API-Schlüssel, Projektlimit und verfügbare Modelle müssen im Platform-Projekt bestätigt werden.

## Quellen

- OpenAI API-Übersicht und Geheimnisverwaltung: https://developers.openai.com/api/reference/overview
- Responses API: https://developers.openai.com/api/reference/resources/responses/methods/create
- OpenAI Quickstart: https://developers.openai.com/api/docs

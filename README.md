# LARA AI HUB

Android-first AI hub starter project. The first milestone is a native Android chat interface for an OpenAI-compatible local endpoint.

## Current status

- Android app shell with Chat, Postfach, ARENA and PC tabs
- Configurable OpenAI-compatible chat-completions endpoint and model name
- GitHub Actions workflow that builds a debug APK and stores it as a workflow artifact

## Not implemented yet

Persistent inbox, multiple provider adapters (OpenAI/Anthropic), API-key vault, ARENA tournament integration, Python execution worker, MCP/Skills management, GitHub operations from inside the app, Windows remote agent and Android automation.

These are planned milestones, not currently working features.

## Local development

Open this repository in Android Studio. Use JDK 17 and Android SDK 35. The GitHub Actions workflow uses Gradle 8.9.

Default endpoint:
`http://127.0.0.1:12434/v1/chat/completions`

On Android, `127.0.0.1` refers to the phone itself. If the model server runs on a PC, enter the PC's reachable LAN address and port instead. Do not expose an unauthenticated model endpoint to the public internet.

## Security note

Cleartext HTTP is enabled only for local development compatibility. Before remote or production use, implement HTTPS, authentication, secure API-key storage, permission checks and explicit approval for device/PC actions.

## Build APK

Every push to `main` and every pull request targeting `main` starts the Android build workflow. Run it manually under **Actions → Build Android APK → Run workflow**. Download the generated `LARA-AI-HUB-debug-apk` artifact from the workflow run.

A successful workflow build is still required before the APK can be considered verified.

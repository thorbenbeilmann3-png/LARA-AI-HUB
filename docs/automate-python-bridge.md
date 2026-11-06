# LARA Python Bridge – Android / Automate proof of concept

Status: **0.1.0 – authenticated loopback connectivity test only**.

This is the first safe connection milestone between Automate and Python on the same Android phone. It deliberately does not execute submitted Python, shell commands, or arbitrary file operations.

## Requirements

- Android phone with Python 3 available (the current setup discussed Pydroid 3).
- Automate by LlamaLab.
- Keep both apps installed on the same phone.

## Start the Python bridge

1. Copy `lara_bridge.py` to a folder accessible inside Pydroid 3.
2. Open it in Pydroid 3 and run it.
3. On first start, the terminal prints `AUTOMATE_BEARER_TOKEN=...`. Copy the token into a private Automate variable. Do not share screenshots or flow exports containing it.
4. Leave the Python script running while testing Automate.
5. The bridge listens only on `127.0.0.1:8765`. It is not reachable from the Wi-Fi network or the public internet.

The token is stored under `Path.home()/.lara_bridge/token` in the Python app's private home directory. It is created once and reused on later starts. If the token is lost, stop the bridge, delete that token file from the Python app's private storage, restart the script, and save the newly printed token in Automate.

## Automate flow: health check

Create a new flow with these blocks:

1. **Flow beginning**.
2. **HTTP request**:
   - URL: `http://127.0.0.1:8765/v1/health`
   - Method: `GET`
   - Request headers: dictionary containing `Authorization` = `Bearer YOUR_TOKEN`
   - Timeout: 10 seconds
   - Save response: variable (for example `healthBody`)
   - Response status code: variable (for example `healthStatus`)
3. **Expression true?** with the condition `healthStatus = 200`.
4. On YES, show `healthBody` with a **Dialog message** block. On NO, show the status/body as an error.

## Automate flow: echo round trip

After the health check succeeds, add another **HTTP request** block:

- URL: `http://127.0.0.1:8765/v1/tasks/echo`
- Method: `POST`
- Request content type: `application/json`
- Request content body: `{"task":"echo","text":"Hallo von Automate"}`
- Request headers: `Authorization` = `Bearer YOUR_TOKEN`
- Timeout: 10 seconds
- Save response and response status code to variables.

A successful result is HTTP 200 and JSON containing `"result":"Hallo von Automate"`. Always check the HTTP status code. Add a **Failure catch** path for connection errors.

## Safety boundary

- The server binds to IPv4 loopback only (`127.0.0.1`).
- Every endpoint requires a random bearer token.
- Request bodies are limited to 4 KiB; echo text is limited to 1,000 characters.
- Only `GET /v1/health` and `POST /v1/tasks/echo` exist.
- Unknown tasks and paths are rejected.
- No arbitrary Python, shell, ADB, device-control, or file-system endpoint exists in this milestone.
- Do not expose this service through a LAN address, port forwarding, or a tunnel.

## Tests

Run on a desktop or in the Python app:

```bash
python -m unittest -v
python -m py_compile lara_bridge.py
```

The automated tests cover token persistence, authenticated health, successful echo, rejection of unauthenticated requests, and rejection of unsupported paths/tasks.

## Not yet verified

The automated test suite runs against a local Python HTTP server in the development environment. It does not prove that Pydroid 3 remains alive in the Android background or that the Automate flow has been created and run on the Pixel. Those two checks require a real on-device test.

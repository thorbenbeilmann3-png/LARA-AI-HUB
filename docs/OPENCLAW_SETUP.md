# OpenClaw Gateway in LARA

LARA connects to OpenClaw through its official OpenAI-compatible Gateway HTTP API. This is a separate connection from provider API keys such as OpenAI, Anthropic or Gemini.

## 1. Enable the HTTP endpoint

The Chat Completions endpoint is disabled by default. Merge this setting into the existing OpenClaw configuration; do not replace the rest of your configuration:

```json5
{
  gateway: {
    http: {
      endpoints: {
        chatCompletions: { enabled: true },
      },
    },
  },
}
```

Apply the configuration using your normal OpenClaw configuration/restart procedure.

## 2. Add the connection in LARA

1. Open **Verbindungen & Geräte**.
2. Select **OpenClaw · Gateway**.
3. Enter the Gateway base URL, including `/v1`:
   - Private LAN example: `http://192.168.1.20:18789/v1`
   - Private HTTPS/Tailscale ingress: `https://your-private-gateway.example/v1`
4. Enter the Gateway token and save it in LARA.
5. Select **Verbinden / Modelle laden**.
6. Select one of the returned agent targets.

The token is stored separately from model-provider keys using the encrypted Android credential store. Never paste it into a chat or commit it to GitHub.

## 3. Agent selection and sessions

The `GET /v1/models` response lists top-level agent targets such as:

- `openclaw/default`
- `openclaw/<agentId>`

These are agent targets, not raw backend model names. Sub-agents are not listed as selectable top-level agents. LARA keeps a stable conversation identifier so successive messages continue the same OpenClaw session.

## 4. Security

Treat the Gateway token as a highly privileged owner/operator credential. With token authentication, OpenClaw grants the full default operator scope set, including administrative, pairing, approval, read and write permissions.

- Keep the HTTP endpoint on loopback, a private LAN, a tailnet or another private ingress.
- Never expose this endpoint directly to the public internet.
- Use HTTPS for remote connections. LARA permits plain HTTP only for localhost/private-network gateway addresses.
- Do not reuse the Gateway token as an OpenAI, Anthropic, Gemini or other provider API key.

OpenClaw's documentation recommends the Gateway WebSocket protocol with paired-device bootstrap/device tokens for native mobile clients connecting remotely. LARA's current integration uses the official HTTP compatibility endpoint; a paired-device WebSocket transport is a separate future hardening step.

## 5. Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| HTTP 404 while loading agents | The Chat Completions endpoint is still disabled, or the URL does not point to the Gateway `/v1` base. |
| HTTP 401 | Gateway token/password is missing or incorrect. |
| HTTP 403 while loading agents | The token or identity lacks `operator.read`. |
| Agent list loads but chat is rejected | The chat request needs the relevant write permission; check Gateway policy and logs. |
| No agent appears | Check that the Gateway exposes top-level agents and that the token can read `/v1/models`. |

The connection test loads the agent list (read access). Sending a message is the separate check for chat/write access.

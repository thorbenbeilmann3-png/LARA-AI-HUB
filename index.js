const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");

// ---------------------------------------------------------------------------
// Sicherheitskonfiguration (nur über Umgebungsvariablen, keine Geheimnisse)
//
// LARA_BIND_HOST          Adresse, auf der der Server lauscht. Standard: 127.0.0.1
//                         (nur dieses Gerät). Für ein VPN gezielt dessen Adresse setzen,
//                         niemals 0.0.0.0 ins Internet freigeben.
// PORT                    Port. Standard: 3000
// LARA_ALLOWED_UPSTREAMS  Kommagetrennte Positivliste erlaubter Modellserver (Origins),
//                         z. B. "http://127.0.0.1:12434". Nur diese Ziele werden
//                         weitergeleitet. Standard: http://127.0.0.1:12434
// LARA_ALLOWED_HOSTS      Zusätzlich erlaubte Host-Header (Schutz gegen DNS-Rebinding).
//                         Standard: 127.0.0.1:<PORT>, localhost:<PORT>
// LARA_ALLOWED_ORIGINS    Kommagetrennte Browser-Origins, die per CORS zugreifen dürfen.
//                         Standard: keine (die eigene Oberfläche braucht kein CORS).
// ---------------------------------------------------------------------------

const MAX_BODY_BYTES = 1024 * 1024;
const UPSTREAM_TIMEOUT_MS = 120000;

function splitList(value) {
  return String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function normalizeOrigin(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    return url.origin;
  } catch (e) {
    return null;
  }
}

function loadConfig(env = process.env) {
  const port = Number(env.PORT || 3000);
  const bindHost = env.LARA_BIND_HOST || "127.0.0.1";
  const upstreams = new Set(
    splitList(env.LARA_ALLOWED_UPSTREAMS || "http://127.0.0.1:12434")
      .map(normalizeOrigin)
      .filter(Boolean)
  );
  const hosts = new Set([`127.0.0.1:${port}`, `localhost:${port}`, ...splitList(env.LARA_ALLOWED_HOSTS)].map((h) => h.toLowerCase()));
  const origins = new Set(splitList(env.LARA_ALLOWED_ORIGINS).map(normalizeOrigin).filter(Boolean));
  return { port, bindHost, upstreams, hosts, origins };
}

// SSRF-Schutz: Die vom Client gesendete Adresse wird nur als Auswahl aus der
// Positivliste verwendet. Es wird nie die Zeichenkette des Clients weitergereicht,
// sondern der erlaubte Origin aus der Konfiguration.
function resolveUpstream(config, urlString) {
  if (typeof urlString !== "string" || urlString.length > 300) return null;
  let url;
  try {
    url = new URL(urlString);
  } catch (e) {
    return null;
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") return null;
  if (url.username || url.password || url.search || url.hash) return null;
  if (url.pathname !== "/" && url.pathname !== "") return null;
  return config.upstreams.has(url.origin) ? url.origin : null;
}

function getHttpModule(url) {
  return url.protocol === "https:" ? https : http;
}

function corsHeaders(config, request) {
  const origin = request.headers.origin;
  if (origin && config.origins.has(normalizeOrigin(origin))) {
    return { "Access-Control-Allow-Origin": origin, Vary: "Origin" };
  }
  return {};
}

// Hilfssender: schreibt Antwort nur einmal, schützt gegen doppelte writeHead-Aufrufe
function sendResponse(response, statusCode, headers, body) {
  if (response.writableEnded) return;
  response.writeHead(statusCode, { "Cache-Control": "no-store", ...headers });
  response.end(body);
}

function sendJson(response, statusCode, cors, payload) {
  sendResponse(response, statusCode, { "Content-Type": "application/json", ...cors }, JSON.stringify(payload));
}

function proxyRequest(targetUrl, method, body, response, cors, onResult) {
  return new Promise((resolve) => {
    const url = new URL(targetUrl);
    const httpModule = getHttpModule(url);
    const options = {
      hostname: url.hostname,
      port: url.port || (url.protocol === "https:" ? 443 : 80),
      path: url.pathname + url.search,
      method: method,
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Content-Length": Buffer.byteLength(body || "")
      }
    };

    // Node folgt Weiterleitungen (3xx) nicht automatisch; der Status wird nur durchgereicht.
    const req = httpModule.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        if (onResult) {
          onResult(res.statusCode, res.headers, data);
        } else {
          sendResponse(response, res.statusCode, {
            "Content-Type": res.headers["content-type"] || "application/json",
            ...cors
          }, data);
        }
        resolve();
      });
    });

    req.on("error", () => {
      sendJson(response, 502, cors, { error: "Modellserver nicht erreichbar" });
      resolve();
    });

    // Timeout 120 Sekunden (Modell-Laden kann lange dauern)
    req.setTimeout(UPSTREAM_TIMEOUT_MS, () => {
      req.destroy();
      sendJson(response, 504, cors, { error: "Anfrage an Ollama-Server timed out (120s)" });
      resolve();
    });

    if (body) {
      req.write(body);
    }
    req.end();
  });
}

function readJsonBody(request, response, cors, onData) {
  let body = "";
  let size = 0;
  let aborted = false;
  request.on("data", (chunk) => {
    if (aborted) return;
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      aborted = true;
      sendJson(response, 413, cors, { error: "Anfrage zu groß" });
      request.resume();
      return;
    }
    body += chunk;
  });
  request.on("end", () => {
    if (aborted) return;
    let data;
    try {
      data = JSON.parse(body || "{}");
    } catch (e) {
      sendJson(response, 400, cors, { error: "Ungültiges JSON" });
      return;
    }
    if (!data || typeof data !== "object" || Array.isArray(data)) {
      sendJson(response, 400, cors, { error: "JSON-Objekt erwartet" });
      return;
    }
    onData(data);
  });
}

function createServer(config = loadConfig()) {
  return http.createServer((request, response) => {
    const cors = corsHeaders(config, request);

    // Schutz gegen DNS-Rebinding: nur bekannte Host-Header akzeptieren.
    const hostHeader = String(request.headers.host || "").toLowerCase();
    if (!config.hosts.has(hostHeader)) {
      sendJson(response, 403, {}, { error: "Host nicht zulässig" });
      return;
    }

    if (request.method === "OPTIONS") {
      if (!cors["Access-Control-Allow-Origin"]) {
        sendResponse(response, 403, {}, "");
        return;
      }
      sendResponse(response, 204, {
        ...cors,
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type"
      }, "");
      return;
    }

    let requestUrl;
    try {
      requestUrl = new URL(request.url, "http://localhost");
    } catch (e) {
      sendJson(response, 400, cors, { error: "Ungültige Adresse" });
      return;
    }

    if (request.method === "GET" && (requestUrl.pathname === "/" || requestUrl.pathname === "/index.html")) {
      const filePath = path.join(__dirname, "index.html");
      fs.readFile(filePath, (err, data) => {
        if (err) {
          sendResponse(response, 500, { "Content-Type": "text/plain; charset=utf-8" }, "Fehler beim Laden der Oberfläche");
          return;
        }
        sendResponse(response, 200, { "Content-Type": "text/html; charset=utf-8" }, data);
      });
      return;
    }

    // Proxy: GET /v1/models?ollamaUrl=...
    if (requestUrl.pathname === "/v1/models" && request.method === "GET") {
      const ollamaUrl = requestUrl.searchParams.get("ollamaUrl");
      if (!ollamaUrl) {
        sendJson(response, 400, cors, { error: "ollamaUrl query parameter required" });
        return;
      }
      const origin = resolveUpstream(config, ollamaUrl);
      if (!origin) {
        sendJson(response, 403, cors, { error: "Ollama-Adresse nicht zulässig (nicht in LARA_ALLOWED_UPSTREAMS)" });
        return;
      }
      void proxyRequest(origin + "/v1/models", "GET", null, response, cors, (status, headers, data) => {
        if (status === 404 || status === 405) {
          void proxyRequest(origin + "/api/tags", "GET", null, response, cors);
          return;
        }
        sendResponse(response, status, {
          "Content-Type": headers["content-type"] || "application/json",
          ...cors
        }, data);
      });
      return;
    }

    // Proxy: POST /api/chat (Ollama-natives Format) und POST /v1/chat/completions (OpenAI-kompatibel)
    const chatPaths = { "/api/chat": "/api/chat", "/v1/chat/completions": "/v1/chat/completions" };
    if (request.method === "POST" && Object.prototype.hasOwnProperty.call(chatPaths, requestUrl.pathname)) {
      readJsonBody(request, response, cors, (data) => {
        const ollamaUrl = data.ollamaUrl;
        if (!ollamaUrl) {
          sendJson(response, 400, cors, { error: "ollamaUrl is required" });
          return;
        }
        const origin = resolveUpstream(config, ollamaUrl);
        if (!origin) {
          sendJson(response, 403, cors, { error: "Ollama-Adresse nicht zulässig (nicht in LARA_ALLOWED_UPSTREAMS)" });
          return;
        }
        delete data.ollamaUrl;
        data.stream = false;
        void proxyRequest(origin + chatPaths[requestUrl.pathname], "POST", JSON.stringify(data), response, cors);
      });
      return;
    }

    sendResponse(response, 404, { "Content-Type": "text/plain; charset=utf-8", ...cors }, "Nicht gefunden");
  });
}

module.exports = { loadConfig, resolveUpstream, createServer };

if (require.main === module) {
  const config = loadConfig();
  createServer(config).listen(config.port, config.bindHost, () => {
    console.log(`Server running on http://${config.bindHost}:${config.port}`);
    console.log("Erlaubte Modellserver: " + ([...config.upstreams].join(", ") || "keine"));
  });
}

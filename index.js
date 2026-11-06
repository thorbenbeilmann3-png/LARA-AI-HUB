const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");

const port = 3000;

// SSRF-Schutz: nur http:// und https:// zulassen, interne Dienste blockieren
function isAllowedUrl(urlString) {
  try {
    const url = new URL(urlString);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return false;
    }
    const hostname = url.hostname.toLowerCase();

    // Loopback blockieren
    if (hostname === "localhost" || hostname === "localhost.localdomain") {
      return false;
    }

    // IPv4-Prüfung
    const ipv4Regex = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/;
    const match = hostname.match(ipv4Regex);
    if (match) {
      const octets = match.slice(1).map(Number);
      if (octets[0] === 127) return false;            // 127.0.0.0/8 Loopback
      if (octets[0] === 169 && octets[1] === 254) return false; // 169.254.0.0/16 Link-Local
      if (octets[0] === 0) return false;               // 0.0.0.0/8
      // 10.x.x.x, 172.16-31.x.x und 192.168.x.x bleiben erlaubt
    }

    return true;
  } catch (e) {
    return false;
  }
}

function getHttpModule(url) {
  return url.protocol === "https:" ? https : http;
}

// Hilfssender: schreibt Antwort nur einmal, schützt gegen doppelte writeHead-Aufrufe
function sendResponse(response, statusCode, headers, body) {
  if (response.writableEnded) return;
  response.writeHead(statusCode, headers);
  response.end(body);
}

function proxyRequest(targetUrl, method, body, response, onResult) {
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

    const req = httpModule.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        if (onResult) {
          onResult(res.statusCode, res.headers, data);
        } else {
          sendResponse(response, res.statusCode, {
            "Content-Type": res.headers["content-type"] || "application/json",
            "Access-Control-Allow-Origin": "*"
          }, data);
        }
        resolve();
      });
    });

    req.on("error", (e) => {
      sendResponse(response, 502, {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*"
      }, JSON.stringify({ error: e.message }));
      resolve();
    });

    // Timeout auf 120 Sekunden erhöht (Modell-Laden kann lange dauern)
    req.setTimeout(120000, () => {
      req.destroy();
      sendResponse(response, 504, {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*"
      }, JSON.stringify({ error: "Anfrage an Ollama-Server timed out (120s)" }));
      resolve();
    });

    if (body) {
      req.write(body);
    }
    req.end();
  });
}

const server = http.createServer((request, response) => {
  if (request.method === "OPTIONS") {
    sendResponse(response, 204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    }, "");
    return;
  }

  const requestUrl = new URL(request.url, `http://${request.headers.host}`);

  if (request.url === "/" || request.url === "/index.html") {
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
      sendResponse(response, 400, { "Content-Type": "application/json" }, JSON.stringify({ error: "ollamaUrl query parameter required" }));
      return;
    }
    if (!isAllowedUrl(ollamaUrl)) {
      sendResponse(response, 403, { "Content-Type": "application/json" }, JSON.stringify({ error: "Ollama-Adresse nicht zulässig (SSRF-Schutz)" }));
      return;
    }
    const base = ollamaUrl.endsWith("/") ? ollamaUrl : ollamaUrl + "/";
    void proxyRequest(base + "v1/models", "GET", null, response, (status, headers, data) => {
      if (status === 404 || status === 405) {
        void proxyRequest(base + "api/tags", "GET", null, response);
        return;
      }
      sendResponse(response, status, {
        "Content-Type": headers["content-type"] || "application/json",
        "Access-Control-Allow-Origin": "*"
      }, data);
    });
    return;
  }

  // Proxy: POST /api/chat (Ollama-natives Format)
  if (requestUrl.pathname === "/api/chat" && request.method === "POST") {
    let body = "";
    request.on("data", (chunk) => (body += chunk));
    request.on("end", () => {
      try {
        const data = JSON.parse(body || "{}");
        const ollamaUrl = data.ollamaUrl;
        if (!ollamaUrl) {
          sendResponse(response, 400, { "Content-Type": "application/json" }, JSON.stringify({ error: "ollamaUrl is required" }));
          return;
        }
        if (!isAllowedUrl(ollamaUrl)) {
          sendResponse(response, 403, { "Content-Type": "application/json" }, JSON.stringify({ error: "Ollama-Adresse nicht zulässig (SSRF-Schutz)" }));
          return;
        }
        delete data.ollamaUrl;
        data.stream = false;
        const base = ollamaUrl.endsWith("/") ? ollamaUrl : ollamaUrl + "/";
        void proxyRequest(base + "api/chat", "POST", JSON.stringify(data), response);
        return;
      } catch (e) {
        sendResponse(response, 400, { "Content-Type": "application/json" }, JSON.stringify({ error: e.message }));
        return;
      }
    });
    return;
  }

  // Proxy: POST /v1/chat/completions (OpenAI-kompatibel, Fallback)
  if (requestUrl.pathname === "/v1/chat/completions" && request.method === "POST") {
    let body = "";
    request.on("data", (chunk) => (body += chunk));
    request.on("end", () => {
      try {
        const data = JSON.parse(body || "{}");
        const ollamaUrl = data.ollamaUrl;
        if (!ollamaUrl) {
          sendResponse(response, 400, { "Content-Type": "application/json" }, JSON.stringify({ error: "ollamaUrl is required" }));
          return;
        }
        if (!isAllowedUrl(ollamaUrl)) {
          sendResponse(response, 403, { "Content-Type": "application/json" }, JSON.stringify({ error: "Ollama-Adresse nicht zulässig (SSRF-Schutz)" }));
          return;
        }
        delete data.ollamaUrl;
        data.stream = false;
        const base = ollamaUrl.endsWith("/") ? ollamaUrl : ollamaUrl + "/";
        void proxyRequest(base + "v1/chat/completions", "POST", JSON.stringify(data), response);
        return;
      } catch (e) {
        sendResponse(response, 400, { "Content-Type": "application/json" }, JSON.stringify({ error: e.message }));
        return;
      }
    });
    return;
  }

  sendResponse(response, 404, { "Content-Type": "text/plain; charset=utf-8" }, "Nicht gefunden");
});

server.listen(port, "0.0.0.0", () => {
  console.log("Server running on port " + port);
});

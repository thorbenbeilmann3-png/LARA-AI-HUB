// Security tests for the Node proxy (node --test). No external dependencies.
const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const { loadConfig, resolveUpstream, createServer } = require("../index.js");

function listen(server) {
  return new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve(server.address().port)));
}

function request(port, { method = "GET", path = "/", host, headers = {}, body } = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { host: "127.0.0.1", port, method, path, headers: { Host: host ?? `127.0.0.1:${port}`, ...headers } },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => resolve({ status: res.statusCode, headers: res.headers, body: data }));
      }
    );
    req.on("error", reject);
    if (body !== undefined) req.write(typeof body === "string" ? body : JSON.stringify(body));
    req.end();
  });
}

async function setup(extraEnv = {}) {
  const hits = [];
  const upstream = http.createServer((req, res) => {
    let b = "";
    req.on("data", (c) => (b += c));
    req.on("end", () => {
      hits.push({ method: req.method, url: req.url, body: b });
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ object: "list", data: [{ id: "qwen2.5-0.5b-instruct-q4_k_m" }], path: req.url }));
    });
  });
  const upstreamPort = await listen(upstream);
  const upstreamOrigin = `http://127.0.0.1:${upstreamPort}`;
  // Port is unknown until listen; Host check uses the real port via LARA_ALLOWED_HOSTS.
  const proxy = createServer(
    loadConfig({ LARA_ALLOWED_UPSTREAMS: upstreamOrigin, PORT: "0", ...extraEnv })
  );
  const port = await listen(proxy);
  proxy.close();
  const config = loadConfig({
    LARA_ALLOWED_UPSTREAMS: upstreamOrigin,
    PORT: String(port),
    ...extraEnv,
  });
  const server = createServer(config);
  await new Promise((r) => server.listen(port, "127.0.0.1", r));
  return {
    port,
    upstreamOrigin,
    hits,
    close: () => Promise.all([new Promise((r) => server.close(r)), new Promise((r) => upstream.close(r))]),
  };
}

test("default bind host is loopback only", () => {
  assert.equal(loadConfig({}).bindHost, "127.0.0.1");
});

test("default upstream allowlist is the confirmed local model server", () => {
  assert.deepEqual([...loadConfig({}).upstreams], ["http://127.0.0.1:12434"]);
});

test("resolveUpstream accepts only exact allowlisted origins", () => {
  const config = loadConfig({ LARA_ALLOWED_UPSTREAMS: "http://127.0.0.1:12434" });
  assert.equal(resolveUpstream(config, "http://127.0.0.1:12434"), "http://127.0.0.1:12434");
  assert.equal(resolveUpstream(config, "http://127.0.0.1:12434/"), "http://127.0.0.1:12434");
  for (const bad of [
    "http://127.0.0.1:12435",
    "http://169.254.169.254",
    "http://10.179.130.230:12434",
    "http://localhost:12434",
    "https://127.0.0.1:12434",
    "file:///etc/passwd",
    "http://user:pw@127.0.0.1:12434",
    "http://127.0.0.1:12434/admin",
    "http://127.0.0.1:12434/?x=1",
    "http://127.0.0.1:12434#x",
    "not a url",
    123,
    null,
  ]) {
    assert.equal(resolveUpstream(config, bad), null, `should reject ${bad}`);
  }
});

test("models request to allowlisted upstream is proxied", async () => {
  const s = await setup();
  try {
    const r = await request(s.port, { path: "/v1/models?ollamaUrl=" + encodeURIComponent(s.upstreamOrigin) });
    assert.equal(r.status, 200);
    assert.equal(JSON.parse(r.body).data[0].id, "qwen2.5-0.5b-instruct-q4_k_m");
    assert.equal(s.hits[0].url, "/v1/models");
  } finally {
    await s.close();
  }
});

test("requests to non-allowlisted targets are refused and never sent", async () => {
  const s = await setup();
  try {
    for (const target of ["http://169.254.169.254", "http://127.0.0.1:1", "http://example.com"]) {
      const g = await request(s.port, { path: "/v1/models?ollamaUrl=" + encodeURIComponent(target) });
      assert.equal(g.status, 403, target);
      const p = await request(s.port, { method: "POST", path: "/v1/chat/completions", body: { ollamaUrl: target, messages: [] } });
      assert.equal(p.status, 403, target);
    }
    assert.equal(s.hits.length, 0);
  } finally {
    await s.close();
  }
});

test("chat completions forwards to the fixed path and strips ollamaUrl", async () => {
  const s = await setup();
  try {
    const r = await request(s.port, {
      method: "POST",
      path: "/v1/chat/completions",
      body: { ollamaUrl: s.upstreamOrigin, model: "m", messages: [{ role: "user", content: "Hallo" }] },
    });
    assert.equal(r.status, 200);
    assert.equal(s.hits[0].url, "/v1/chat/completions");
    const sent = JSON.parse(s.hits[0].body);
    assert.equal(sent.ollamaUrl, undefined);
    assert.equal(sent.stream, false);
  } finally {
    await s.close();
  }
});

test("unknown Host header is rejected (DNS rebinding)", async () => {
  const s = await setup();
  try {
    const r = await request(s.port, { path: "/", host: "evil.example:" + s.port });
    assert.equal(r.status, 403);
  } finally {
    await s.close();
  }
});

test("no CORS access for foreign origins by default", async () => {
  const s = await setup();
  try {
    const r = await request(s.port, {
      path: "/v1/models?ollamaUrl=" + encodeURIComponent(s.upstreamOrigin),
      headers: { Origin: "https://evil.example" },
    });
    assert.equal(r.headers["access-control-allow-origin"], undefined);
    const pre = await request(s.port, { method: "OPTIONS", path: "/v1/chat/completions", headers: { Origin: "https://evil.example" } });
    assert.equal(pre.status, 403);
  } finally {
    await s.close();
  }
});

test("explicitly allowed CORS origin is echoed", async () => {
  const s = await setup({ LARA_ALLOWED_ORIGINS: "https://app.example" });
  try {
    const r = await request(s.port, { method: "OPTIONS", path: "/v1/chat/completions", headers: { Origin: "https://app.example" } });
    assert.equal(r.status, 204);
    assert.equal(r.headers["access-control-allow-origin"], "https://app.example");
  } finally {
    await s.close();
  }
});

test("oversized and invalid bodies are rejected", async () => {
  const s = await setup();
  try {
    const big = await request(s.port, { method: "POST", path: "/api/chat", body: "x".repeat(1024 * 1024 + 10) });
    assert.equal(big.status, 413);
    const bad = await request(s.port, { method: "POST", path: "/api/chat", body: "{nope" });
    assert.equal(bad.status, 400);
    assert.equal(s.hits.length, 0);
  } finally {
    await s.close();
  }
});

test("index.html is served", async () => {
  const s = await setup();
  try {
    const r = await request(s.port, { path: "/" });
    assert.equal(r.status, 200);
    assert.match(r.headers["content-type"], /text\/html/);
  } finally {
    await s.close();
  }
});

const http = require("node:http");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const MAX_REQUESTS = 200;
const TIMESTAMP_WINDOW_SECONDS = 5 * 60;
const requests = [];

function headerValue(headers, name) {
  const value = headers[name.toLowerCase()];
  return Array.isArray(value) ? value[0] : value;
}

function constantTimeEqual(left, right) {
  const leftBuffer = Buffer.from(left, "utf8");
  const rightBuffer = Buffer.from(right, "utf8");
  if (leftBuffer.length !== rightBuffer.length) return false;
  return crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

function verifySignature(timestamp, body, signature, secret) {
  if (!timestamp || !signature || !secret) return false;
  const match = /^sha256=([a-fA-F0-9]{64})$/.exec(signature);
  if (!match) return false;
  const expected = crypto
    .createHmac("sha256", secret)
    .update(`${timestamp}.`)
    .update(body)
    .digest("hex");
  return constantTimeEqual(match[1].toLowerCase(), expected);
}

function timestampIsFresh(timestamp, nowSeconds = Math.floor(Date.now() / 1000)) {
  if (!/^\d+$/.test(String(timestamp))) return false;
  return Math.abs(nowSeconds - Number(timestamp)) <= TIMESTAMP_WINDOW_SECONDS;
}

function addRequest(entry) {
  requests.push(entry);
  if (requests.length > MAX_REQUESTS) requests.shift();
}

function sendJson(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    "content-type": "application/json",
    "content-length": Buffer.byteLength(body)
  });
  response.end(body);
}

function readRequestBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on("data", chunk => chunks.push(Buffer.from(chunk)));
    request.on("end", () => resolve(Buffer.concat(chunks)));
    request.on("error", reject);
  });
}

function serializeEntry(entry) {
  return {
    id: entry.id,
    time: entry.time,
    path: entry.path,
    headers: entry.headers,
    bodyBase64: entry.body.toString("base64"),
    deliveryCount: entry.deliveryCount
  };
}

const indexHtml = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");

function createServer() {
  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url, "http://localhost");

      if (request.method === "GET" && url.pathname === "/healthz") {
        response.writeHead(200, { "content-type": "text/plain" });
        response.end("ok");
        return;
      }

      if (request.method === "GET" && url.pathname === "/") {
        response.writeHead(200, {
          "content-type": "text/html; charset=utf-8",
          "content-length": Buffer.byteLength(indexHtml)
        });
        response.end(indexHtml);
        return;
      }

      if (request.method === "GET" && url.pathname === "/_requests") {
        sendJson(response, 200, requests.map(serializeEntry));
        return;
      }

      if (request.method === "DELETE" && url.pathname === "/_requests") {
        requests.length = 0;
        sendJson(response, 200, {});
        return;
      }

      if (request.method === "POST" && url.pathname === "/_verify") {
        response.writeHead(404);
        response.end();
        return;
      }

      if (request.method === "POST") {
        const body = await readRequestBody(request);
        const headers = Object.fromEntries(
          Object.entries(request.headers).map(([key, value]) => [
            key,
            Array.isArray(value) ? value : String(value)
          ])
        );
        const delivery = headerValue(request.headers, "x-swiftdrop-delivery");
        const prior = delivery ? requests.filter(entry => headerValue(entry.headers, "x-swiftdrop-delivery") === delivery) : [];

        addRequest({
          id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
          time: new Date().toISOString(),
          path: url.pathname + url.search,
          headers,
          body: Buffer.from(body),
          deliveryCount: delivery ? prior.length + 1 : 0
        });
        sendJson(response, 200, {});
        return;
      }

      response.writeHead(404);
      response.end();
    } catch (error) {
      response.writeHead(500, { "content-type": "application/json" });
      response.end(JSON.stringify({ error: "internal server error" }));
    }
  });
}

if (require.main === module) {
  const port = Number(process.env.PORT || 9000);
  createServer().listen(port, "0.0.0.0", () => {
    console.log(`Mock merchant listening on port ${port}`);
  });
}

module.exports = {
  MAX_REQUESTS,
  TIMESTAMP_WINDOW_SECONDS,
  addRequest,
  constantTimeEqual,
  createServer,
  headerValue,
  requests,
  timestampIsFresh,
  verifySignature
};

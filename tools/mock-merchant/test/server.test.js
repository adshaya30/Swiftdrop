const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const {
  MAX_REQUESTS,
  addRequest,
  constantTimeEqual,
  createServer,
  requests,
  timestampIsFresh,
  verifySignature
} = require("../server.js");

test("HMAC known vector", () => {
  const body = Buffer.from("hello");
  const actual = crypto.createHmac("sha256", "whsec_test")
    .update("1700000000.")
    .update(body)
    .digest("hex");
  assert.match(actual, /^[a-f0-9]{64}$/);
  assert.equal(actual, "457c4d31b232d95fcd50f1d95021aaaffddd859d0ee8a206675e84ccc977431d");
  assert.equal(verifySignature("1700000000", body, `sha256=${actual}`, "whsec_test"), true);
});

test("constant-time compare function is available and compares safely", () => {
  assert.equal(constantTimeEqual("abc", "abc"), true);
  assert.equal(constantTimeEqual("abc", "abd"), false);
  assert.equal(constantTimeEqual("abc", "abcd"), false);
});

test("timestamp must be within five minutes", () => {
  assert.equal(timestampIsFresh(1000, 1000), true);
  assert.equal(timestampIsFresh(700, 1000), true);
  assert.equal(timestampIsFresh(699, 1000), false);
  assert.equal(timestampIsFresh(1300, 1000), true);
  assert.equal(timestampIsFresh(1301, 1000), false);
});

test("UI contains no innerHTML", () => {
  const html = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
  assert.doesNotMatch(html, /innerHTML/);
});

test("POST stores raw bytes and API exposes them", async () => {
  requests.length = 0;
  const body = Buffer.from([0, 255, 65, 66]);
  const server = createServer().listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const port = server.address().port;

  const response = await fetch(`http://127.0.0.1:${port}/hook`, {
    method: "POST",
    headers: { "x-test": "yes" },
    body
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {});
  assert.deepEqual(requests[0].body, body);

  const list = await (await fetch(`http://127.0.0.1:${port}/_requests`)).json();
  assert.deepEqual(Buffer.from(list[0].bodyBase64, "base64"), body);
  await new Promise(resolve => server.close(resolve));
});

test("in-memory requests are capped at 200", () => {
  requests.length = 0;
  for (let i = 0; i < MAX_REQUESTS + 25; i++) {
    addRequest({ id: String(i), headers: {}, body: Buffer.from("") });
  }
  assert.equal(requests.length, MAX_REQUESTS);
  assert.equal(requests[0].id, "25");
  assert.equal(requests.at(-1).id, String(MAX_REQUESTS + 24));
});

test("healthz, UI, and clear requests endpoints work as expected", async () => {
  requests.length = 0;
  const server = createServer().listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const port = server.address().port;

  // GET /healthz
  const healthRes = await fetch(`http://127.0.0.1:${port}/healthz`);
  assert.equal(healthRes.status, 200);
  assert.equal(await healthRes.text(), "ok");

  // GET /
  const uiRes = await fetch(`http://127.0.0.1:${port}/`);
  assert.equal(uiRes.status, 200);
  assert.match(uiRes.headers.get("content-type"), /text\/html/);

  // Send a webhook request
  await fetch(`http://127.0.0.1:${port}/webhook-test`, {
    method: "POST",
    headers: { "x-swiftdrop-delivery": "del-123" },
    body: JSON.stringify({ event: "DELIVERY_UPDATED" })
  });
  assert.equal(requests.length, 1);
  assert.equal(requests[0].deliveryCount, 1);

  // Send a duplicate/retry delivery request
  await fetch(`http://127.0.0.1:${port}/webhook-test`, {
    method: "POST",
    headers: { "x-swiftdrop-delivery": "del-123" },
    body: JSON.stringify({ event: "DELIVERY_UPDATED" })
  });
  assert.equal(requests.length, 2);
  assert.equal(requests[1].deliveryCount, 2);

  // DELETE /_requests
  const delRes = await fetch(`http://127.0.0.1:${port}/_requests`, { method: "DELETE" });
  assert.equal(delRes.status, 200);
  assert.equal(requests.length, 0);

  await new Promise(resolve => server.close(resolve));
});


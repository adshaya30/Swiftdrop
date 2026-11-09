# SwiftDrop Mock Merchant

This demo webhook receiver runs on Node.js 22 with no third-party packages.

## Run locally

```sh
cd tools/mock-merchant
npm test
node server.js
```

Open http://localhost:9000/. The service listens on container port `9000`.

## Docker

Runtime image used: `node:22-alpine`. This satisfies the SRS build-stage image reference while keeping Node available in the demo runtime.

```sh
docker build -t swiftdrop/mock-merchant tools/mock-merchant
docker run --rm -p 9090:9000 swiftdrop/mock-merchant
```

The UI accepts webhook secrets only in browser memory. Verification is performed in the browser with Web Crypto so the secret is never transmitted to the server.

## Demo use

Save webhook URLs as `http://mock-merchant:9000/<any path>`. The demo backend must allow this destination with:

```text
WEBHOOK_ALLOW_PRIVATE_TARGETS=true
WEBHOOK_ALLOW_HTTP=true
```

## Signature algorithm

The documented algorithm is:

```text
expected = HMAC-SHA256(secret, timestamp + "." + raw_request_body_bytes)
header = sha256=<lowercase hexadecimal digest>
```

The timestamp must be no more than 300 seconds from the current Unix timestamp. Known vector: with secret `whsec_test`, timestamp `1700000000`, and body `hello`, the signature is documented by the test suite using Node crypto.

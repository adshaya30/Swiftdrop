# Merchant API demo (DEP-015)

The SwiftDrop website has no create-order screen: merchants create orders from their own system through the API
(D-23). These tools show that side of the demo (Flow 4.3, scenario AT-14) against the running stack.

## Before you start

1. Start the stack (`docker compose up -d --build`) so it answers on `http://localhost:8080`.
2. Register a merchant at `/register-merchant` and approve it as an admin (`/admin/merchants`).
3. Log in as the merchant, open **API credentials** and create a credential. Copy the `client_id` (`sdc_...`) and the
   `client_secret` (`sds_...`): the secret is shown only once.

## Option A: curl script

```bash
CLIENT_ID=sdc_... CLIENT_SECRET=sds_... ./tools/postman/merchant-demo.sh
```

Set `BASE_URL` to use another address. The script needs only bash, curl, grep and sed (Git Bash on Windows has all of
them). It prints `PASS` or `FAIL` for each step and exits with an error if any step fails.

## Option B: Postman

1. Import `SwiftDrop-Merchant.postman_collection.json`.
2. In the collection's **Variables**, set `clientId` and `clientSecret` (and `baseUrl` if needed).
3. Run the collection with the **Collection Runner**, in order. Each request has tests that check the expected result.

## The steps

| # | Request | Expected |
|---|---|---|
| 1 | `POST /api/v1/oauth/token` (form-encoded, `grant_type=client_credentials`) | 200 with a 15-minute bearer token |
| 2 | `POST /api/v1/merchant/orders` with an `Idempotency-Key` | 201 with tracking number, discounted fee and `trackingUrl` |
| 3 | The same request again, same key and body | 201 with `Idempotent-Replay: true`, the same order, no second order |
| 4 | The same `merchantReference` with a new key | 409 `DUPLICATE_MERCHANT_REFERENCE` |
| 5 | A drop-off district that does not exist | 400 `VALIDATION_ERROR` |
| 6 | `GET /api/v1/merchant/orders?merchantReference=...` | 200 with the order |
| 7 | `POST /api/v1/merchant/orders/{id}/cancel` | 200, status `CANCELLED` |

The order from step 2 also appears on the merchant dashboard and the Orders page. A suspended merchant gets 403
`MERCHANT_NOT_ACTIVE` on steps 2 and 7: suspend the merchant as an admin and run the script again to see it.

## Troubleshooting

| Problem | Cause |
|---|---|
| Step 1 gives 401 `invalid_client` | Wrong id or secret, revoked credential, or the merchant is not APPROVED |
| Step 2 gives 403 `MERCHANT_NOT_ACTIVE` | The merchant or its user is not active |
| Step 2 gives 503 | pricing-service or auth-service is not running |
| `curl: (7) Failed to connect` | The stack is not running on `BASE_URL` |

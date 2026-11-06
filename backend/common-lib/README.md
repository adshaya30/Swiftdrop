# common-lib

Shared servlet library for all SwiftDrop services except the gateway (the gateway uses only `common-core`).
Owner of everything except the events part: Member 3. Events, outbox and audit: Member 6.

## Start a service with it

1. Add the dependency `com.swiftdrop:common-lib` (and `common-core`).
2. Provide one bean, `SecurityRules`.
3. Set the environment variables below. No other wiring is needed.

```java
@Bean
SecurityRules securityRules() {
    return new SecurityRules() {
        public List<String> publicPaths() { return List.of("POST /api/v1/auth/login"); }
        public List<String> passwordChangeWhitelist() { return List.of("POST /api/v1/auth/change-password"); }
    };
}
```

Patterns are `"METHOD /path"` (Ant style, `*` for any method). Everything not listed is authenticated (deny by default).
`/actuator/health/**` is always public.

| Variable | Purpose |
|---|---|
| `JWT_PUBLIC_KEY_PATH` | X.509 `BEGIN PUBLIC KEY` PEM. auth-service overrides `JwtPublicKeyProvider` instead. |
| `INTERNAL_API_KEY` | Secret for `/internal/**`. The service refuses to start if it is missing, under 32 characters or a placeholder. |
| `LOG_LEVEL` | Root log level, default `INFO`. |

## What you get

- **JWT verification:** RS256 only, `iss=swiftdrop`, `aud=swiftdrop-api`, 30 s clock skew. `role` becomes `ROLE_<role>`, each scope `SCOPE_<scope>`. Use `@PreAuthorize`.
- **Current caller:** `CurrentUsers.get()` returns `CurrentUser(userId, role, typ, mid, scopes, passwordChangeRequired)`.
  `OwnershipPolicy.isOwnerOrAdmin(user, customerId, merchantId)`: if it is false, answer `404 NOT_FOUND` (BR-25).
- **Token-type rule (once, here):** `typ=client` tokens are accepted only on `/api/v1/merchant/orders` and below, otherwise `403 ACCESS_DENIED`. Do not re-implement it.
- **`pwc=true`:** `403 PASSWORD_CHANGE_REQUIRED` except on `passwordChangeWhitelist()`.
- **`/internal/**`:** its own chain, secured only by `X-Internal-Key` (constant-time compare). JWTs are ignored there.
- **Request id:** `X-Request-Id` is read or created, put in the MDC as `requestId` and returned.
- **JSON logging** on stdout, strict JSON input (unknown properties give 400), Hikari and Flyway start-up retry for 120 s,
  readiness = database only (never the broker).

## Errors

Throw `DomainException(ErrorCode, safeDetail[, errors[, extensions]])` from common-core. The `GlobalExceptionHandler` answers
with an RFC 9457 `application/problem+json` body (`type, title, status, detail, code, traceId, timestamp, errors[]`);
`extensions` are written as extra top-level members (for example `newFee` for `FEE_CHANGED`).
Validation, unknown JSON property, optimistic lock (409 `CONCURRENT_UPDATE`), too large (413), media type (415) and
unexpected errors (500 `INTERNAL_ERROR`, `traceId` only) are mapped for you. Never put personal data in `detail`.
A wrong HTTP method is answered as `404 NOT_FOUND` because SRS 7.2 has no 405 code.

## Calling another service (SRS 3.3, 7.4)

```java
RestClient client = factory.create("http://auth-service:8081");   // InternalRestClientFactory bean
client.get().uri("/internal/v1/users/{id}/contact", id).retrieve().body(ContactDto.class);
```

5 s connect and 10 s read timeouts, `X-Internal-Key` and `X-Request-Id` are sent. A callee business error is rethrown as
the same `DomainException` (code, detail, extensions); connection failures, timeouts and 5xx become `503 SERVICE_UNAVAILABLE`.

## Testing authorisation without auth-service

Add the test-jar (`<type>test-jar</type>`, scope `test`) and use `com.swiftdrop.common.testsupport.TestTokens`:

```java
static final TestTokens TOKENS = TestTokens.create();

@Bean JwtPublicKeyProvider keys() { return TOKENS.publicKeyProvider(); }   // in a test configuration

mockMvc.perform(get("/api/v1/x").header("Authorization", TOKENS.admin().bearer()));
TOKENS.customer().userId(id).bearer();
TOKENS.merchant(mid).bearer();
TOKENS.client(mid, "orders:read", "orders:write").bearer();   // typ=client
TOKENS.customer().passwordChangeRequired(true).bearer();
TOKENS.customer().expired().bearer();
TOKENS.customer().signedBy(OTHER).bearer();                   // wrong key
```

Set `INTERNAL_API_KEY` (32+ characters) in the test properties. Do not use `TestApp` or `TestController`: they are common-lib's own tests.

## Events (Member 6)

`com.swiftdrop.common.events.EventsAutoConfiguration` is an empty placeholder created by Member 3 and now yours.
All dependencies you need (AMQP, JPA, retry, Jackson, Micrometer, Testcontainers, WireMock) are already in this `pom.xml`.

# Google OAuth 2.0 PKCE Sequence Diagram (Flow 4.5)

This document visualizes the Google OAuth 2.0 PKCE authentication flow for SwiftDrop per **SRS v2.1 (Section 4, Flow 4.5; FR-AUTH-009, FR-AUTH-010, FR-AUTH-011)** and **`MEMBER_02.md`**.

```mermaid
sequenceDiagram
    autonumber
    actor Customer as User / Customer
    participant SPA as Browser / SwiftDrop SPA
    participant GW as API Gateway / Reverse Proxy
    participant Auth as auth-service
    participant DB as PostgreSQL (users, oauth_login_states)
    participant Google as Google Identity (OAuth 2.0 & OIDC)

    %% Step 1: Feature check
    Customer->>SPA: Navigate to /login
    SPA->>GW: GET /api/v1/auth/config
    GW->>Auth: GET /api/v1/auth/config
    Auth-->>SPA: {"googleEnabled": true}
    SPA->>Customer: Display "Continue with Google" button

    %% Step 2: Initiation
    Customer->>SPA: Click "Continue with Google"
    SPA->>GW: GET /api/v1/auth/google/login
    GW->>Auth: GET /api/v1/auth/google/login
    Note over Auth: 1. Generate random state, nonce, code_verifier<br/>2. Compute code_challenge = S256(code_verifier)<br/>3. Compute state_hash = SHA-256(state)
    Auth->>DB: INSERT into oauth_login_states (state_hash, nonce, code_verifier, expires_at)
    Auth-->>SPA: 302 Found (Google Auth URL)<br/>Set-Cookie: sd_oauth=<raw_state>; HttpOnly; SameSite=Lax; Path=/api/v1/auth/google; Max-Age=300

    %% Step 3: Google Consent
    SPA->>Google: Redirect to Google authorization endpoint
    Customer->>Google: Authenticate & Grant consent (openid, email, profile)

    alt User cancels consent
        Google-->>SPA: Redirect to /api/v1/auth/google/callback?error=access_denied
        SPA->>Auth: GET /api/v1/auth/google/callback?error=access_denied
        Auth-->>SPA: 302 Redirect to /login?error=google_denied<br/>Set-Cookie: sd_oauth=; Max-Age=0
        SPA->>Customer: Display "Google sign-in was cancelled"
    else Consent granted
        Google-->>SPA: Redirect to /api/v1/auth/google/callback?code=<code>&state=<raw_state>
        SPA->>GW: GET /api/v1/auth/google/callback?code=<code>&state=<raw_state><br/>Cookie: sd_oauth=<cookie_state>
        GW->>Auth: Forward callback request

        Note over Auth: Verify state == sd_oauth cookie
        Auth->>DB: SELECT * FROM oauth_login_states WHERE state_hash = SHA-256(state) AND expires_at > now()
        
        alt State mismatch or expired
            Auth-->>SPA: 302 Redirect to /login?error=google_failed<br/>Set-Cookie: sd_oauth=; Max-Age=0
            SPA->>Customer: Display "Google sign-in could not be completed"
        else State valid
            Auth->>DB: DELETE FROM oauth_login_states WHERE state_hash = SHA-256(state) (one-time use)

            %% Step 4: Token exchange
            Auth->>Google: POST /token (code, client_id, client_secret, code_verifier, grant_type=authorization_code)
            Google-->>Auth: 200 OK {id_token, access_token}

            %% Step 5: ID Token Validation
            Note over Auth: Validate ID Token with Nimbus JOSE:<br/>- Signature against Google JWKS<br/>- iss in (accounts.google.com, https://accounts.google.com)<br/>- aud == client_id<br/>- exp > now()<br/>- nonce == login_state.nonce<br/>- email_verified == true

            alt ID token invalid or email not verified
                Auth-->>SPA: 302 Redirect to /login?error=google_failed<br/>Set-Cookie: sd_oauth=; Max-Age=0
            else Token valid
                %% Step 6: Account Resolution
                Auth->>DB: SELECT * FROM users WHERE google_sub = claims.sub
                alt Existing linked Google account
                    alt Account is DEACTIVATED
                        Auth-->>SPA: 302 Redirect to /login?error=account_deactivated
                    else Account ACTIVE
                        Note over Auth: Update last_login_at
                        Auth->>DB: UPDATE users SET last_login_at = now() WHERE id = user.id
                        Note over Auth: Issue refresh token (RefreshTokenService)
                        Auth->>DB: INSERT into refresh_tokens (token_hash, user_id, family_id, expires_at)
                        Auth-->>SPA: 302 Redirect to /auth/callback<br/>Set-Cookie: sd_refresh=<token>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth<br/>Set-Cookie: sd_oauth=; Max-Age=0
                    end
                else No account with google_sub
                    Auth->>DB: SELECT * FROM users WHERE email = lower(claims.email)
                    alt Local account exists with same email
                        Note over Auth: NEVER MERGE! Conflict rule FR-AUTH-010
                        Auth-->>SPA: 302 Redirect to /login?error=account_exists<br/>Set-Cookie: sd_oauth=; Max-Age=0
                        SPA->>Customer: Display "An account with this email already exists"
                    else No existing account
                        Note over Auth: Create new CUSTOMER account<br/>(auth_provider=GOOGLE, password_hash=null)
                        Auth->>DB: INSERT into users (email, full_name, role, auth_provider, status, google_sub)
                        Note over Auth: Issue refresh token (RefreshTokenService)
                        Auth->>DB: INSERT into refresh_tokens (token_hash, user_id, family_id, expires_at)
                        Auth-->>SPA: 302 Redirect to /auth/callback<br/>Set-Cookie: sd_refresh=<token>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth<br/>Set-Cookie: sd_oauth=; Max-Age=0
                    end
                end
            end
        end
    end

    %% Step 7: SPA restores session
    SPA->>GW: POST /api/v1/auth/refresh (Cookie: sd_refresh, Header: X-Requested-With: SwiftDrop)
    GW->>Auth: POST /api/v1/auth/refresh
    Auth-->>SPA: 200 OK {accessToken, user: {id, fullName, role, mustChangePassword}}
    SPA->>Customer: Navigate to Customer Dashboard (/customer/dashboard)
```

---

## Error Redirect Codes Reference

| Error Code | Trigger Condition | User-Facing Message |
|---|---|---|
| `google_denied` | User clicked cancel or denied permission on Google consent screen | "Google sign-in was cancelled. Please try again or use your password." |
| `google_failed` | State mismatch, expired state, network failure, ID token verification failure, or unverified email | "Google sign-in could not be completed. Please try again." |
| `account_exists` | Email already registered with password/local auth (`auth_provider=LOCAL`). No auto-merge allowed | "An account with this email already exists. Please log in with your email and password." |
| `account_deactivated` | User account has been deactivated by an administrator | "Your account is deactivated. Please contact customer support." |


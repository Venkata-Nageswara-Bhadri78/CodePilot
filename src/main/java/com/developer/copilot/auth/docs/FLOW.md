# Auth Flows

These workflows are the ones implemented in `AuthServiceImpl`, the security filters, and `AuthTokenCleanupJob`. Defaults below come from `AuthProperties` (overridable via `app.auth.*`).

## Request lifecycle

Every call to `/api/v1/auth/**` follows the same outer path. Public routes skip authentication; protected routes (`/me`, `/logout`, `/logout-all`, `/complete-username`, `/setup-password/**`, `/extension-token`) require a live access JWT. `/me`, logout, and `/complete-username` allow a web user who still needs a username. `/extension-token` rejects browser-extension JWTs and users without a username.

```mermaid
sequenceDiagram
    participant C as Client
    participant RL as AuthRateLimitFilter
    participant JWT as JwtAuthenticationFilter
    participant SEC as SecurityConfig
    participant CTL as AuthController
    participant SVC as AuthServiceImpl
    participant DB as MySQL
    C->>RL: HTTP request
    alt POST on a limited public path and IP over limit
        RL-->>C: 429 ApiResponse plus Retry-After
    else allowed or path not IP-limited
        RL->>JWT: continue
        JWT->>JWT: optional Bearer validation
        JWT->>SEC: continue
        alt protected and not authenticated
            SEC-->>C: 401 Unauthorized.
        else protected, extension client, not job-extraction
            SEC-->>C: 403 This client is not authorized to access this resource.
        else
            SEC->>CTL: dispatch
            CTL->>SVC: use case
            SVC->>DB: read/write
            SVC-->>CTL: result or exception
            CTL-->>C: ApiResponse
        end
    end
```

## Register

Goal: create an unverified account or silently no-op if the username or email already exists. The HTTP contract is always `201` `"User registered successfully."` so callers cannot probe occupancy from the status code.

```mermaid
flowchart TD
    A[POST /register] --> B[Validate DTO]
    B --> C[Normalize username and email to lowercase]
    C --> D[Per-email register rate limit]
    D --> E{Username or email exists?}
    E -->|yes| Z[Return 201 no write]
    E -->|no| F[Save User enabled false verified false tokenVersion 0]
    F --> G{Unique constraint race?}
    G -->|yes| Z
    G -->|no| H[Replace OTP rows for user]
    H --> I[Store HMAC of 6-digit OTP]
    I --> J[After commit send OTP email]
    J --> Z
```

Side effects on a real insert: BCrypt password, `Role.USER`, OTP row, SMTP after commit. Mail failures after commit are logged and swallowed (`sendMailSafely`); the account still exists. The user can call resend-otp.

## Verify email

Uses the latest `EmailVerification` for that email (`PESSIMISTIC_WRITE`).

```mermaid
flowchart TD
    A[POST /verify-email] --> B[Per-email verify rate limit]
    B --> C{Latest OTP row?}
    C -->|none| N1[400 OTP not found.]
    C -->|exists| D{Already verified?}
    D -->|yes| N2[400 OTP already used.]
    D -->|no| E{Expired?}
    E -->|yes| N3[400 OTP has expired.]
    E -->|no| F{failedAttempts >= max?}
    F -->|yes| N4[400 OTP not found.]
    F -->|no| G{HMAC matches?}
    G -->|no| H[Increment failedAttempts]
    H --> N5[400 Invalid OTP.]
    G -->|yes| I[Mark OTP verified]
    I --> J[User enabled true emailVerified true]
```

Default: 10-minute OTP, 5 attempts. After the attempt cap, the message is `"OTP not found."` rather than `"Invalid OTP."`

## Resend OTP

Always HTTP `200` `"If the account requires verification, an OTP has been sent."`

Mail is sent only when the email exists, the account is not yet verified, and mail cooldown allows it (`tryAcquireMail` on the email, default 60 seconds). Verified accounts, unknown emails, and cooldown skips are silent.

## Login

```mermaid
flowchart TD
    A[POST /login] --> B[Per-email login rate limit]
    B --> C{Failed-login window blocked?}
    C -->|yes| D[Dummy BCrypt then 401 same message]
    C -->|no| E{User by email?}
    E -->|no or password is null| F[Dummy BCrypt, record failure, 401]
    E -->|yes| G{Password matches?}
    G -->|no| H[Record failure, 401]
    G -->|yes| I{Enabled and emailVerified?}
    I -->|no| J[401 same message, no failure record]
    I -->|yes| K[Clear failure window]
    K --> L[Cap active refresh tokens]
    L --> M[New refresh UUID plus access JWT]
```

The client-visible message is always `"Invalid email or password."` Dummy BCrypt on unknown users, passwordless Google accounts, and blocked emails keeps timing closer to a real password check.

Unverified or disabled accounts that present the correct password do **not** increment the failure counter.

## Continue with Google

The browser Google Identity Services button returns an ID token. The frontend posts it to `POST /api/v1/auth/google`. Verification runs **before** the database transaction. A Google ID token is not an application bearer token.

```mermaid
flowchart TD
    A[POST /google idToken] --> B[Per-IP google-ip limit]
    B --> C[Verify signature issuer audience expiry subject email]
    C -->|invalid| D[401 Invalid Google token]
    C -->|valid| E[Per-email google-email limit]
    E --> F{Identity for GOOGLE plus sub?}
    F -->|yes and active| G[Issue access JWT and refresh UUID]
    F -->|yes and disabled| H[401 This account is disabled]
    F -->|no| I{users row for that email?}
    I -->|yes| J[409 email already exists no silent link]
    I -->|no| K[Insert user username null password null plus user_identities]
    K --> G
    G --> L{username null?}
    L -->|yes| M[onboardingComplete false]
    L -->|no| N[onboardingComplete true]
```

A unique-constraint race re-reads `(GOOGLE, sub)` and continues as that user. If the email row appeared without this subject, the response stays 409.

`POST /complete-username` then normalizes the username, rejects a taken or already-different name with 409, saves it, and returns a new access JWT with `onboardingComplete: true`. Until that save, `/me`, logout, and complete-username are the only authenticated web routes. Jobs, AI, chat, resumes, job extraction, and extension-token return 403.

## Set a password after Google

`POST /setup-password/request` (web JWT, username already set) emails a 6-digit OTP stored in `password_setup_otp`, separate from registration OTP. `POST /setup-password/confirm` checks the HMAC, stores a BCrypt password, bumps `tokenVersion`, revokes refresh tokens, and returns a new session. Google sign-in for the same `sub` still works, and `POST /login` works with the new password.

Forgot-password also emails a reset link when `password` is null. Reset stores the hash and does not mark onboarding complete.

## Refresh token

The path is `permitAll`. The refresh UUID is the credential. Lookup uses SHA-256 of the raw UUID with `PESSIMISTIC_WRITE`.

```mermaid
flowchart TD
    A[POST /refresh-token] --> B[Per-IP refresh limit]
    B --> C{Row for SHA-256?}
    C -->|no| N1[401 Invalid refresh token.]
    C -->|yes| D{Revoked?}
    D -->|yes and replacedByToken set| E[Revoke all refresh tokens for user]
    E --> N2[401 Refresh token has been revoked.]
    D -->|yes and no replacement| N2
    D -->|no| F{Expired?}
    F -->|yes| N3[401 Refresh token has expired.]
    F -->|no| G{User enabled and verified?}
    G -->|no| N1
    G -->|yes| H[Revoke current, persist new, set replacedByToken]
    H --> I[New access JWT plus new refresh UUID]
```

Replaying a token that was already rotated (`revoked` with `replacedByToken` set) is treated as theft: every active refresh token for that user is revoked. Replaying a token revoked by **logout** (no `replacedByToken`) returns revoked without wiping other sessions.

Refresh does not bump `tokenVersion`. Old access JWTs remain valid until they expire.

## Password reset

**Forgot** always returns `200` `"If the account exists, a password reset email has been sent."` If the user exists and mail cooldown allows (`identity` `reset:` + email), unused reset rows for that user are deleted, a new UUID is stored as SHA-256, and the raw UUID is emailed after commit.

**Reset** is public; the emailed UUID is the secret. It is **not** IP-rate-limited by `AuthRateLimitFilter`.

```mermaid
flowchart TD
    A[POST /reset-password] --> B{SHA-256 token row?}
    B -->|no| N1[400 Invalid password reset token.]
    B -->|yes| C{Used?}
    C -->|yes| N2[400 Password reset token is invalid or already used.]
    C -->|no| D{Expired?}
    D -->|yes| N3[400 Password reset token has expired.]
    D -->|no| E[BCrypt new password]
    E --> F[Bump tokenVersion]
    F --> G[Mark reset token used]
    G --> H[Revoke all refresh tokens]
```

Default reset expiry: 15 minutes.

## Logout vs logout-all

Both require a valid **web** access JWT. A browser-extension JWT is `403` on these paths.

```mermaid
flowchart LR
    subgraph one [POST /logout]
        L1[Load current user] --> L2[Find active refresh by hash]
        L2 --> L3{Same user?}
        L3 -->|no| L4[401 Invalid refresh token.]
        L3 -->|yes| L5[Set revoked true]
    end
    subgraph all [POST /logout-all]
        A1[Load current user] --> A2[Bump tokenVersion]
        A2 --> A3[Revoke all active refresh tokens]
    end
```

`/logout` does not bump `tokenVersion`. That device cannot refresh; its access JWT still works until expiry (~15 minutes). `/logout-all` makes existing access JWTs fail `tv` checks on the next request.

## Authenticated request to the rest of the API

```mermaid
sequenceDiagram
    participant C as Client
    participant F as JwtAuthenticationFilter
    participant U as UserRepository
    participant J as JwtService
    participant S as SecurityContext
    C->>F: Authorization Bearer accessJWT
    F->>J: extractUserId
    F->>U: findById
    alt missing user, disabled, unverified, or tv/expiry/signature fail
        F->>S: leave empty
        Note over F,S: Protected routes then 401 Unauthorized.
    else valid
        F->>S: CustomUserDetails plus extension authority and ONBOARDING_INCOMPLETE when username is null
    end
```

Database failures during user load are **not** swallowed; they propagate (typically `500`).

## Browser-extension token

The web frontend, already holding a full-privilege access JWT (no `cid`) for a user with a username, calls `POST /api/v1/auth/extension-token`. Auth mints a short-lived JWT with `cid=browser-extension` and **no** refresh token. The caller must not already be an extension client. A null username returns `403` `"Complete your username to continue."` `app.extension.enabled=false` returns `403` `"Browser extension access is disabled."`

```mermaid
sequenceDiagram
    participant W as Web JWT
    participant C as AuthController
    participant S as AuthServiceImpl
    participant J as JwtService
    W->>C: POST /extension-token Authorization Bearer
    C->>S: issueExtensionToken
    S->>S: reject if extension principal or disabled
    S->>S: consume extension-token per user id
    S->>J: generateExtensionToken
    J-->>S: JWT with cid browser-extension
    S-->>W: ExtensionAuthResponse no refreshToken
```

That JWT may call `/api/v1/job-extraction/**` and `/api/v1/automated-job-extraction/**`. Other authenticated routes, including `/me` and `/extension-token`, return `403` `"This client is not authorized to access this resource."`

How a website process delivers the JWT to a Chrome extension is outside the auth package. Details: [BROWSER-EXTENSION.md](AUTH-SERVICE-SPECIFIC-DOCS/BROWSER-EXTENSION.md).

## Hourly cleanup

`AuthTokenCleanupJob.purgeExpiredRows` runs at minute 0 of every hour (`cron 0 0 * * * *`), using the UTC clock:

- delete refresh rows that are revoked or past `expiresAt`
- delete reset rows that are used or expired
- delete OTP rows that are verified or expired

This is housekeeping, not a security event. Revocation is already recorded on the row before purge.

# Auth Endpoints

Base path: `/api/v1/auth`. All JSON responses use the shared `ApiResponse` envelope:

```json
{
  "success": true,
  "message": "…",
  "data": {},
  "timestamp": "2026-01-01T12:00:00"
}
```

On errors `data` is omitted/null and `success` is `false`. Validation failures (`400`) join field errors as `field: message, field: message`.

Unless noted, `Content-Type: application/json` is required. CSRF tokens are not used.

**Access JWT vs refresh UUID:** send the access JWT as `Authorization: Bearer <accessToken>`. The refresh value is **not** a JWT; send it in the JSON body of `/refresh-token` and `/logout`.

Rate limits: selected POSTs also have a **per-IP** limit in `AuthRateLimitFilter` (60-second window). Several routes add a **per-email** limit in `AuthServiceImpl`. Both return `429` with `Retry-After` and message `"Too many requests. Please try again later."` Defaults are in [CONFIGURATION.md](CONFIGURATION.md) and [RATE-LIMITING.md](AUTH-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

---

## Access summary

| Method | Path | Auth | IP limit | Email limit |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | Yes | Yes |
| POST | `/api/v1/auth/login` | Public | Yes | Yes + failure window |
| POST | `/api/v1/auth/google` | Public | Yes | Per verified email, after the token is checked |
| POST | `/api/v1/auth/google/link` | Bearer (web, username required) | Yes | Per user |
| POST | `/api/v1/auth/complete-username` | Bearer (web, onboarding allowed) | Yes | Per user |
| POST | `/api/v1/auth/setup-password/request` | Bearer (web, username required) | Yes | Per user + mail cooldown |
| POST | `/api/v1/auth/setup-password/confirm` | Bearer (web, username required) | Yes | Per user |
| POST | `/api/v1/auth/extension-token` | Bearer (web frontend JWT only, username required) | Yes | Per-user |
| POST | `/api/v1/auth/verify-email` | Public | Yes | Yes |
| POST | `/api/v1/auth/resend-otp` | Public | Yes | Yes + mail cooldown |
| POST | `/api/v1/auth/forgot-password` | Public | Yes | Yes + mail cooldown |
| POST | `/api/v1/auth/reset-password` | Public (token in body) | No | No |
| POST | `/api/v1/auth/refresh-token` | Public (UUID in body) | Yes | No |
| GET | `/api/v1/auth/me` | Bearer | No | No |
| POST | `/api/v1/auth/logout` | Bearer | No | No |
| POST | `/api/v1/auth/logout-all` | Bearer | No | No |
| GET | `/api/v1/test` | Bearer, **`dev` profile only** | No | No |

Missing/invalid JWT on protected routes: `401` `"Unauthorized."` (filter entry point), not the login message. Browser-extension JWT on `/me`, `/logout`, `/logout-all`, `/complete-username`, `/extension-token`, `/api/v1/test`, and other non-job-extraction routes: `403` `"This client is not authorized to access this resource."` A web user with no username may call `/me`, `/logout`, `/logout-all`, and `/complete-username`. Other routes return `403` `"Complete your username to continue."`

---

## POST `/api/v1/auth/register`

Creates an unverified account and emails an OTP. Duplicate username or email still returns **201** with the same body; no user or mail is written.

**Auth:** public.

**Body (`RegisterRequest`):**

| Field | Rules |
| --- | --- |
| `username` | Required, 3–50 chars. Stored trimmed lowercase. |
| `fullName` | Required, 3–100 chars. Trimmed. |
| `email` | Required, valid email, max 255. Stored trimmed lowercase. |
| `password` | `@ValidPassword`: 8–72 chars, at least one upper, lower, digit, and special from `@$!%*?&#^+=`. |

**Success:** `201`

```json
{
  "success": true,
  "message": "User registered successfully.",
  "data": null,
  "timestamp": "2026-01-01T12:00:00"
}
```

OTP is never in the JSON.

**Errors:** `400` validation; `429` rate limit. Unique-constraint races are treated like duplicates (still 201).

**Side effects:** BCrypt password; `Role.USER`; `enabled=false`; `emailVerified=false`; `tokenVersion=0`; HMAC OTP row; SMTP after commit.

---

## POST `/api/v1/auth/login`

**Auth:** public.

**Body (`LoginRequest`):**

| Field | Rules |
| --- | --- |
| `email` | Required, valid email, max 255. Normalized lowercase. |
| `password` | Required, max 72. Not `@ValidPassword` (login must accept the stored password). |

**Success:** `200` `"Login successful."` with `AuthResponse`:

```json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "refreshToken": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "onboardingComplete": true
  },
  "timestamp": "2026-01-01T12:00:00"
}
```

`onboardingComplete` is `true` when `username` is not null. Email/password accounts always have a username. A Google-only row (`password` null) still returns `401` `"Invalid email or password."` and does not throw.

**Errors:** `400` validation; `401` `"Invalid email or password."` for unknown email, wrong password, null password, unverified, disabled, or lockout; `429` rate limit.

**Side effects:** new refresh row (may revoke oldest active tokens if over `maxActiveRefreshTokens`); new access JWT; clears failed-login counter on success.

---

## POST `/api/v1/auth/extension-token`

Mints a **restricted** access JWT for the Chrome browser-extension client. Requires a live **web-frontend** access JWT. Extension tokens cannot call this endpoint.

**Auth:** Bearer (web). Not `permitAll`.

**Body:** none. No DTO. `Content-Type` is not required.

**Success:** `200` `"Browser extension access token issued."` with `ExtensionAuthResponse`:

```json
{
  "success": true,
  "message": "Browser extension access token issued.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "client": "browser-extension",
    "expiresIn": 900
  },
  "timestamp": "2026-01-01T12:00:00"
}
```

No refresh token is issued. The JWT includes signed `cid=browser-extension` plus the same `sub` / `email` / `role` / `tv` as a web access token.

**Errors:** `401` `"Unauthorized."`; `403` `"This client is not authorized to access this resource."` if the caller is already an extension client; `403` `"Complete your username to continue."` when `username` is null; `403` `"Browser extension access is disabled."` if `app.extension.enabled=false`; `429` rate limit.

**Authorization of the minted token:** `SecurityConfig` allows it on `/api/v1/job-extraction/**` and `/api/v1/automated-job-extraction/**` (any HTTP method). Other APIs return `403` `"This client is not authorized to access this resource."` See [BROWSER-EXTENSION.md](AUTH-SERVICE-SPECIFIC-DOCS/BROWSER-EXTENSION.md).

---

## POST `/api/v1/auth/google`

Verifies a Google Identity Services ID token and either signs in the linked user or creates one. There is no separate Google register endpoint. The ID token is not accepted as `Authorization: Bearer` on other APIs.

**Auth:** public.

**Body (`GoogleAuthRequest`):**

| Field | Rules |
| --- | --- |
| `idToken` | Required, max 4096 characters. |

**Success:** `200` `AuthResponse` (same fields as login, including `onboardingComplete`).

| `onboardingComplete` | Message |
| --- | --- |
| `true` | `"Login successful."` |
| `false` | `"Complete your username to continue."` |

New users are stored with `username` null, `password` null, `role=USER`, `enabled=true`, and `emailVerified=true`. Email and full name come from the verified token. Full name falls back to the email local-part (padded with `user` when shorter than 3 characters) when Google's name is missing or shorter than 3 characters. That fallback is not a username.

**Errors:**

| HTTP | Message |
| --- | --- |
| 400 | Validation, including a blank or oversized `idToken` |
| 401 | `"Invalid Google token."` (bad signature, issuer, audience, expiry, missing subject, missing email, or `email_verified` not true) |
| 401 | `"This account is disabled."` when the linked user is disabled or not email-verified |
| 409 | `"An account with this email already exists. Sign in with your email and password."` when the email exists and this Google subject is not linked |
| 429 | Rate limit |

Google sign-in never grants `ADMIN`. It does not merge into an existing password account. Linking is a separate signed-in call, `POST /api/v1/auth/google/link`.

---

## POST `/api/v1/auth/google/link`

Attaches a verified Google subject to the account that is already signed in. The caller must present both a web access JWT and a Google ID token. Matching emails are not enough: `POST /google` still returns 409 when the email exists and this subject is not linked.

**Auth:** Bearer web JWT. The account must already have a username. Extension tokens and incomplete Google users are rejected.

**Body:** same as `/google`, `{ "idToken": "..." }`.

**Success:** `200` `"Google account linked successfully."` No new tokens. The current session stays. Username, password, email, full name, and role are unchanged. Calling it again with the same Google subject is also 200. After this, `POST /google` with that subject signs into this same account, and email/password login still works.

**Errors:**

| HTTP | Message |
| --- | --- |
| 400 | Blank or oversized `idToken` |
| 401 | `"Unauthorized."` or `"Invalid Google token."` |
| 403 | Extension client, or `"Complete your username to continue."` |
| 409 | `"The Google account email does not match this account."` |
| 409 | `"This Google account is already linked to another user."` |
| 409 | `"A Google account is already linked to this user."` when a different Google subject is already stored |
| 429 | Rate limit |

---

## POST `/api/v1/auth/complete-username`

Saves the username for a Google user. Onboarding is complete only after this succeeds.

**Auth:** Bearer web JWT. Incomplete users are allowed. Extension JWTs are rejected.

**Body (`CompleteUsernameRequest`):** `username` required, 3–50 characters. Stored trimmed and lowercase.

**Success:** `200` `"Username saved successfully."` with a new access JWT and a new refresh UUID. `onboardingComplete` is `true`. Submitting the username already stored also returns 200 and new tokens. The previous refresh UUID stays valid until it expires or is rotated; the next refresh reads `username` from the database.

**Errors:** `400` when the normalized username is outside 3–50 (`"username: size must be between 3 and 50"`); `401` missing/invalid JWT; `403` extension client; `409` `"Username is already taken."`; `409` `"Username is already set."` when a different username is submitted after one was saved; `429` rate limit.

---

## POST `/api/v1/auth/setup-password/request`

Emails a 6-digit OTP so a signed-in user can add or replace a password. Intended for Google accounts that later want email/password login. The same account keeps its Google identity.

**Auth:** Bearer web JWT with a username. Incomplete users and extension JWTs are rejected.

**Body:** none.

**Success:** `200`. `"A verification code has been sent to your email."` when a new OTP was stored. `"A verification code was sent recently. Use that code or wait before requesting another."` when the mail cooldown skips a new send. The OTP is never in the JSON.

**Errors:** `401` unauthorized; `403` extension client or `"Complete your username to continue."`; `429` rate limit.

---

## POST `/api/v1/auth/setup-password/confirm`

**Auth:** same as the request endpoint.

**Body (`SetupPasswordConfirmRequest`):** `otp` exactly 6 digits; `newPassword` uses `@ValidPassword`.

**Success:** `200` `"Password saved successfully."` with a new `AuthResponse`. The password is BCrypt-hashed, `tokenVersion` increments, and previous refresh tokens are revoked. Google sign-in for that identity still works. Email/password login works on the next `POST /login`.

**Errors:** `400` for a weak password or `"OTP not found."`, `"OTP already used."`, `"OTP has expired."`, `"Invalid OTP."`; `401` unauthorized; `403` as on the request endpoint; `429` rate limit.

Forgot-password / reset-password can also set a password when `users.password` is null. Reset does not skip username completion.

---

## POST `/api/v1/auth/verify-email`

**Auth:** public.

**Body (`VerifyOtpRequest`):**

| Field | Rules |
| --- | --- |
| `email` | Required, valid email, max 255. |
| `otp` | Required, exactly 6 digits (`^\\d{6}$`). |

**Success:** `200` `"Email verified successfully."`

**Errors:** `400` validation or:

| Message | Cause |
| --- | --- |
| `OTP not found.` | No row, or failed-attempt cap reached |
| `OTP already used.` | Row already verified |
| `OTP has expired.` | `expiresAt` in the past |
| `Invalid OTP.` | HMAC mismatch (increments `failedAttempts`) |

`429` rate limit.

**Side effects:** `enabled=true`, `emailVerified=true`, OTP marked verified.

---

## POST `/api/v1/auth/resend-otp`

**Auth:** public.

**Body (`ResendOtpRequest`):** `email` required, valid, max 255.

**Success:** always `200` `"If the account requires verification, an OTP has been sent."` whether or not mail was sent.

**Errors:** `400` validation; `429` rate limit.

**Side effects:** only for an existing unverified user passing mail cooldown: delete prior OTP rows, insert new HMAC OTP, SMTP after commit.

---

## POST `/api/v1/auth/forgot-password`

**Auth:** public.

**Body (`ForgotPasswordRequest`):** `email` required, valid, max 255.

**Success:** always `200` `"If the account exists, a password reset email has been sent."`

**Errors:** `400` validation; `429` rate limit.

**Side effects:** if the user exists and cooldown allows: delete unused reset tokens, save SHA-256 of a new UUID, email the **raw** UUID after commit.

---

## POST `/api/v1/auth/reset-password`

**Auth:** public. Possession of the reset UUID is the authorization.

**Body (`ResetPasswordRequest`):**

| Field | Rules |
| --- | --- |
| `token` | Required, max 128. Raw UUID from email, not the stored hash. |
| `newPassword` | `@ValidPassword` (same rules as register). |

**Success:** `200` `"Password reset successfully."`

**Errors:** `400` validation or:

| Message | Status |
| --- | --- |
| `Invalid password reset token.` | 400 |
| `Password reset token is invalid or already used.` | 400 |
| `Password reset token has expired.` | 400 |

**Side effects:** new BCrypt password; `tokenVersion + 1`; reset row used; all active refresh tokens revoked. Existing access JWTs fail on the next request.

---

## GET `/api/v1/auth/me`

**Auth:** `Authorization: Bearer <accessToken>` required (web-frontend JWT; extension JWT → `403`).

**Success:** `200` `"Current user."` with `UserResponse`:

```json
{
  "success": true,
  "message": "Current user.",
  "data": {
    "id": 1,
    "username": "johndoe",
    "fullName": "John Doe",
    "email": "john.doe@example.com",
    "role": "USER",
    "passwordConfigured": true,
    "googleLinked": false
  },
  "timestamp": "2026-01-01T12:00:00"
}
```

Password, `enabled`, `emailVerified`, and `tokenVersion` are not returned. `username` may be null for a Google user who has not finished onboarding. `passwordConfigured` is true only when a password hash is stored. `googleLinked` is true when a `user_identities` row with provider `GOOGLE` exists for this user. Incomplete web users may call this route so the client can restore the username page after refresh.

**Errors:** `401` `"Unauthorized."` if the JWT is missing/invalid or the user is disabled/unverified. A browser-extension JWT is `403` `"This client is not authorized to access this resource."` before the controller. If a principal is present but not `CustomUserDetails`, `CurrentUserService` throws `401` `"User is not authenticated."`

---

## POST `/api/v1/auth/refresh-token`

**Auth:** public. The refresh UUID is the secret.

**Body (`RefreshTokenRequest`):** `refreshToken` required, max 128.

**Success:** `200` `"Token refreshed successfully."` with a new `AuthResponse` (new access JWT and **new** refresh UUID). The previous UUID is revoked and chained via `replacedByToken`. `onboardingComplete` follows the live `username`. An incomplete Google user stays incomplete after refresh.

**Errors:** `400` validation; `401`:

| Message |
| --- |
| `Invalid refresh token.` |
| `Refresh token has been revoked.` |
| `Refresh token has expired.` |

`429` per-IP limit.

**Side effects:** rotation; reuse of a rotated token revokes **all** refresh tokens for that user.

---

## POST `/api/v1/auth/logout`

**Auth:** Bearer required (web-frontend JWT; extension JWT → `403`). Body must be the **current session’s** refresh UUID.

**Body (`LogoutRequest`):** `refreshToken` required, max 128.

**Success:** `200` `"Logged out successfully."`

**Errors:** `400` validation; `401` `"Unauthorized."` (JWT) or `"Invalid refresh token."` (missing, already revoked, or belongs to another user).

**Side effects:** that refresh row `revoked=true`. Does **not** bump `tokenVersion`. Access JWT still works until expiry.

---

## POST `/api/v1/auth/logout-all`

**Auth:** Bearer required (web-frontend JWT; extension JWT → `403`). No body.

**Success:** `200` `"Logged out from all devices successfully."`

**Errors:** `401` `"Unauthorized."`

**Side effects:** `tokenVersion + 1`; all active refresh tokens revoked.

---

## GET `/api/v1/test` (development only)

**Not a production API.** `TestController` is `@Profile("dev")` and `@Hidden` from OpenAPI.

**Auth:** Bearer required (`anyRequest()` = authenticated and **not** `CLIENT_BROWSER_EXTENSION`).

**Success:** `200` with `data` `"JWT Authentication Successful"` and the same message field.

If the `dev` profile is not active, the mapping is not registered (`404` unless another handler exists).

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
| POST | `/api/v1/auth/extension-token` | Bearer (web frontend JWT only) | Yes | Per-user |
| POST | `/api/v1/auth/verify-email` | Public | Yes | Yes |
| POST | `/api/v1/auth/resend-otp` | Public | Yes | Yes + mail cooldown |
| POST | `/api/v1/auth/forgot-password` | Public | Yes | Yes + mail cooldown |
| POST | `/api/v1/auth/reset-password` | Public (token in body) | No | No |
| POST | `/api/v1/auth/refresh-token` | Public (UUID in body) | Yes | No |
| GET | `/api/v1/auth/me` | Bearer | No | No |
| POST | `/api/v1/auth/logout` | Bearer | No | No |
| POST | `/api/v1/auth/logout-all` | Bearer | No | No |
| GET | `/api/v1/test` | Bearer, **`dev` profile only** | No | No |

Missing/invalid JWT on protected routes: `401` `"Unauthorized."` (filter entry point), not the login message. Browser-extension JWT on `/me`, `/logout`, `/logout-all`, `/extension-token`, `/api/v1/test`, and other non-job-extraction routes: `403` `"This client is not authorized to access this resource."`

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
    "refreshToken": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
  },
  "timestamp": "2026-01-01T12:00:00"
}
```

**Errors:** `400` validation; `401` `"Invalid email or password."` for unknown email, wrong password, unverified, disabled, or lockout; `429` rate limit.

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

**Errors:** `401` `"Unauthorized."`; `403` `"This client is not authorized to access this resource."` if the caller is already an extension client; `403` `"Browser extension access is disabled."` if `app.extension.enabled=false`; `429` rate limit.

**Authorization of the minted token:** `SecurityConfig` allows it on `/api/v1/job-extraction/**` and `/api/v1/automated-job-extraction/**` (any HTTP method). Other APIs return `403` `"This client is not authorized to access this resource."` See [BROWSER-EXTENSION.md](AUTH-SERVICE-SPECIFIC-DOCS/BROWSER-EXTENSION.md).

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
    "role": "USER"
  },
  "timestamp": "2026-01-01T12:00:00"
}
```

Password, `enabled`, `emailVerified`, and `tokenVersion` are not returned.

**Errors:** `401` `"Unauthorized."` if the JWT is missing/invalid or the user is disabled/unverified. A browser-extension JWT is `403` `"This client is not authorized to access this resource."` before the controller. If a principal is present but not `CustomUserDetails`, `CurrentUserService` throws `401` `"User is not authenticated."`

---

## POST `/api/v1/auth/refresh-token`

**Auth:** public. The refresh UUID is the secret.

**Body (`RefreshTokenRequest`):** `refreshToken` required, max 128.

**Success:** `200` `"Token refreshed successfully."` with a new `AuthResponse` (new access JWT and **new** refresh UUID). The previous UUID is revoked and chained via `replacedByToken`.

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

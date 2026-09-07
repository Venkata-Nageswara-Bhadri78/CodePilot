# Auth Validation

Validation in auth is four layers: HTTP DTO constraints, extra service normalization/checks, security/token checks, and rate/abuse limits. Only rules that exist in code are listed.

## Input validation (Bean Validation)

`AuthController` methods take `@Valid` request bodies. Failures become `400` with `field: message` (see [ERROR-HANDLING.md](ERROR-HANDLING.md)).

### `@ValidPassword`

Composite constraint on register `password` and reset `newPassword`:

- `@NotBlank`
- `@Size(min = 8, max = 72)` (72 matches BCrypt’s usable length)
- Pattern: at least one lowercase, uppercase, digit, and character from `@$!%*?&#^+=`
- Default message: `"Password must contain uppercase, lowercase, number and special character."`

Login `password` is only `@NotBlank` and `@Size(max = 72)` so a stored password that would fail the complexity regex can still be submitted.

### DTO fields

| DTO | Field | Constraints |
| --- | --- | --- |
| `RegisterRequest` | `username` | `@NotBlank` `@Size(3, 50)` |
| | `fullName` | `@NotBlank` `@Size(3, 100)` |
| | `email` | `@NotBlank` `@Email` `@Size(max = 255)` |
| | `password` | `@ValidPassword` |
| `LoginRequest` | `email` | `@NotBlank` `@Email` `@Size(max = 255)` |
| | `password` | `@NotBlank` `@Size(max = 72)` |
| `VerifyOtpRequest` | `email` | same email rules |
| | `otp` | `@NotBlank` `@Pattern("^\\d{6}$")` |
| `ResendOtpRequest` / `ForgotPasswordRequest` | `email` | same email rules |
| `ResetPasswordRequest` | `token` | `@NotBlank` `@Size(max = 128)` |
| | `newPassword` | `@ValidPassword` |
| `RefreshTokenRequest` / `LogoutRequest` | `refreshToken` | `@NotBlank` `@Size(max = 128)` |

There are no query/path variables on auth routes.

Malformed JSON is rejected before field validation (`400` `"Request body is missing or malformed JSON."`).

## Normalization (service)

Before lookups, `AuthServiceImpl`:

- **Email:** `trim` + `toLowerCase(Locale.ROOT)`
- **Username:** `trim` + `toLowerCase(Locale.ROOT)`
- **Full name:** `trim`; null stays null (DTO already requires non-blank on register)

Uniqueness and login therefore treat `John@Example.com` as `john@example.com`.

If username is null or not in `[3, 50]` after normalize, the service throws `IllegalArgumentException` `"username: size must be between 3 and 50"` (`400`). The DTO usually already enforces this.

## Business validation

| Rule | Where | Failure |
| --- | --- | --- |
| Username/email must be unused to **create** a row | `register` | No exception; method returns; controller still 201 |
| Unique constraint race | `register` save | Caught; same silent return |
| Latest OTP must exist | `verifyOtp` | `InvalidOtpException` `"OTP not found."` |
| OTP not already verified | | `"OTP already used."` |
| OTP not expired | `expiresAt` vs UTC now | `OtpExpiredException` |
| Failed attempts &lt; `maxOtpAttempts` | default 5 | `"OTP not found."` |
| HMAC match | `CredentialDigests.hmacMatches` | `"Invalid OTP."` + increment attempts |
| Resend only if user exists and not verified | `resendOtp` | Silent (still 200) |
| Mail cooldown | `tryAcquireMail` | Silent skip |
| Reset token exists / unused / not expired | `resetPassword` | dedicated 400 exceptions |
| Refresh exists, not revoked, not expired | `refreshToken` | 401 exceptions |
| User still enabled + verified on refresh | | `InvalidRefreshTokenException` |
| Logout refresh belongs to current user and is active | `logout` | `InvalidRefreshTokenException` |
| Active refresh cap | `maxActiveRefreshTokens` default 5 | Oldest extra tokens revoked; login still succeeds |

Existence of an email on forgot/resend is **not** revealed to the client.

## Security / credential validation

| Check | Implementation |
| --- | --- |
| Password at login | `PasswordEncoder.matches`; dummy hash if user missing or lockout active |
| Account usable | `emailVerified == true` and `enabled == true` or same login 401 |
| Access JWT | Signature, expiry, `sub` matches user id, `tv` matches `tokenVersion` |
| JWT user state | Filter: user must exist, enabled, verified |
| Refresh / reset possession | SHA-256 lookup of the raw UUID |
| OTP possession | HMAC-SHA256 with `app.jwt.secret` |
| JWT secret quality | Length ≥ 32; not a known placeholder; prod requires env `APP_JWT_SECRET` |
| Mail sender config | Non-blank `app.mail.from` and `app.mail.sender-name` at startup |

Constant-time compare: `MessageDigest.isEqual` for HMAC and SHA-256 helpers.

## Rate limits and lockout

These are validation-adjacent controls, not Bean Validation:

- Per-IP filter on selected POSTs
- Per-email `consumeOrThrow` on register, login, verify, resend, forgot
- Failed-login window (`maxFailedLogins` / `failedLoginWindowMinutes`)
- Mail cooldown seconds

They produce `429` (rate limit) or the generic login `401` (lockout). Details: [RATE-LIMITING.md](AUTH-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

## What is not validated

- `Role` is not accepted from the client; register always sets `USER`.
- Refresh/reset tokens are not checked for UUID format, only non-blank length ≤ 128 then hashed lookup.
- `fullName` has no extra character-class rules beyond size.
- There is no password-history or “new password ≠ old password” check on reset.

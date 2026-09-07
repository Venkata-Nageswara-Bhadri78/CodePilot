# Auth Testing

Tests live under `src/test/java/com/developer/copilot/auth/`. They are mostly unit and slice tests (Mockito, `MockMvc`, `WebMvcTest`). They do not claim a coverage percentage; none is measured in this documentation.

Use this list to see what is already locked by tests and where to add new ones.

## Layout

```text
src/test/java/com/developer/copilot/auth/
├── controller/     AuthControllerTest, AuthSecurityTest, AuthProductionSecurityTest
├── service/impl/   AuthServiceImplTest, EmailServiceImplTest
├── jwt/            JwtServiceTest, JwtAuthenticationFilterTest
├── ratelimit/      AuthRateLimitFilterTest, AuthRateLimitServiceImplTest
├── redis/          AuthRedisKeyBuilderTest, AuthRedisServiceImplTest
├── config/         AuthSecretsGuardTest, CorsPropertiesTest
├── mapper/         AuthMapperTest
└── util/           CredentialDigestsTest, OtpGeneratorTest
```

Related: `src/test/java/com/developer/copilot/common/exception/GlobalExceptionHandlerTest` includes mappings for auth exceptions (including `ResourceAlreadyExistsException` → 409). That class is not under the auth test tree.

## Controller and HTTP contract

**`AuthControllerTest`** — standalone `MockMvc` + `GlobalExceptionHandler`, `AuthService` mocked.

Covers: register `201`; invalid email / short password / password longer than 72 / email longer than 255 → `400`; login missing password, oversized password/email, malformed JSON message; login `200` with token fields; `GET /me` body; OTP not 6 digits; forgot-password generic success message; blank and oversized refresh token → `400`.

Does **not** run `SecurityConfig` or rate-limit filters.

**`AuthSecurityTest`** — `@WebMvcTest(AuthController)` importing `SecurityConfig`, `SecurityBeansConfig`, `JsonAuthenticationEntryPoint`, `AuthRateLimitConfig`.

Covers: `/me` and `/logout-all` / `/logout` without auth → `401`; `/me` with `@WithMockUser` → `200`; public register and forgot-password are not `401`.

**`AuthProductionSecurityTest`** — same slice, `@ActiveProfiles("prod")`.

Covers: `GET /v3/api-docs` is not HTTP 200 (Swagger not publicly documented on production profile).

## Security components

**`JwtServiceTest`** — generate token contains user id and email; valid for matching user; invalid for other user or bumped `tokenVersion`; expired token; short/placeholder secret rejected at `validateConfiguration`; missing `tv` treated as 0; `alg=none` token rejected.

**`JwtAuthenticationFilterTest`** — valid Bearer sets `CustomUserDetails`; disabled or unverified user leaves context empty; no header / malformed JWT continues without auth; `DataAccessException` on user load propagates.

**`AuthSecretsGuardTest`** — blank/null env secret rejected; non-blank accepted.

**`CorsPropertiesTest`** — `*` dropped from allowed origins; only-wildcard list becomes empty.

## Service / business behavior

**`AuthServiceImplTest`** — in-memory `AuthRateLimitServiceImpl` (no Redis). Covers:

- Register saves OTP and sends mail; existing email or username does not save; username/email normalized to lowercase.
- Verify OTP success, invalid, expired, already used.
- Resend sends for unverified; skips verified.
- Login success tokens; wrong password; unverified; disabled; unknown email same message; unverified/disabled same message as bad password.
- Forgot saves token and mails; reset success bumps `tokenVersion` and revokes refresh; expired/used reset tokens.
- Refresh rotates; revoked; expired; disabled user.
- Logout success / invalid / other user’s token; logout-all revokes all and bumps version.
- OTP storage is HMAC, not plain SHA-256.
- `me` mapping.

**`EmailServiceImplTest`** — OTP and reset templates receive configured expiry minutes; blank `from` fails `validateMailProperties`.

## Rate limiting and Redis

**`AuthRateLimitFilterTest`** — login and refresh-token limited per IP; `/reset-password` not limited by the filter.

**`AuthRateLimitServiceImplTest`** — in-memory block after limit; `consumeOrThrow`; mail cooldown; login failure window clears on success; Redis increment path; Redis exception falls back to memory.

**`AuthRedisKeyBuilderTest`** — colon sanitizing (IPv6); blank identity → `unknown`.

**`AuthRedisServiceImplTest`** — increment/tryAcquire/delete use namespaced keys.

## Utilities and mapper

**`CredentialDigestsTest`** — SHA-256 length/stability; matches; HMAC ≠ SHA-256; wrong secret fails.

**`OtpGeneratorTest`** — 20 samples match `\\d{6}`.

**`AuthMapperTest`** — public fields copied (password is simply not a `UserResponse` field).

## What is not covered in the auth test tree

No dedicated tests for `AuthTokenCleanupJob`, `AuthOpenApiConfig`, `TestController`, pessimistic-lock SQL, or a full SMTP/MySQL/Redis integration suite. Adding a feature should follow the nearest existing test:

| New work | Add tests next to |
| --- | --- |
| New `/api/v1/auth` route | `AuthControllerTest` + `AuthSecurityTest` (public vs authenticated) |
| Login / token / OTP rule | `AuthServiceImplTest` |
| JWT claim or validation | `JwtServiceTest` / filter test |
| New rate-limit path | filter test + properties default |
| Redis key shape | `AuthRedisKeyBuilderTest` |
| Production-only guard | `AuthSecretsGuardTest` or a profile slice test |

Keep anti-enumeration tests: same status/message for duplicate register, unknown login, and generic forgot/resend bodies.

# Endpoints

Common does **not** declare `@RestController` classes. There is no `/api/v1/common/**` resource and no “Common” OpenAPI tag (`SwaggerConfig` states that explicitly).

What common *does* expose on the HTTP surface:

1. **Filter behavior** on the internal path prefix (default `/api/v1/internal/**`).
2. **Global error bodies** for every controller in the app (documented in [ERROR-HANDLING.md](ERROR-HANDLING.md)).
3. **OpenAPI / Swagger UI** on non-production profiles.

Business routes under `/api/v1/internal/resumes/**` belong to the `user` package. They inherit the hallway documented below.

## Uniform success and error envelope

Every JSON body common writes (filters and `GlobalExceptionHandler`) matches:

```json
{
  "success": false,
  "message": "A human-readable description of what went wrong.",
  "data": null,
  "timestamp": "2026-01-01T12:00:00"
}
```

`timestamp` is server `LocalDateTime` without an offset. Do not assume UTC unless the host clock is UTC.

Successful feature endpoints use the same fields with `success: true` and optional `data`. That wrapping is done by feature controllers, not by a common interceptor.

---

## Internal prefix — authentication filter

Registered by `InternalApiSecurityConfig` on `{pathPrefix}` and `{pathPrefix}/*` only.

| | |
|---|---|
| **Applies to** | `GET`/`POST`/any method under `internal.api.path-prefix` (default `/api/v1/internal`) |
| **Purpose** | Require the shared service secret after JWT validation |
| **Authentication** | JWT required by `auth` `SecurityFilterChain` (`anyRequest().authenticated()`). Then `X-Internal-Api-Key` (configurable) must match `internal.api.key` or `internal.api.previous-key`. |
| **Required headers** | `Authorization: Bearer <access JWT>` (from auth). `X-Internal-Api-Key: <secret>` when the check is active. |
| **Request body** | None at this layer |
| **Side effects** | Micrometer `copilot.internal.auth.failure` on reject. No database writes. |

### When the key check is skipped

Only if `internal.api.enabled=false` **and** an active profile is `local` or `dev` (case-insensitive). Any other profile still rejects.

### Success

The filter does not produce a body. The request continues to the hallway rate limiter, then to a feature controller.

### Error — 401 Unauthorized

Returned for: disabled outside laptop, unconfigured blank key, missing header, or non-matching secret. Client message is always:

```json
{
  "success": false,
  "message": "Invalid or missing internal service key.",
  "data": null,
  "timestamp": "2026-01-01T12:00:00"
}
```

The body does not distinguish those cases and never includes the supplied key.

If the JWT is missing, the **auth** entry point typically responds first with `"Unauthorized."` (401), before this filter runs.

---

## Internal prefix — hallway rate limit

Registered by `CommonRateLimitConfig`, order after the key filter. Same URL patterns.

| | |
|---|---|
| **Purpose** | Cap all internal traffic so a new internal controller inherits a limit without copying a feature filter |
| **Limits** | `app.common.internal-key-per-minute` (default **60**) for identity `"service"`; `app.common.internal-user-per-minute` (default **30**) per JWT user id |
| **Window** | 60 seconds |
| **Disabled** | Set a limit to `0` to skip that bucket |

### Error — 429 Too Many Requests

```json
{
  "success": false,
  "message": "Too many requests. Please try again later.",
  "data": null,
  "timestamp": "2026-01-01T12:00:00"
}
```

Header: `Retry-After: <seconds>`. This path does **not** go through `GlobalExceptionHandler`. If a service later throws `common.ratelimit.exception.RateLimitExceededException`, the handler maps it the same way (429 + `Retry-After`).

---

## OpenAPI and Swagger UI (non-production)

`SwaggerConfig` is `@Profile("!prod & !production")`. `SecurityConfig` (auth) permits the Swagger matchers only when the profile is not `prod`/`production`.

Configured paths (from `application.properties.example`):

| Method | Path | Purpose |
|---|---|---|
| GET | `/swagger-ui.html` and `/swagger-ui/**` | Swagger UI |
| GET | `/v3/api-docs` and `/v3/api-docs/**` | OpenAPI JSON |

These are **documentation surfaces**, not production APIs. If they are reachable on a public production host, the Spring profile is wrong.

Security schemes registered in the shared OpenAPI document:

- **Bearer Authentication** — HTTP bearer JWT; global requirement except public auth paths listed below.
- **InternalApiKey** — API key in header `X-Internal-Api-Key`. Documented for internal operations; **not** applied as a global security requirement.

Public auth paths that the customizer clears Bearer from (they are `permitAll` in `SecurityConfig`):

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/resend-otp`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `POST /api/v1/auth/refresh-token`

Servers listed in the OpenAPI info: `http://localhost:8080` (Local) and `https://api.yourdomain.com` (docs-only production host label).

---

## Related endpoints not owned by common

Document these in the `user` service. Common only intercepts the prefix.

| Method | Path | Owner |
|---|---|---|
| GET | `/api/v1/internal/resumes/parsed` | `user` `InternalResumeController` |
| GET | `/api/v1/internal/resumes/{resumeId}/parsed` | `user` `InternalResumeController` |

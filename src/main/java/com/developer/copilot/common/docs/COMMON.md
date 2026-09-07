# Common

`common` is the shared kernel of the Copilot Spring Boot application. It is **not** a separately deployed microservice and it does **not** own user-facing REST controllers. Other packages (`auth`, `user`, `jobs`, `jobextraction`, `ai`, `chatassistant`) depend on it for cross-cutting behavior that must stay consistent across the app.

A new developer should treat this package as the place where API shape, global errors, internal service-to-service access, object storage, and job-URL canonicalization are defined once.

## Purpose

Keep every HTTP JSON response, every unexpected failure, and every internal service call on the same contract, while also owning the infrastructure that several features share (MinIO file storage, optional Redis counters, URL hashing).

## Responsibilities

- Wrap every JSON success and error in the same `ApiResponse` envelope (`success`, `message`, `data`, `timestamp`).
- Translate exceptions from all feature packages into HTTP status codes and client-safe messages (`GlobalExceptionHandler`).
- Require a shared service secret on `/api/v1/internal/**` after the JWT has already been validated, and fail closed outside laptop profiles.
- Rate-limit that internal prefix per calling-service bucket and per authenticated user.
- Store and retrieve PDF objects in MinIO (or S3-compatible storage) with path-traversal and ownership checks.
- Canonicalize job-posting URLs and produce SHA-256 hashes for duplicate detection.
- Resolve the authenticated `User` from the Spring Security context (`CurrentUserService`).
- Register OpenAPI/Swagger for non-production profiles.
- Enable JPA auditing so `@CreatedDate` / `@LastModifiedDate` work for entities in other packages.

## What this package does not do

- It does not expose business REST APIs. Controllers under `/api/v1/internal/**` live in other packages (today, parsed-resume reads live in `user`).
- It does not issue or validate JWTs. That belongs to `auth`. Common only reads the already-authenticated principal.
- It does not own MySQL tables.
- It does not implement feature-specific rate limiters (auth login, jobs, AI, chat). Those stay in their own packages; common only maps their exceptions to HTTP 429.

## Major capabilities

| Capability | What a caller gets |
|---|---|
| Uniform JSON envelope | `ApiResponse<T>` on successes and errors |
| Global error mapping | Feature exceptions become 4xx/5xx without leaking SQL, SMTP, or MinIO details |
| Internal API hallway | JWT plus `X-Internal-Api-Key`, then a hallway rate limit |
| Object storage | PDF upload / download / delete / exists against a configured bucket |
| Job URL identity | Strict or lenient normalization plus SHA-256 hex |
| Current user | `User` entity from `CustomUserDetails`, or 401 `"User is not authenticated."` |
| Optional Redis | Distributed counters for the internal hallway when `app.common.redis.enabled=true` |

## Main components

- **HTTP contract:** `ApiResponse`, `GlobalExceptionHandler`, `SwaggerConfig`
- **Identity helper:** `CurrentUserService` / `CurrentUserServiceImpl`
- **Internal access:** `InternalApiKeyFilter`, `InternalApiStartupValidator`, `InternalApiRateLimitFilter`
- **Storage:** `FileStorageService` / `FileStorageServiceImpl`, `StorageStartupValidator`, MinIO client
- **Redis (optional):** `CommonRedisService`, `CommonRedisKeyBuilder`, `CommonRedisRepository`
- **Shared utilities:** `UrlNormalizationUtil`, `ChecksumUtil`, `CopilotMetrics`

## Integrations

- **auth:** JWT principal (`CustomUserDetails`), `User` entity, auth exceptions, CORS/security chain that runs *before* the internal-key filter.
- **user:** resume file I/O via `FileStorageService`; `ResumeProperties` / `UserProfileProperties` registered from `JpaConfig`; internal resume controllers sit behind the common hallway.
- **jobs / jobextraction:** `UrlNormalizationUtil` and `InvalidJobUrlException`.
- **ai / chatassistant / jobs / user / jobextraction:** exception types mapped in `GlobalExceptionHandler`.
- **MinIO / S3-compatible object store:** actual bytes for resumes.
- **Redis:** optional, only for common internal rate-limit counters.

## Important flows

1. A public or authenticated API call hits a feature controller. Validation or business code throws. `GlobalExceptionHandler` returns `ApiResponse` with `success: false`.
2. A service-to-service call hits `/api/v1/internal/**`. Spring Security validates the JWT, then common checks the internal key, then common applies hallway rate limits, then the feature controller runs.
3. A user uploads a resume PDF. `user` builds a folder under `users/{id}/resumes` and calls `FileStorageService.upload`. Common validates PDF magic bytes, path safety, and (when a JWT user is on the thread) ownership.
4. Job extraction or job create passes a posting URL through `UrlNormalizationUtil.normalizeStrict`, then `sha256Hex`, for stable duplicate detection.

## Security responsibilities

Common enforces the **second** authentication factor for internal URLs (shared secret), refuses to boot with a disabled or weak key outside `local`/`dev`, hides infrastructure details in error bodies, and refuses storage paths that escape the authenticated user's prefix. JWT issuance, password hashing, and public auth endpoints remain in `auth`.

## Infrastructure

| System | Role in common |
|---|---|
| MySQL / JPA | No common-owned tables. `JpaConfig` turns on auditing for the rest of the app. |
| Redis | Optional counters for `/api/v1/internal/**` rate limits. Off by default; in-memory fallback when Redis is missing or fails. |
| MinIO | Object storage for PDFs. Bucket is checked (and optionally created) at startup. |
| Micrometer | Best-effort counters (`CopilotMetrics`). Failures never break a request. There is no Actuator dependency in this project. |

## Documentation map

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, components, dependency direction |
| [FLOW.md](FLOW.md) | Request, security, storage, URL, and error workflows |
| [ENDPOINTS.md](ENDPOINTS.md) | Why common has no REST API, plus filter and Swagger surfaces |
| [SECURITY.md](SECURITY.md) | Internal key, profiles, storage ownership, secrets |
| [DATABASE.md](DATABASE.md) | Auditing only; no common entities |
| [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md) | Optional Redis counters and in-memory fallback |
| [ERROR-HANDLING.md](ERROR-HANDLING.md) | Exception → HTTP mapping and `ApiResponse` |
| [VALIDATION.md](VALIDATION.md) | Input, business, and security validation |
| [CONFIGURATION.md](CONFIGURATION.md) | Properties that common actually reads |
| [TESTING.md](TESTING.md) | Test layout and what is covered |
| [DEPENDENCIES.md](DEPENDENCIES.md) | Why common needs each major library |
| [COMMON-SERVICE-SPECIFIC-DOCS/INTERNAL-API.md](COMMON-SERVICE-SPECIFIC-DOCS/INTERNAL-API.md) | Internal key + hallway rate limit |
| [COMMON-SERVICE-SPECIFIC-DOCS/FILE-STORAGE.md](COMMON-SERVICE-SPECIFIC-DOCS/FILE-STORAGE.md) | MinIO PDF storage |
| [COMMON-SERVICE-SPECIFIC-DOCS/URL-NORMALIZATION.md](COMMON-SERVICE-SPECIFIC-DOCS/URL-NORMALIZATION.md) | Canonical job URLs and hashes |

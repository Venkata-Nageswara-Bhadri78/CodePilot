# Testing architecture

Tests live under `src/test/java/com/developer/copilot`, mirroring production packages. They are **JUnit tests** using Spring Boot test starters (webmvc, security, validation, data-jpa) from `pom.xml`. This document does not claim coverage percentages.

There is no separate QA/e2e suite in this repository. Automated extraction has a small `integration` package that still runs as unit/slice-style tests (for example `ManualJobExtractionGatewayTest`), not a live HTTP environment matrix.

## How tests are organized

```text
src/test/java/com/developer/copilot/
  auth/           controllers, JWT, rate limit, redis, secrets, mappers
  user/           profile, resumes, parse, internal API, security
  jobs/           CRUD, ownership, rate limit, redis, security
  jobextraction/  manual + automated (fetch, SSRF, pipeline, cache)
  ai/             controller, ChatClient mocks, guard, rate limit, DTOs
  chatassistant/  service, controller, sanitizer, security
  common/         ApiResponse, GlobalExceptionHandler, storage, internal key, URL util
  SwaggerApiDocsTest.java   OpenAPI wiring (non-prod)
```

Naming patterns that show up repeatedly:

| Suffix / name | Intent |
| --- | --- |
| `*ControllerTest` | HTTP contract: status, envelope, validation, happy path with mocked services |
| `*SecurityTest` | JWT required, 401/403, extension vs web |
| `*ProductionSecurityTest` | `prod`/`production` profile: Swagger closed, secrets guard behavior |
| `*ServiceImplTest` | Business rules without a full server |
| `*RateLimit*Test` | Filter + service counters, Redis vs memory |
| `*Redis*Test` | Key builder / repository / fallback |
| `*ExceptionMappingTest` | Exception → HTTP status via `GlobalExceptionHandler` |
| `*MapperTest` | DTO mapping |

## What important behavior is tested

### Security and identity

- Public vs protected routes; JSON 401 `"Unauthorized."`
- Extension JWT blocked off extraction paths
- JWT validity including `tokenVersion`, enabled, emailVerified
- Production: Swagger not public; `APP_JWT_SECRET` required
- Internal key filter: missing/wrong key, laptop-only disable, fail-closed elsewhere
- Auth register enumeration, login generic 401, refresh rotation/reuse (service tests)

### HTTP / controllers

Each user-facing module has controller tests for the catalog in [ENDPOINTS.md](ENDPOINTS.md): auth, user (profile + resumes + internal parse), jobs (including field PATCH), both extraction `/parse` endpoints, AI chat/stream/health, chat-assistant send/history/list/delete.

### Persistence and ownership

- Job uniqueness on canonical URL hash; foreign job id looks like 404 (`JobOwnershipIsolationTest` and jobs controller tests)
- Profile/resume ownership and duplicate checksums
- Chat one-session-per-job and conflict on concurrent create

### Redis and rate limits

- In-memory path when Redis is null/disabled
- Redis INCR/TTL when a fake/stub redis service is injected
- Filter returns 429 and `Retry-After`

### Resilience

- `AiChatGuard`, `JobExtractionAiGuard`, `JobPageFetchGuard`: circuit open and bulkhead full → unavailable exceptions / 503 mapping

### Extraction / SSRF / fetch

- `SsrfProtectionService` blocks private and metadata hosts
- Pipeline strategies (ATS HTML/JSON), noise stripper, Workday CXS URL helpers
- Preview/content cache TTL and identity (user + URL hash)

### Storage and parsing

- PDF validation, checksum, path safety (`FileStorageServiceImplTest`)
- Resume parse writer/extractor/section parser; pending vs completed vs failed

### Shared kernel

- `ApiResponse` shape
- `GlobalExceptionHandler` (including AI and user mappings)
- `UrlNormalizationUtil`
- `CurrentUserService` unauthenticated → 401-style failure
- `SwaggerConfig` / `SwaggerApiDocsTest`

## Where to add tests

| You are changing… | Add or extend… |
| --- | --- |
| A REST route or status code | `*ControllerTest` + `*SecurityTest` |
| Production Swagger/secret rules | `*ProductionSecurityTest` / `AuthSecretsGuardTest` |
| Auth token or OTP rules | `AuthServiceImplTest`, `JwtServiceTest` |
| Job URL / uniqueness | jobs service + controller + `UrlNormalizationUtilTest` |
| Rate limit numbers or buckets | that module’s `*RateLimitServiceImplTest` and filter test |
| Redis key shape | `*RedisKeyBuilderTest` |
| New exception type | mapping test + `GlobalExceptionHandler` |
| AI prompt or provider mapping | `AiServiceImplTest` (mock `ChatClient`) |
| Fetch/SSRF | `SsrfProtectionServiceTest`, fetch/pipeline tests |
| Resume parse | `user/service/parsing/*` and `ResumeParsingServiceImplTest` |

Keep tests from needing a real Redis, SMTP, MinIO, or LLM: those dependencies are mocked or replaced. Do not start documenting hypothetical coverage.

Module testing notes: `*/docs/TESTING.md` under each feature package.

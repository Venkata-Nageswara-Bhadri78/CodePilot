# Testing

How `jobextraction` is tested today and where to add coverage. Tests live under `src/test/java/com/developer/copilot/jobextraction/`. This document does not claim a coverage percentage.

## Layout

| Area | Class | Style |
| --- | --- | --- |
| Controller + `GlobalExceptionHandler` | `JobExtractionControllerTest` | Standalone `MockMvc`, service mocked |
| Security filter chain | `JobExtractionSecurityTest` | `@WebMvcTest` + `SecurityConfig` |
| Production swagger | `JobExtractionProductionSecurityTest` | `@WebMvcTest`, `@ActiveProfiles("prod")` |
| Service orchestration | `JobExtractionServiceImplTest` | Mockito, real util/mapper/cache/guard/metrics |
| Mapper | `JobExtractionMapperTest` | Pure unit |
| Preview cache | `JobExtractionPreviewCacheTest` | Memory + mocked Redis |
| AI guard | `JobExtractionAiGuardTest` | Pure unit |
| Rate-limit filter | `JobExtractionRateLimitFilterTest` | Mock request/response + in-memory limiter |
| Rate-limit service | `JobExtractionRateLimitServiceImplTest` | Memory + mocked Redis |
| Redis service | `JobExtractionRedisServiceImplTest` | Mocked repository |
| Redis keys | `JobExtractionRedisKeyBuilderTest` | Pure unit |
| Exception HTTP mapping | `JobExtractionExceptionMappingTest` | Direct handler calls |

There is no `@SpringBootTest` slice that boots Redis or a live model for this package.

## What important behaviors are covered

### HTTP and validation (`JobExtractionControllerTest`)

- 200 maps `data` and `success`
- Missing/blank `sourceUrl` or `rawJobText` → 400
- URL longer than 2000, text longer than 50000 → 400
- Text of exactly 50000 → 200 (service stubbed)
- Malformed JSON and empty body → 400
- Unknown JSON fields ignored → 200
- `InvalidJobUrlException` → 400 without echoing `<script>`
- Duplicate → 409
- `InvalidCredentialsException` → 401
- `AiServiceException` → 502
- `EmailNotVerifiedException` → 403
- `JobExtractionAiUnavailableException` → 503
- Unexpected `RuntimeException` → 500 `Something went wrong.`

### Security (`JobExtractionSecurityTest`)

- No `Authorization` → 401, service never called
- Garbage Bearer / `JwtException` → 401
- `emailVerified=false` → 401 (filter), service never called
- `enabled=false` → 401, service never called

### Production (`JobExtractionProductionSecurityTest`)

`GET /v3/api-docs/job-extraction`, `/v3/api-docs`, `/swagger-ui/index.html` are **not** HTTP 200 on profile `prod`.

### Service (`JobExtractionServiceImplTest`)

- Happy path: tracking params stripped, canonical URL sent to AI and returned, paste echoed as `originalDescription`
- Hash passed to `existsByUserIdAndSourceUrlHash` matches `sha256Hex(normalized)`
- Null AI skills → empty list
- Blank title/company → `requiresManualReview`
- Invalid URL, `javascript:`/`data:`/`ftp:`/missing host → exception, AI not called; `javascript:` not in message
- Duplicate before AI; product 409 message
- Unauthenticated / unverified / null `emailVerified`
- Localhost HTTP allowed
- Default HTTPS port and trailing slash canonicalized
- Query param order → same hash; unknown params kept; `access_token`/`token`/`auth` stripped
- Other users' jobs do not 409 this user; check uses current user id
- Concurrent same user+URL → one AI call
- Oversized AI title clipped to 255 + manual review
- Script in AI title kept; `sourceUrl` still canonical
- Second parse uses cache; cache is per user
- Three AI failures → fourth `JobExtractionAiUnavailableException` without a fourth provider call

### Mapper

Field mapping, clip lengths, skill cap, control-character strip, defensive copy of skills list, HTML kept, `javascript:`/`data:` URI fields blanked, `originalDescription` not blanked for `javascript:` in the paste.

### Cache

Per-user memory isolation, Redis JSON get/put, Redis failure → memory, `computeIfAbsent` loader once under concurrency.

### Guard

Three failures open the circuit; a success resets the failure count so three failures are required again.

### Rate limiting

POST limited per IP; per user across IPs; other paths and GET not limited; Redis INCR path; Redis throw → memory; `limit <= 0` always allows; `consumeOrThrow`.

### Redis helpers

Namespaced keys; colon sanitization for IPv6; blank identity → `unknown`.

### Exception mapping

403 / 503 / 429+`Retry-After` for the three job-extraction-specific handler methods.

## How to add tests for new behavior

1. **New request field or status mapping** — extend `JobExtractionControllerTest` with `GlobalExceptionHandler` already installed.
2. **New security rule** — `JobExtractionSecurityTest` (or production profile test if swagger/docs related).
3. **Orchestration (order of checks, cache, AI)** — `JobExtractionServiceImplTest`. Prefer real `UrlNormalizationUtil` / `JobExtractionMapper` like the existing tests.
4. **Clipping / sanitization** — `JobExtractionMapperTest` (no Spring).
5. **Redis key or TTL changes** — key builder + redis service tests; cache test for JSON identity `userId_urlHash`.
6. **Rate-limit identity or path** — filter test with `MockHttpServletRequest`.

Keep using the same generic client messages in assertions (`Unauthorized.`, `Too many requests...`, duplicate copy, URL copy) so docs and tests stay aligned.

## Out of scope in current tests

- Live Gemini/Groq or Testcontainers Redis
- Full `POST /api/v1/jobs` round-trip after parse (belongs to jobs tests)
- Metrics log format
- Lettuce connection factory integration

# Testing

How `automatedjobextraction` is tested today and where to add coverage. Tests live under `src/test/java/com/developer/copilot/jobextraction/automatedjobextraction/`. This document does not claim a coverage percentage.

## Layout

| Area | Class | Style |
| --- | --- | --- |
| Controller + `GlobalExceptionHandler` | `AutomatedJobExtractionControllerTest` | Standalone `MockMvc`, service mocked |
| Security filter chain | `AutomatedJobExtractionSecurityTest` | `@WebMvcTest` + `SecurityConfig` |
| Production swagger | `AutomatedJobExtractionProductionSecurityTest` | `@WebMvcTest`, `@ActiveProfiles("prod")` |
| Service orchestration | `AutomatedJobExtractionServiceImplTest` | Mockito, real URL util, SSRF (stub DNS), in-memory cache |
| Pipeline / merge / formatter | `JobExtractionPipelineTest` | Real strategies, no HTTP |
| ATS strategies | `AtsJobExtractionStrategyTest` | Jsoup HTML fixtures |
| Noise stripper | `HtmlNoiseStripperTest` | Pure unit |
| SSRF | `SsrfProtectionServiceTest` | Stub `HostnameResolver` |
| Fetcher | `JobPageFetcherTest` | Mock `JobPageHttpClient` |
| Fetch guard | `JobPageFetchGuardTest` | Pure unit |
| Workday CXS URL map | `WorkdayCxsUrlsTest` | Pure unit |
| HTTP body cap | `JdkJobPageHttpClientTest` | `readLimited` only (no sockets) |
| Extracted-text cache | `ExtractedJobContentCacheTest` | Memory + mocked Redis |
| Rate-limit filter | `AutomatedJobExtractionRateLimitFilterTest` | Mock request/response + in-memory limiter |
| Rate-limit service | `AutomatedJobExtractionRateLimitServiceImplTest` | Memory + mocked Redis |
| Redis service | `AutomatedJobExtractionRedisServiceImplTest` | Mocked repository |
| Redis keys | `AutomatedJobExtractionRedisKeyBuilderTest` | Pure unit |
| Gateway | `ManualJobExtractionGatewayTest` | Mock `JobExtractionService` |
| Exception HTTP mapping | `AutomatedJobExtractionExceptionMappingTest` | Direct handler calls |
| OpenAPI group | `AutomatedJobExtractionOpenApiConfigTest` | Bean + `@Profile` |

There is no `@SpringBootTest` slice that boots Redis, opens sockets to career sites, or calls a live model for this package. `JobPageHttpClient` and `HostnameResolver` exist so tests never need either.

## What important behaviors are covered

### HTTP and validation (`AutomatedJobExtractionControllerTest`)

- 200 maps `data.title` / `data.company` and `success`
- Missing/blank `sourceUrl` → 400
- URL longer than 2000 → 400
- Malformed JSON and empty body → 400
- Unknown JSON fields ignored → 200
- `InvalidAutomatedJobUrlException` → 400 `INVALID JOB URL` without `http` in the message
- `InvalidJobUrlException` for `javascript:` → 400 without echoing `javascript`
- Duplicate → 409
- `InvalidCredentialsException` → 401
- `EmailNotVerifiedException` → 403
- `AutomatedJobPageFetchException` → 502 with generic fetch message
- `AutomatedJobExtractionUnavailableException` → 503
- `AiServiceException` → 502
- Unexpected `RuntimeException` → 500 `Something went wrong.` without leaked host:port

### Security (`AutomatedJobExtractionSecurityTest`)

- No `Authorization` → 401, service never called
- Garbage Bearer / `JwtException` → 401
- `emailVerified=false` → 401 (filter), service never called
- `enabled=false` → 401, service never called

### Production (`AutomatedJobExtractionProductionSecurityTest`)

`GET /v3/api-docs/automated-job-extraction`, `/v3/api-docs`, `/swagger-ui/index.html` are **not** HTTP 200 on profile `prod`.

### Service (`AutomatedJobExtractionServiceImplTest`)

- Happy path: tracking params stripped, canonical URL sent to fetch/pipeline/gateway
- Second parse uses extracted-text cache (fetch once, gateway twice)
- Unverified email / unauthenticated → no fetch
- `javascript:` → `InvalidJobUrlException`, no fetch
- localhost → `InvalidAutomatedJobUrlException`, no fetch
- Pipeline reject → gateway never called
- Fetch failure propagates, gateway never called
- DNS to `127.0.0.1` and `UnknownHostException` → `InvalidAutomatedJobUrlException`, no fetch

### Pipeline

JSON-LD title/company/location; multiple JobPostings prefer URL match; listing pages rejected; empty body rejected; semantic article keeps body and drops nav/similar jobs; JSON `JobPosting` payload; Workday `jobPostingInfo` JSON; formatter omits missing fields; merger does not overwrite an earlier title.

### Strategies

Greenhouse, Lever, Workday (automation ids and JSON), Indeed, LinkedIn fixtures.

### SSRF

Public hostname allowed (stub DNS to `8.8.8.8`); localhost, loopback IP, RFC1918, metadata IP/host, user-info, `file:`/`ftp:`, IPv6 loopback, `.internal`/`.local`, mixed public+private DNS, unknown host, dotted numeric host, decimal IP, CGNAT.

### Fetch

200 HTML; Workday widget → CXS JSON; 404/401 → invalid job; 502 → fetch failure; public redirect followed (`finalUrl` updated); redirect to localhost rejected; too many redirects rejected; captcha without job markers rejected; client throw → fetch failure.

### Guard

Three fetch failures open the circuit; a success resets the failure count.

### Cache

Per-user memory isolation; loader once; Redis get/put with namespace `extracted` and identity `1_abc`; TTL 3 minutes on put.

### Rate limiting

POST limited per IP; per user across IPs; `/api/v1/job-extraction/parse` and GET not limited by **this** filter; Redis INCR path; Redis throw → memory; `limit <= 0` always allows; `consumeOrThrow`.

### Redis helpers

Namespaced keys; colon sanitization; blank identity → `unknown`; blank prefix → `automatedjobextraction`.

### Gateway

Forwards canonical URL and extracted text as `JobExtractionRequest.sourceUrl` / `rawJobText`.

### Exception mapping

400 / 502 / 503 / 429+`Retry-After` for the automated-extraction handler methods.

## How to add tests for new behavior

1. **New request field or status mapping** — extend `AutomatedJobExtractionControllerTest` with `GlobalExceptionHandler` already installed.
2. **New security rule** — `AutomatedJobExtractionSecurityTest` (or production profile test if swagger/docs related).
3. **Orchestration (order of checks, cache, fetch)** — `AutomatedJobExtractionServiceImplTest`. Prefer real `UrlNormalizationUtil` and a stub `HostnameResolver`.
4. **New site family** — add a `JobExtractionStrategy` test like `AtsJobExtractionStrategyTest`, then a pipeline case if quality/merge behavior matters.
5. **SSRF / redirect** — `SsrfProtectionServiceTest` or `JobPageFetcherTest` with a mock HTTP client. Do not open sockets.
6. **Redis key or TTL changes** — key builder + redis service tests; cache test for identity `userId_urlHash`.
7. **Rate-limit identity or path** — filter test with `MockHttpServletRequest`.

Keep using the same generic client messages in assertions (`Unauthorized.`, `INVALID JOB URL`, `Too many requests...`, duplicate copy, URL format copy) so docs and tests stay aligned.

## Out of scope in current tests

- Live career-site HTTP or Testcontainers Redis
- Live Gemini/Groq
- Full `POST /api/v1/jobs` round-trip after parse (belongs to jobs tests)
- Metrics log format
- Lettuce connection factory integration
- Browser-extension JWT on this controller (covered in auth `BrowserExtensionSecurityTest` with a probe path)

# Dependencies

Why `automatedjobextraction` uses the libraries and internal modules it actually imports. Transitive Maven tree is omitted.

## Internal modules

| Dependency | Why this service needs it |
| --- | --- |
| `common` | `ApiResponse` envelope; `CurrentUserService`; `UrlNormalizationUtil` + `InvalidJobUrlException`; `GlobalExceptionHandler` HTTP mapping. |
| `auth` | `User` on the principal; `CustomUserDetails` in the rate-limit filter; JWT filter and `SecurityConfig` (not reimplemented here). |
| `manualextraction` | `JobExtractionService.extractJobInfo`; `JobExtractionRequest` / `JobExtractionResultResponse`; `EmailNotVerifiedException`; `JobExtractionLimits` via `AutomatedJobExtractionLimits`. Duplicate check, AI call, preview cache, and mapper live there. |

`automatedjobextraction` does not import `jobs` or `ai` types directly. Those are reached only through the gateway.

`chatassistant` and `user` are unused by this package.

## Framework

| Library | Why |
| --- | --- |
| `spring-boot-starter-webmvc` | `AutomatedJobExtractionController`, servlet rate-limit filter, JSON request/response. |
| `spring-boot-starter-security` | Authenticated parse endpoint; `SecurityContextHolder` for user id in the filter. |
| `spring-boot-starter-validation` | `@NotBlank` / `@Size` on `AutomatedJobExtractionRequest`. |
| Spring `@Configuration` / `@ConditionalOnProperty` | HTTP client bean; optional Redis beans; rate-limit filter registration. |

`JobRepository` still needs JPA at runtime because the gateway’s service uses it; this package does not declare extra JPA entities.

## Persistence and Redis

| Library | Why |
| --- | --- |
| `mysql-connector-j` | JDBC for the `jobs` exists-check inside manual extraction (runtime). |
| `spring-boot-starter-data-redis` + Lettuce | Optional `StringRedisTemplate` when `app.automatedjobextraction.redis.enabled=true`. Not used as a jobs-row cache. |

MySQL and Redis are not required to **compile** the extracted-text cache or in-memory limiter; Redis is required at runtime only if that property is true.

## HTML and JSON parsing

| Library | Why |
| --- | --- |
| `org.jsoup:jsoup` (1.18.3) | Parse fetched HTML, strip chrome, CSS selectors in strategies, `Safelist.none()` for JSON-LD HTML descriptions. |
| Jackson (Boot) | JSON-LD / Workday `jobPostingInfo` (`ObjectMapper` inside those strategies); request/response JSON; filter 429 body. |

## Outbound HTTP

| Library | Why |
| --- | --- |
| JDK `java.net.http.HttpClient` | `JdkJobPageHttpClient`. No Apache HttpClient / WebClient in this package. Redirects disabled at the client; `JobPageFetcher` follows them after SSRF. |

## AI integration (via `manualextraction` → `ai`)

| Library | Why |
| --- | --- |
| `spring-ai-starter-model-openai` | Structured `.call().entity(JobExtractionAiResponse.class)` inside `AiServiceImpl`, not imported by most `automatedjobextraction` classes. |

Timeouts use Project Reactor `Mono` inside `AiServiceImpl.callWithTimeout`.

## JSON and API docs

| Library | Why |
| --- | --- |
| `springdoc-openapi-starter-webmvc-ui` | `AutomatedJobExtractionOpenApiConfig` grouped OpenAPI + controller `@Operation` / `@Schema`. Gated off in production profiles. |

## Utilities

| Library | Why |
| --- | --- |
| Lombok | Boilerplate on DTOs, controller, service, properties. |
| JJWT | Not used directly in this package; required for the JWT filter that protects the endpoint. |

## Resilience and metrics

No Resilience4j, Bucket4j, or Micrometer dependency for this module. Fetch circuit/bulkhead is `Semaphore` + `AtomicInteger`. Metrics are `AtomicLong` plus log lines.

## Test scope

`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, Mockito/JUnit (from Boot test starters). Tests mock `JwtService`, `UserRepository`, `JobPageHttpClient`, `JobExtractionService`, and `AutomatedJobExtractionRedisService` as needed. `HostnameResolver` is stubbed so SSRF tests never call real DNS.

## Direction to remember

When adding a feature, prefer:

- `jobs` for anything that **writes** a job
- `ai` / `manualextraction` for prompt, clipping, or preview DTO changes
- this package for URL fetch, SSRF, extraction strategies, extracted-text cache, and this path’s rate limit

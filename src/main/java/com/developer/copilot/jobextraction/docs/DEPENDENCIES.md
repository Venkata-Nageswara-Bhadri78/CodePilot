# Dependencies

Why `jobextraction` uses the libraries and internal modules it actually imports. Transitive Maven tree is omitted.

## Internal modules

| Dependency | Why this service needs it |
| --- | --- |
| `common` | `ApiResponse` envelope; `CurrentUserService`; `UrlNormalizationUtil` + `InvalidJobUrlException`; `GlobalExceptionHandler` HTTP mapping. |
| `auth` | `User` on the principal; `CustomUserDetails` in the rate-limit filter; JWT filter and `SecurityConfig` (not reimplemented here). |
| `jobs` | `JobRepository.existsByUserIdAndSourceUrlHash`; `DuplicateJobException`; `JobLimits.MAX_DESCRIPTION_LENGTH` via `JobExtractionLimits`. |
| `ai` | `AiService.extractJobInfo`; `JobExtractionAiRequest` / `JobExtractionAiResponse`; `AiServiceException`. Prompts and ChatClient stay in `ai`. |

`jobextraction` does not call `chatassistant` or `user` services.

## Framework

| Library | Why |
| --- | --- |
| `spring-boot-starter-webmvc` | `JobExtractionController`, servlet rate-limit filter, JSON request/response. |
| `spring-boot-starter-security` | Authenticated parse endpoint; `SecurityContextHolder` for user id in the filter. |
| `spring-boot-starter-validation` | `@NotBlank` / `@Size` on `JobExtractionRequest`. |
| `spring-boot-starter-data-jpa` | `JobRepository` (jobs module) for the duplicate exists-query. |
| Spring `@Configuration` / `@ConditionalOnProperty` | Optional Redis beans; rate-limit filter registration. |

## Persistence and Redis

| Library | Why |
| --- | --- |
| `mysql-connector-j` | JDBC for the `jobs` exists-check (runtime). |
| `spring-boot-starter-data-redis` + Lettuce | Optional `StringRedisTemplate` when `app.jobextraction.redis.enabled=true`. Not used as a jobs-row cache. |

MySQL and Redis are not required to **compile** the preview cache or in-memory limiter; Redis is required at runtime only if that property is true.

## AI integration (via `ai` module)

| Library | Why |
| --- | --- |
| `spring-ai-starter-model-openai` | Structured `.call().entity(JobExtractionAiResponse.class)` and `OpenAiChatOptions` (temperature 0, max tokens, model). Used inside `AiServiceImpl`, not imported by most `jobextraction` classes. |

Timeouts use Project Reactor `Mono` inside `AiServiceImpl.callWithTimeout` (`spring-boot-starter-webflux` on the classpath for the app).

## JSON and API docs

| Library | Why |
| --- | --- |
| Jackson (Boot) | Request/response JSON; preview cache serialize/deserialize; filter 429 body. |
| `springdoc-openapi-starter-webmvc-ui` | `JobExtractionOpenApiConfig` grouped OpenAPI + controller `@Operation` / `@Schema`. Gated off in production profiles. |

## Utilities

| Library | Why |
| --- | --- |
| Lombok | Boilerplate on DTOs, controller, service, properties. |
| JJWT | Not used directly in this package; required for the JWT filter that protects the endpoint. |

## Resilience and metrics

No Resilience4j, Bucket4j, or Micrometer dependency for this module. Circuit/bulkhead is `Semaphore` + `AtomicInteger`. Metrics are `AtomicLong` plus log lines.

## Test scope

`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, Mockito/JUnit (from Boot test starters). Tests mock `JwtService`, `UserRepository`, `JobRepository`, `AiService`, and `JobExtractionRedisService` as needed.

## Direction to remember

When adding a feature, prefer:

- `jobs` for anything that **writes** a job
- `ai` for prompt or model-option changes
- this package for parse orchestration, cache, rate limit, and preview DTO mapping

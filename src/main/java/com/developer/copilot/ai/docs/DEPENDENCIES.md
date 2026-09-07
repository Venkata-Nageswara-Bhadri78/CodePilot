# Dependencies

This document lists **why** the `ai` service needs each important dependency. It is not a dump of the Maven tree.

## Internal modules

| Dependency | Why AI needs it |
|---|---|
| `auth` (`User`, `UserRepository`, `JwtAuthenticationFilter`, `CustomUserDetails`) | Identify the caller; job chat maps email → user id; JWT gate on every AI URL |
| `common` (`ApiResponse`, `CurrentUserService`, `GlobalExceptionHandler`) | Response envelope, current user, HTTP exception mapping |
| `user` (`ResumeParsingService`, resume exceptions, `ResumeParsedDataResponse`) | Parsed resume `contextText` and parse lifecycle |
| `jobs` (`JobRepository`, `JobEntity`, `JobNotFoundException`) | Owned job description for grounding |
| `jobextraction` | **Caller** of `extractJobInfo` (AI does not depend on that module’s HTTP) |
| `chatassistant` | **Caller** of `continueJobChat` |

AI does not depend on MinIO or PDFBox directly. Resume files are parsed in the user module.

## Framework

| Library | Why |
|---|---|
| Spring WebMVC | `AiController`, filters, `ApiResponse` JSON |
| Spring WebFlux | `Flux` / `Mono` for SSE streaming and timeouts (`Mono.fromCallable(...).timeout`) |
| Spring Context / Boot | `@Configuration`, `@ConfigurationProperties`, `FilterRegistrationBean`, `@ConditionalOnProperty` for Redis |
| Jakarta Validation | `@NotBlank`, `@Size`, `@DecimalMin`/`Max` on DTOs |
| Jackson | Filter `429` body; `JavaTimeModule` for timestamps |

## LLM integration

| Library | Why |
|---|---|
| `spring-ai-starter-model-openai` (BOM `spring-ai-bom` 2.0.0) | `ChatClient`, `OpenAiChatOptions`, streaming `content()`, structured `entity(JobExtractionAiResponse.class)` |
| OpenAI-compatible HTTP | Example config targets Gemini’s OpenAI-compatible URL with `GEMINI_API_KEY` |

Swagger annotations are excluded from the Spring AI starter to avoid clashing with springdoc.

## Security

| Library | Why |
|---|---|
| Spring Security | Authenticated-only AI routes, CORS, stateless sessions |
| JJWT (auth module) | Parse and validate Bearer tokens before AI code runs |

## Persistence and Redis

| Library | Why |
|---|---|
| Spring Data JPA / Hibernate | `JobRepository`, `UserRepository`, `EntityManager.clear()` |
| MySQL connector | Runtime database used by those repositories |
| Spring Data Redis / Lettuce | Optional AI rate-limit counters (`StringRedisTemplate`) |

Boot Data Redis auto-config is **excluded** on `CopilotApplication`. AI creates its own factory when `app.ai.redis.enabled=true`.

## API docs

| Library | Why |
|---|---|
| springdoc OpenAPI | `AiOpenApiConfig` group `ai`, `@Tag` / `@Operation` on `AiController` |

Gated off in `prod` / `production` profiles.

## Observability (in-process)

`AiMetrics` uses `slf4j` + `lombok` `@Slf4j` only. There is no Micrometer/Actuator dependency required for these counters.

## Test scope

| Library | Why |
|---|---|
| JUnit 5, Mockito | Service, filter, prompt, Redis unit tests |
| Spring Security Test / `@WebMvcTest` | `AiSecurityTest` |
| reactor-test (via WebFlux) | Stream `Flux` in service/controller tests |
| Jakarta Validation test factory | `AiRequestValidationTest` |

## What not to add “because AI”

- A second HTTP client besides Spring AI’s `ChatClient` for the same provider.
- Application-wide Redis auto-config just to turn on AI limits — use `app.ai.redis.*`.
- Actuator solely for AI metrics unless you introduce a real meter registry; today metrics are log lines.

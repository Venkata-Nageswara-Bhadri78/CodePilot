# Technology and dependency architecture

Parent: Spring Boot **4.1.0** (`spring-boot-starter-parent`). Language: **Java 17**. Artifact: `com.developer:copilot`.

Direct dependencies come from `pom.xml`. Transitive libraries are omitted unless they are part of how the system is built (JJWT impl, Lettuce via Data Redis, etc.).

## Spring / framework

| Dependency | Why this backend uses it |
| --- | --- |
| `spring-boot-starter-webmvc` | Servlet REST API (almost all controllers). |
| `spring-boot-starter-webflux` | `Flux` / `ServerSentEvent` for `POST /api/v1/ai/chat/stream`. Not a second public reactive API surface. |
| `spring-boot-devtools` | Local restart (runtime, optional). |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI and `/v3/api-docs` on non-production profiles. |

## Security

| Dependency | Why |
| --- | --- |
| `spring-boot-starter-security` | Filter chain, CORS, CSRF off, stateless sessions, method-unrelated HTTP authorization. |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` 0.12.7 | HS256 access JWTs (`JwtService`). |

## Persistence / database

| Dependency | Why |
| --- | --- |
| `spring-boot-starter-data-jpa` | Entities, repositories, auditing. |
| `mysql-connector-j` | MySQL driver (runtime). |

## Redis / data store

| Dependency | Why |
| --- | --- |
| `spring-boot-starter-data-redis` | Lettuce client types. **Auto-config is excluded** so Redis is opt-in per module. |

## Validation

| Dependency | Why |
| --- | --- |
| `spring-boot-starter-validation` | `@Valid` DTOs, constraint annotations, `MethodArgumentNotValidException`. |

## External integrations

| Dependency | Why |
| --- | --- |
| `spring-boot-starter-mail` | OTP and password-reset SMTP. |
| `spring-boot-starter-thymeleaf` | HTML email templates. |
| `spring-ai-starter-model-openai` (BOM 2.0.0) | `ChatClient` against an OpenAI-compatible API (Gemini, Groq, or similar via `base-url`). Swagger-annotations excluded to avoid clashing with SpringDoc. |
| `io.minio:minio` 8.5.17 | Resume object storage. |
| `org.apache.pdfbox:pdfbox` 3.0.8 | Resume text extraction. |
| `org.jsoup:jsoup` 1.18.3 | HTML parse/noise strip for automated job-page extraction. |

## Resilience

No Resilience4j (or similar) dependency. Circuit breaker and bulkhead are **in-process Java** (`AiChatGuard`, `JobExtractionAiGuard`, `JobPageFetchGuard`).

## Testing

| Dependency | Why |
| --- | --- |
| `spring-boot-starter-webmvc-test` | `@WebMvcTest` / MockMvc-style controller tests. |
| `spring-boot-starter-security-test` | Security test support. |
| `spring-boot-starter-validation-test` | Validation test support. |
| `spring-boot-starter-data-jpa-test` | JPA slice tests where used. |

## Other

| Dependency | Why |
| --- | --- |
| `lombok` | Boilerplate on entities/DTOs; annotation processor in `maven-compiler-plugin`. |

## Internal project relationships (not Maven modules)

This is a **single Maven module**. Package-level coupling:

- Feature packages depend on `common` (`ApiResponse`, storage, URL util, `CurrentUserService`, global errors).
- Feature packages depend on `auth.entity.User` and JWT principal types.
- `jobextraction.automatedjobextraction` depends on **manual** extraction for the AI preview step.
- `chatassistant` and `jobextraction.manualextraction` call `ai` **in-process** (`AiService`), not over HTTP.
- `ai` reads `user` parsed resume context and `jobs` descriptions.

See [ARCHITECTURE.md](ARCHITECTURE.md).

## What is not on the classpath

- Flyway / Liquibase
- Spring Session
- Spring Boot Actuator (metrics go through a small `CopilotMetrics` helper; failures must not break requests)
- Resilience4j
- A second “microservices” BOM or Spring Cloud Gateway

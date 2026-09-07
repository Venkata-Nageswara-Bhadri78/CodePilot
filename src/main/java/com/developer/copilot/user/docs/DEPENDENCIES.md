# Dependencies

This document lists **direct** dependencies the `user` service needs and why. It does not list every transitive Maven artifact.

The application is one Maven module (`pom.xml`). There is no separate `user-service` JAR.

## Internal modules

| Dependency | Why the user service needs it |
|---|---|
| `com.developer.copilot.auth` | `User` entity (profile FK, `fullName`/`email` on responses), `JwtAuthenticationFilter` / `JwtService` (access tokens), `CustomUserDetails` (rate-limit user id, storage path ownership). |
| `com.developer.copilot.common` | `CurrentUserService`, `ApiResponse`, `GlobalExceptionHandler`, `FileStorageService` (MinIO), `InternalApiKeyFilter` and properties, `InternalApiRateLimitFilter` / `CommonRateLimitService` on internal parse URLs, `ChecksumUtil` (via storage). |

Other product modules (jobs, AI, chat) may **call** `ResumeParsingService` in-process. That is an inbound dependency; the user package does not import those modules.

## Framework

| Library | Why |
|---|---|
| Spring Web MVC (`spring-boot-starter-webmvc`) | REST controllers, multipart upload, `Resource` download. |
| Spring Security | Authenticated user APIs; JWT filter is in `auth` but the chain covers `/api/v1/users/**` and `/api/v1/internal/**`. |
| Spring Data JPA | Entities and repositories for profile, children, resumes, parsed data; pessimistic lock query; auditing timestamps. |
| Spring Validation | `@Valid` DTOs, `@HttpOrHttpsUrl`, `@AssertTrue` year ranges. |
| Spring `@Async` / `ThreadPoolTaskExecutor` | Background parse and async persist (`ResumeParsingAsyncConfig`). |
| Spring Transaction | Service `@Transactional`; `REQUIRES_NEW` parse writes; `TransactionSynchronization` after commit. |
| springdoc OpenAPI | Grouped docs for User and Internal APIs (non-production). |

## Persistence and object storage

| Library | Why |
|---|---|
| MySQL connector | JDBC URL used by JPA for user tables. |
| MinIO Java SDK (`io.minio:minio`) | Put/get/stat/remove PDF objects. `storage.provider=s3` still uses this client (S3-compatible). |

## Redis

| Library | Why |
|---|---|
| `spring-boot-starter-data-redis` + Lettuce | Optional `StringRedisTemplate` for user rate-limit counters when `app.user.redis.enabled=true`. |

If Redis is off, the starter is unused by this package at runtime; in-memory maps replace it.

## Security-related libraries

| Library | Why |
|---|---|
| JJWT (`io.jsonwebtoken`) | Used by `auth` to parse the Bearer token this service requires. User code does not call JJWT directly. |

Internal key comparison uses JDK `MessageDigest.isEqual`, not a separate crypto library.

## Parsing

| Library | Why |
|---|---|
| Apache PDFBox `3.0.8` | Load PDFs, enforce extract permission, strip text, detect encryption. This is the only PDF engine in the parse pipeline. |

Section detection and contact regexes are application code (`ResumeSectionParser`), not a resume-parsing SaaS.

## JSON

Controllers and `ResumeSectionsCodec` use Jackson (`tools.jackson` / Spring Boot's ObjectMapper) to serialize `ApiResponse`, section maps, and filter 429 bodies.

## Validation

Jakarta Validation (`jakarta.validation`) annotations on DTOs and the custom `HttpOrHttpsUrl` constraint.

## Observability

No Micrometer/Actuator dependency is required for user metrics. `UserMetrics` logs incrementing counters. Storage and internal-auth failures also increment `CopilotMetrics` in `common`.

## Test scope

| Library | Why |
|---|---|
| `spring-boot-starter-webmvc-test` | `MockMvc`, `@WebMvcTest`. |
| `spring-boot-starter-security-test` | Security test support used with `@WebMvcTest`. |
| Mockito (via Boot test starters) | Service and parser unit tests. |
| JUnit 5 | All tests in `src/test/java/com/developer/copilot/user`. |

PDFBox is on the main classpath; extractor tests build in-memory PDDocuments with it.

## What the user service does **not** use

- Spring AI / Gemini — parsing is PDFBox + heuristics, not an LLM.
- Mail / Thymeleaf — auth email, not profile.
- WebFlux — not used by user controllers (MVC only).

Those libraries may be on the application classpath for other packages; they are not part of the user-service runtime path described here.

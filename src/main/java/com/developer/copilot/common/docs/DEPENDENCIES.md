# Dependencies

Common is compiled into the same Maven artifact as the rest of Copilot (`com.developer:copilot`). There is no separate `common` module. This page lists **direct** libraries and **internal packages** the kernel actually uses, and why.

## Internal project dependencies

| Package | Why common needs it |
|---|---|
| `auth` | `User`, `CustomUserDetails`, `InvalidCredentialsException` and other auth exceptions mapped globally; JWT principal for current user, rate-limit identity, and storage ownership. |
| `user` | `ResumeProperties` (upload-size error text), `UserProfileProperties` (bean registration), user/resume/profile exceptions mapped globally. Internal resume **controllers** depend on common, not the reverse. |
| `jobs` | Job exceptions mapped globally. `JobServiceImpl` calls `UrlNormalizationUtil`. |
| `jobextraction` | Extraction exceptions mapped globally. Extraction service calls `UrlNormalizationUtil`. |
| `ai` | AI exceptions and AI rate-limit exception mapped globally. |
| `chatassistant` | `ChatConflictException` and chat rate-limit exception mapped globally. |

Common does not depend on feature *services*. The coupling is types (entities, exceptions, properties) so the kernel can authenticate, store files safely, and map errors.

## Framework

| Library | Why |
|---|---|
| Spring Web MVC (`spring-boot-starter-webmvc`) | Filters, `@RestControllerAdvice`, servlet API, `MultipartFile`, `Resource`. |
| Spring Security | `SecurityContextHolder`, `Authentication` for current user, internal user rate limit, storage ownership. JWT filter itself is in `auth`. |
| Spring Data JPA | `@EnableJpaAuditing` only. No common repositories. |
| Spring Boot configuration | `@ConfigurationProperties`, `FilterRegistrationBean`, `ApplicationRunner`, `@ConditionalOnProperty`, `Environment` profiles. |
| Spring Data Redis + Lettuce | Optional `StringRedisTemplate` / connection factory when `app.common.redis.enabled=true`. |
| springdoc OpenAPI | `SwaggerConfig` security schemes, `ApiResponse` schema, public-path customizer. |

Boot parent version is `4.1.0` (Java 17) in `pom.xml`.

## Security-related libraries

| Library | Why |
|---|---|
| `java.security.MessageDigest` | Constant-time internal key compare (`isEqual`); SHA-256 for file checksums and URL hashes. |
| JJWT | **Not used in common.** Token create/parse is `auth`. |

## Persistence and data stores

| Library | Why |
|---|---|
| MySQL driver | Runtime for the app; common has no JDBC of its own. |
| MinIO Java SDK `io.minio:minio` (8.5.17) | Bucket and object operations. S3-compatible endpoints use this same client. |
| OkHttp (pulled in by MinIO) | Timeouts on `MinioClient` (10s connect, 30s I/O). |

## Validation

| Library | Why |
|---|---|
| `spring-boot-starter-validation` / Jakarta Validation | `ConstraintViolationException` handling; feature DTOs. Common has no custom `ConstraintValidator`. |

## Observability

| Library | Why |
|---|---|
| Micrometer (`io.micrometer.core`) | `CopilotMetrics` counters. Transitive; **Actuator is not** a direct dependency, so there is no documented `/actuator` scrape endpoint in this project. |

## JSON

Internal filters use `tools.jackson.databind.ObjectMapper` (Jackson 3 as shipped with this Boot generation) to write `ApiResponse`. Auth’s `JsonAuthenticationEntryPoint` uses `com.fasterxml.jackson` — that split is auth, not common, but clients still see the same envelope fields.

## Utilities in the JDK / Spring

`URI` for URL parse, `UUID` for object keys, `ConcurrentHashMap` for in-memory limits. Lombok is used on several common types (properties, `ApiResponse`, filters).

## Intentionally unused by common

- Spring AI, JavaMail, Thymeleaf, PDFBox — those belong to AI, auth mail, and `user` resume parsing. Common only stores PDF **bytes** and checks `%PDF` magic; it does not parse documents.
- WebFlux — not used by common filters/services.

## Test scope

`spring-boot-starter-*-test` (webmvc, security, validation, data-jpa) plus Mockito. Common tests generally do not need `@SpringBootTest`.

# Jobs Dependencies

This document lists **direct** dependencies the jobs slice needs, and why. Transitive Maven artifacts are omitted.

## Internal project dependencies

| Dependency | Why jobs needs it |
|---|---|
| `auth.entity.User` | Owner of each `JobEntity`; id used in every query and uniqueness key |
| `auth.entity.BaseEntity` | `createdAt` / `updatedAt` auditing columns |
| `auth.security.CustomUserDetails` | Rate-limit filter reads user id from the security principal |
| `auth.jwt.JwtAuthenticationFilter` / `JwtService` | Authenticate Bearer tokens before jobs controllers run |
| `auth.config.SecurityConfig` / `JsonAuthenticationEntryPoint` | `/api/v1/jobs/**` is authenticated; unauthenticated JSON 401 |
| `auth.exception.InvalidCredentialsException` | Thrown by `CurrentUserService` when the principal is missing |
| `common.security.CurrentUserService` | Resolve the `User` for create/list/mutate |
| `common.util.UrlNormalizationUtil` | Canonical URL + SHA-256 hash for dedupe |
| `common.exception.InvalidJobUrlException` | Strict URL rejection |
| `common.exception.GlobalExceptionHandler` | HTTP mapping for jobs and shared exceptions |
| `common.dto.ApiResponse` | Success and error envelope |
| `common.config.JpaConfig` | JPA auditing for timestamps |
| `common.config.SwaggerConfig` | Shared Bearer scheme and `ApiResponse` schema (non-prod) |

Jobs does **not** depend on mail, MinIO, or Spring AI for its own HTTP API.

## Other services that depend on jobs

These are inbound uses of jobs **data**, not libraries jobs imports for its controller:

| Consumer | Use |
|---|---|
| Job extraction | `JobRepository.existsByUserIdAndSourceUrlHash` and `DuplicateJobException` |
| AI | `JobRepository.findByIdAndUserId` / `JobEntity` text for prompts |
| Chat assistant | Same ownership lookup; `ChatSession` FK to `JobEntity` |

Document those services separately. For jobs, the important fact is: keep `findByIdAndUserId` and the unique hash constraint stable.

## Framework and web

| Library | Why |
|---|---|
| `spring-boot-starter-webmvc` | REST controller, `FilterRegistrationBean` |
| `spring-boot-starter-validation` | `@NotBlank`, `@Size`, `@Valid` |
| `spring-boot-starter-security` | Filter order relative to JWT; `SecurityContextHolder` in the rate-limit filter |
| Spring Data Commons `Page` / `Pageable` / `Sort` | List API |

## Persistence

| Library | Why |
|---|---|
| `spring-boot-starter-data-jpa` | `JobRepository`, `JobEntity`, transactions, entity graph |
| `mysql-connector-j` | MySQL dialect/driver used by the app datasource |

Hibernate maps `@ElementCollection` skills and the unique constraint name used in `saveJob`.

## Redis

| Library | Why |
|---|---|
| `spring-boot-starter-data-redis` | `StringRedisTemplate`, Lettuce `LettuceConnectionFactory` for optional jobs counters |

Used only when `app.jobs.redis.enabled=true`. Not used to cache entities.

## JSON and API docs

| Library | Why |
|---|---|
| Jackson (via web starter) | Filter writes `ApiResponse` for 429; MVC JSON bodies |
| `jackson-datatype-jsr310` | Filter `ObjectMapper` timestamps |
| `springdoc-openapi-starter-webmvc-ui` | `JobsOpenApiConfig` grouped docs, `@Operation` on the controller |
| Lombok | DTOs, entities, constructors |

## Testing

| Library | Why |
|---|---|
| `spring-boot-starter-webmvc-test` | `MockMvc`, `@WebMvcTest` |
| `spring-boot-starter-security-test` | Security slice tests |
| JUnit 5 + Mockito | Service, mapper, Redis, rate-limit tests |

## Not used by jobs (even if on the classpath)

Spring AI, JavaMail, Thymeleaf, PDFBox, MinIO, and WebFlux are used by other modules. Jobs HTTP handling is servlet MVC, not WebFlux.

JWT (`jjwt-*`) is used by auth to mint/validate tokens that jobs then require; jobs code does not call JJWT directly.

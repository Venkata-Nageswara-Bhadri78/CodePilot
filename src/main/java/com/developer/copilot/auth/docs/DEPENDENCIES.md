# Auth Dependencies

Auth is a package in the Copilot Maven module (`com.developer:copilot`). It does not have its own `pom.xml`. This list is the **direct** libraries and internal types the auth code uses, and why. Transitive Maven artifacts are omitted.

## Internal project dependencies

| Dependency | Why auth needs it |
| --- | --- |
| `com.developer.copilot.common.dto.ApiResponse` | Uniform JSON envelope for every auth response. |
| `com.developer.copilot.common.exception.GlobalExceptionHandler` | Maps auth exceptions to HTTP statuses (auth also has `RateLimitExceptionHandler` for 429). |
| `com.developer.copilot.common.security.CurrentUserService` | Resolves the authenticated `User` for `/me`, `/logout`, `/logout-all`, `/extension-token`. Implementation lives in common and reads `CustomUserDetails`. |
| `com.developer.copilot.common.config.JpaConfig` | `@EnableJpaAuditing` for `BaseEntity` timestamps. |
| `CopilotApplication` | Enables `EmailProperties`; excludes Boot Redis auto-config so auth Redis is opt-in. |
| Other feature packages | Not called by auth. They consume JWTs and `User`. |

`ResourceAlreadyExistsException` is declared in auth and handled globally; current auth flows do not throw it.

## Framework and web

| Library | Why |
| --- | --- |
| `spring-boot-starter-webmvc` | REST controllers, filters, JSON. |
| `spring-boot-starter-validation` | `@Valid`, `@Email`, `@Size`, `@ValidPassword`. |
| Spring Scheduling | `AuthTokenCleanupJob` (`@EnableScheduling` on `AuthConfig`). |
| Jackson | `ApiResponse` in filters, `JsonAuthenticationEntryPoint`, and `JsonAccessDeniedHandler` (`JavaTimeModule` for `LocalDateTime`). |

## Security

| Library | Why |
| --- | --- |
| `spring-boot-starter-security` | Filter chain, CORS, `PasswordEncoder`, `UserDetails`, stateless sessions. |
| `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson` (0.12.7) | Create and parse HS256 access JWTs (`JwtService`). |

BCrypt comes from Spring Security (`BCryptPasswordEncoder`). Login does not use OAuth2 or resource-server JWT auto-config; validation is the custom filter.

## Persistence and Redis

| Library | Why |
| --- | --- |
| `spring-boot-starter-data-jpa` | `User` and token entities/repositories. |
| `mysql-connector-j` | JDBC driver for the shared MySQL database. |
| `spring-boot-starter-data-redis` | Lettuce + `StringRedisTemplate` when `app.auth.redis.enabled=true`. Boot auto-config stays excluded; auth supplies its own factory. |

## Mail and templates

| Library | Why |
| --- | --- |
| `spring-boot-starter-mail` | `JavaMailSender` OTP and password-reset messages. |
| `spring-boot-starter-thymeleaf` | HTML bodies from `otp-email` and `password-reset` templates. |

## API documentation

| Library | Why |
| --- | --- |
| `springdoc-openapi-starter-webmvc-ui` | `@Operation` / `@Tag` on `AuthController`; `AuthOpenApiConfig` group `authentication`. Inactive on `prod`/`production`. |

## Validation (Jakarta)

Jakarta Bean Validation annotations on DTOs, plus the composed `@ValidPassword` (`@Pattern` + `@Size` + `@NotBlank`). No custom `ConstraintValidator` class.

## Supporting libraries used in auth code

| Library | Why |
| --- | --- |
| Lombok | Boilerplate on entities, DTOs, services. |
| JDK `javax.crypto` / `MessageDigest` | HMAC-SHA256 and SHA-256 in `CredentialDigests`. |
| JDK `SecureRandom` | `OtpGenerator` 6-digit codes. |

## Test-only (auth tests)

`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, Mockito / JUnit 5 — `MockMvc`, `@WebMvcTest`, `@WithMockUser`, `@MockitoBean`.

## Not used by auth (present on the application classpath)

Spring AI, MinIO, PDFBox, WebFlux, and other feature starters are application dependencies. Auth does not import them. Do not assume auth talks to object storage or an LLM.

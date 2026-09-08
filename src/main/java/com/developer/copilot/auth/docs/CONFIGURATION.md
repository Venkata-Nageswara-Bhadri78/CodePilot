# Auth Configuration

Auth reads a mix of `AuthProperties` (`app.auth.*`), JWT/mail/CORS properties, Spring Mail, the datasource (MySQL), and optional Redis. Values below are **defaults from code** or from `application.properties.example`. Do not copy secrets from local files; use placeholders.

Project-wide MySQL is included because auth tables live in that database. MinIO, AI, and internal API keys are not auth configuration.

## JWT

| Property | Used by | Purpose |
| --- | --- | --- |
| `app.jwt.secret` | `JwtService`, OTP HMAC in `AuthServiceImpl` (`@Value`) | HS256 signing key **and** OTP HMAC key. Minimum 32 characters. Rejected if it looks like a placeholder (`enter-your-jwt`, `your-jwt-configuration`, or `changeme`). |
| `APP_JWT_SECRET` | `AuthSecretsGuard` on `prod`/`production` | Must be set in the environment; boot fails if blank. |
| `app.auth.access-expiry-ms` | `JwtService` (`@Value`, default `900000`), also field on `AuthProperties` | Access JWT lifetime in milliseconds (default 15 minutes). |

`application.properties.example` may still list `app.jwt.expiration`. **`JwtService` does not bind that property.** Changing it does not change access-token lifetime.

## Auth behavior (`app.auth`)

`AuthProperties` prefix `app.auth`:

| Property | Default | Purpose |
| --- | --- | --- |
| `otp-expiry-minutes` | 10 | OTP row and email copy |
| `reset-expiry-minutes` | 15 | Password-reset row and email copy |
| `refresh-expiry-days` | 30 | Refresh row lifetime |
| `max-otp-attempts` | 5 | Failed HMAC attempts before `"OTP not found."` |
| `access-expiry-ms` | 900000 | Same lifetime `JwtService` reads |
| `max-active-refresh-tokens` | 5 | Cap of non-revoked refresh rows per user on login. `<= 0` disables the cap |
| `mail-cooldown-seconds` | 60 | OTP resend and forgot-password mail spacing. `<= 0` skips cooldown |
| `login-rate-limit-per-minute` | 5 | Per-IP (filter) and per-email (service) |
| `register-rate-limit-per-minute` | 5 | Per-IP and per-email |
| `verify-rate-limit-per-minute` | 10 | Per-IP and per-email |
| `resend-rate-limit-per-minute` | 3 | Per-IP and per-email |
| `forgot-rate-limit-per-minute` | 3 | Per-IP and per-email |
| `refresh-rate-limit-per-minute` | 10 | Per-IP only |
| `max-failed-logins` | 10 | Lockout threshold. `<= 0` disables lockout |
| `failed-login-window-minutes` | 15 | Failure window |

## Browser extension (`app.extension`)

| Property | Default | Purpose |
| --- | --- | --- |
| `enabled` | `true` | Kill switch for minting and accepting extension JWTs |
| `id` | blank | Chrome extension ID. When set, CORS allows `chrome-extension://<id>`. Not an authorization secret. Must not be a URL or `*` |
| `access-expiry-ms` | 900000 | Extension access JWT lifetime |
| `token-rate-limit-per-minute` | 10 | Per-IP (filter) and per-user (service) on `POST /api/v1/auth/extension-token` |

Relaxed binding accepts `otpExpiryMinutes` or `otp-expiry-minutes`. Setting a per-minute limit to `<= 0` disables that bucket (filter and `consume` permit).

## Redis (`app.auth.redis`)

| Property | Default | Purpose |
| --- | --- | --- |
| `enabled` | `false` | When `true`, create Lettuce beans |
| `host` | `localhost` | |
| `port` | `6379` | |
| `password` | none | Optional |
| `database` | `0` | |
| `timeout-ms` | `2000` | Command timeout |
| `key-prefix` | `auth` | Redis key prefix |

See [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md).

## Mail

| Property | Purpose |
| --- | --- |
| `app.mail.from` | SMTP From address. Required; blank fails startup. |
| `app.mail.sender-name` | From display name. Required. |
| `spring.mail.host` | SMTP host (example: `smtp.gmail.com`) |
| `spring.mail.port` | Example `587` |
| `spring.mail.username` / `spring.mail.password` | SMTP credentials (placeholder in example file) |
| `spring.mail.properties.mail.smtp.auth` | Example `true` |
| `spring.mail.properties.mail.smtp.starttls.enable` | Example `true` |
| `spring.mail.properties.mail.smtp.connectiontimeout` / `timeout` / `writetimeout` | Example `5000` ms |

Templates: `src/main/resources/templates/otp-email.html`, `password-reset.html`.

## CORS (`cors`)

| Property | Default (Java) |
| --- | --- |
| `cors.allowed-origins` | `http://localhost:5173`, `5174`, `3000` and the same ports on `127.0.0.1` |

Wildcard `*` is ignored. `allowCredentials` is hardcoded `true` in `SecurityConfig`. If `app.extension.id` is set, `chrome-extension://<id>` is merged into the allow-list.

## HTTP security / OpenAPI / profiles

| Item | Behavior |
| --- | --- |
| Spring profiles `prod` or `production` | `AuthSecretsGuard` on; Swagger matchers not permitAll; `AuthOpenApiConfig` / common `SwaggerConfig` inactive (`@Profile("!prod & !production")`) |
| `application-prod.properties` and `application-production.properties` | `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` |
| `dev` profile | Registers `TestController` `GET /api/v1/test` |
| `springdoc.swagger-ui.path` | Example `/swagger-ui.html` (non-prod) |
| `springdoc.api-docs.path` | Example `/v3/api-docs` |

## Persistence (shared, required by auth)

| Property | Relevance |
| --- | --- |
| `spring.datasource.url` / `username` / `password` | MySQL for `users` and token tables |
| `spring.jpa.hibernate.ddl-auto` | How tables are created/updated |
| JPA auditing | Enabled globally; fills `created_at` / `updated_at` |

Auth scheduling uses `@EnableScheduling` on `AuthConfig` (hourly cleanup). Clock bean is `Clock.systemUTC()`.

## Logging

Example local files set `logging.level.org.springframework.security=DEBUG`. That is application configuration, not an auth-only feature.

## Startup checks that abort the process

1. `JwtService.validateConfiguration` — secret length/placeholder, positive `access-expiry-ms`, valid `app.extension.id` if set.
2. `EmailServiceImpl.validateMailProperties` — from and sender name.
3. `AuthSecretsGuard` — `APP_JWT_SECRET` on prod/production.

There is no auth-specific setup guide beyond setting these properties and having MySQL (and Redis if enabled) reachable.

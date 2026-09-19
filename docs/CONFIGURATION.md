# Global configuration

Configuration is Spring Boot properties, plus a small number of environment variables used as **secrets**. Copy `src/main/resources/application.properties.example` for local setup. Do not commit real passwords, API keys, or JWT secrets.

This document explains **purpose**. It does not list every DTO field or copy live values from a local `application.properties`.

## Profiles and files

| Source | Role |
| --- | --- |
| `application.properties` (local, not for docs) | Developer overrides. Treat as secret-bearing. |
| `application.properties.example` | Safe template with placeholders. |
| `application-production.properties` | Disables SpringDoc (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`). |
| Active profiles `prod` / `production` | Swagger beans not loaded (`SwaggerConfig` is `@Profile("!prod & !production")`). Security chain does not `permitAll` Swagger. `AuthSecretsGuard` requires `APP_JWT_SECRET`. |
| `local` / `dev` | Only profiles that may skip the internal API key when `internal.api.enabled=false`. |
| `dev` | Also loads `TestController` (`GET /api/v1/test`). |

There is no other `application-*.properties` in `src/main/resources`.

## Application identity

- `spring.application.name` — process name (`copilot`).

## Database (MySQL / JPA)

| Property | Purpose |
| --- | --- |
| `spring.datasource.url` | JDBC URL (database name `copilot` in the example). |
| `spring.datasource.username` / `password` | DB credentials. Use `<ENVIRONMENT_VARIABLE>` / `<SECRET>` — never document real values. |
| `spring.jpa.hibernate.ddl-auto` | Schema strategy in this project: typically `update`. No Flyway/Liquibase. |
| `spring.jpa.show-sql` / `hibernate.format_sql` | SQL logging for local debugging. |

## JWT and auth lifetimes

| Property | Purpose |
| --- | --- |
| `app.jwt.secret` | HMAC key for access JWTs and OTP HMAC. Min 32 characters. Placeholder strings rejected at startup. Production: set `APP_JWT_SECRET`. |
| `app.auth.access-expiry-ms` | Access JWT lifetime. **This is what `JwtService` reads** (default 900000 = 15 minutes). |
| `app.jwt.expiration` | Present in the example file. **Not bound by `JwtService`.** Changing it does not change token lifetime. |
| `app.auth.otp-expiry-minutes` | OTP validity (default 10). |
| `app.auth.reset-expiry-minutes` | Password-reset token validity (default 15). |
| `app.auth.refresh-expiry-days` | Refresh UUID lifetime (default 30). |
| `app.auth.max-otp-attempts` | Failed OTP attempts (default 5). |
| `app.auth.max-active-refresh-tokens` | Session cap (default 5). |
| `app.auth.mail-cooldown-seconds` | Resend/forgot mail spacing (default 60). |
| `app.auth.login-rate-limit-per-minute` (and register/verify/resend/forgot/refresh) | Auth POST budgets. |
| `app.auth.max-failed-logins` / `failed-login-window-minutes` | Lockout window; login still returns the generic 401. |

## CORS and browser extension

| Property | Purpose |
| --- | --- |
| `cors.allowed-origins` | Explicit origins for credentialed CORS. `*` is dropped. |
| `app.extension.enabled` | Kill switch for minting/accepting extension JWTs. |
| `app.extension.id` | Chrome extension ID only (not a URL). Adds `chrome-extension://<id>` to CORS. |
| `app.extension.access-expiry-ms` | Extension access JWT lifetime. |
| `app.extension.token-rate-limit-per-minute` | Mint budget for `POST /extension-token`. `<= 0` disables that limit. |

## Email

| Property | Purpose |
| --- | --- |
| `spring.mail.host` / `port` / SMTP auth and STARTTLS | JavaMailSender. |
| `spring.mail.username` / `password` | SMTP credentials (`<SECRET>`). |
| `spring.mail.properties.mail.smtp.*timeout` | Connection/read/write timeouts (ms). |
| `app.mail.from` / `app.mail.sender-name` | Envelope From and display name (`EmailProperties`, required). |

## Object storage

Prefix `storage.*`: provider (`minio` / S3-compatible), `endpoint`, `access-key`, `secret-key`, `bucket-name`, `auto-create-bucket`. Used only for resume PDFs.

## Resume and profile

| Property | Purpose |
| --- | --- |
| `resume.max-resume-count` | Max PDFs per user (default 10). |
| `resume.max-file-size-mb` | Upload cap (default 5); also used in oversize error text. |
| `resume.parsing.max-attempts` | Parse retries before FAILED. |
| `resume.parsing.max-text-length` | Truncation bound. |
| `resume.parsing.parser-version` | Stamped on parse rows; mismatch can trigger re-parse. |
| `resume.parsing.max-pages` | PDFBox page cap (default 30). |
| `resume.parsing.timeout-seconds` | On-demand internal parse wait (default 15). |
| `user.profile.max-child-items` | Max rows per child collection (default 20). |

## Internal API

| Property | Purpose |
| --- | --- |
| `internal.api.enabled` | Shared-secret check. Disabled only on `local`/`dev`; elsewhere fail-closed. |
| `internal.api.key` | Current secret (`<SECRET>`). Header default `X-Internal-Api-Key`. |
| `internal.api.previous-key` | Optional rotation secret. |
| `internal.api.header-name` / `path-prefix` | Defaults `X-Internal-Api-Key` and `/api/v1/internal`. |
| `app.common.internal-key-per-minute` / `internal-user-per-minute` | Hallway rate limits (defaults 60 / 30). Bound on `CommonRateLimitProperties` (`app.common`). |

## AI provider

Spring AI OpenAI-compatible client:

- `spring.ai.openai.api-key` — `<SECRET>` (example uses `${GEMINI_API_KEY:…}`).
- `spring.ai.openai.base-url` — provider endpoint.
- Model/temperature: example file uses `spring.ai.openai.chat.options.model` and `…temperature`.

Application overlay (`AiProperties` / `AiRateLimitProperties`, prefix `app.ai`):

| Property | Purpose |
| --- | --- |
| `app.ai.provider` | Label in metadata (not a second HTTP client). |
| `app.ai.default-model` | Model name used by `ChatClient` as source of truth. |
| `app.ai.timeout-seconds` | Provider timeout. `app.ai.streaming-timeout-seconds` is a deprecated alias that sets the same field. |
| `app.ai.max-completion-tokens` / `cover-letter-max-completion-tokens` | Completion caps by mode. |
| `app.ai.max-prior-turns-sent` | Turns actually sent (default 16). |
| `app.ai.chat-per-minute` / `resume-context-per-minute` | AI HTTP rate limits (defaults 8 / 20). |
| `app.ai.agentic-server-url` | Present in the example file. **No Java consumer binds it.** It does not start a second AI client. |

## Feature rate limits (defaults)

| Prefix | Defaults (per identity per minute) |
| --- | --- |
| `app.jobs` | post 15, mutate 30, list 60, search 20, read 60 |
| `app.user` | upload 8, delete 8, parse (internal) 20 |
| `app.chatassistant.messages-per-minute` | 8 |
| `app.jobextraction.parse-per-minute` | 8 |
| `app.automatedjobextraction.parse-per-minute` | 5 |

## Automated fetch

`app.automatedjobextraction.http.*`: connect/request timeouts, max response bytes, max redirects, user-agent (`CopilotJobExtraction/1.0`).

## Redis (optional, per module)

Pattern (all **off** by default):

```text
app.<module>.redis.enabled=false
app.<module>.redis.host=localhost
app.<module>.redis.port=6379
app.<module>.redis.database=0
app.<module>.redis.timeout-ms=2000
app.<module>.redis.key-prefix=<module>
app.<module>.redis.password=<SECRET>   # optional
```

Modules: `auth`, `common`, `user`, `jobs`, `ai`, `chatassistant`, `jobextraction`, `automatedjobextraction`.

See [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md).

## OpenAPI

- `springdoc.swagger-ui.path` / `springdoc.api-docs.path` — local UI.
- Production profile file turns SpringDoc off.

## Logging

`logging.level.org.springframework.security` — often DEBUG in local templates. Not a security control.

## Secrets checklist (never copy real values)

Use placeholders only: `<SECRET>`, `<ENVIRONMENT_VARIABLE>`, `<REDACTED>`.

- Datasource password
- `app.jwt.secret` / `APP_JWT_SECRET`
- SMTP password
- `internal.api.key` / `previous-key`
- Storage access/secret keys
- `spring.ai.openai.api-key`
- Redis password

Module-level property notes: each package’s `CONFIGURATION.md`.

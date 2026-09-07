# Jobs Configuration

Jobs reads two property groups of its own (`app.jobs` and `app.jobs.redis`) plus shared application settings for HTTP security, JWT, MySQL, and CORS. There is no jobs-specific `application.yml`. Values below are **Java defaults** and the checked-in `application.properties.example`. Do not copy real secrets into documentation or git.

## Jobs rate-limit properties

`JobsRateLimitProperties` prefix: `app.jobs`. Window length is **hardcoded 60 seconds** in `JobsRateLimitFilter` (not a property).

| Property | Default | Meaning |
|---|---|---|
| `app.jobs.post-per-minute` | 15 | `POST /api/v1/jobs` |
| `app.jobs.mutate-per-minute` | 30 | PUT, PATCH, DELETE |
| `app.jobs.list-per-minute` | 60 | `GET /api/v1/jobs` with no query string |
| `app.jobs.search-per-minute` | 20 | `GET /api/v1/jobs` with a query string |
| `app.jobs.read-per-minute` | 60 | `GET /api/v1/jobs/{id}` |

A limit of `0` or less causes the filter to skip that bucket (permit). Defaults are all positive.

These keys are not currently in `application.properties.example`; defaults apply until you add them.

## Jobs Redis properties

`JobsRedisProperties` prefix: `app.jobs.redis`. Beans exist only when `enabled=true`.

| Property | Default | Meaning |
|---|---|---|
| `app.jobs.redis.enabled` | `false` | Distributed rate-limit counters |
| `app.jobs.redis.host` | `localhost` | Redis host |
| `app.jobs.redis.port` | `6379` | Port |
| `app.jobs.redis.password` | empty | Optional. Use `${REDIS_PASSWORD:}` or similar in real config — never commit the value |
| `app.jobs.redis.database` | `0` | Logical database index |
| `app.jobs.redis.timeout-ms` | `2000` | Lettuce command timeout |
| `app.jobs.redis.key-prefix` | `jobs` | Redis key prefix |

Not listed in `application.properties.example` at the time of writing.

Global Spring Data Redis auto-config is **excluded**. Setting `spring.data.redis.*` does not create the jobs connection; use `app.jobs.redis.*`.

## MySQL (required for jobs rows)

From `application.properties.example`:

| Property | Role for jobs |
|---|---|
| `spring.datasource.url` | JDBC URL (example: `jdbc:mysql://localhost:3306/copilot`) |
| `spring.datasource.username` | DB user |
| `spring.datasource.password` | DB password (placeholder in example) |
| `spring.jpa.hibernate.ddl-auto` | Example uses `update` — creates/updates `jobs` and `job_skills` |
| `spring.jpa.show-sql` / `hibernate.format_sql` | Debug SQL (example `true`) |

Jobs has no separate datasource. JPA auditing is enabled globally (`JpaConfig` `@EnableJpaAuditing`) so `createdAt` / `updatedAt` populate.

## Authentication and CORS (required to call jobs)

Jobs endpoints are not permit-all. Relevant shared properties:

| Property | Role |
|---|---|
| `app.jwt.secret` / `APP_JWT_SECRET` | HMAC secret (must be a long non-placeholder value) |
| Access token expiry | Auth module (`app.auth.access-expiry-ms`; example file also shows `app.jwt.expiration`) |
| `cors.allowed-origins` | Browser origins (defaults include localhost Vite/React ports). `*` is ignored when credentials are on |

Login remains `POST /api/v1/auth/login`. Jobs does not define mail, OTP, or refresh-token properties.

## OpenAPI

`JobsOpenApiConfig` is `@Profile("!prod & !production")`. Example also sets:

- `springdoc.swagger-ui.path=/swagger-ui.html`
- `springdoc.api-docs.path=/v3/api-docs`

In production profiles, Swagger matchers are not `permitAll` and the jobs OpenAPI group bean is not registered.

## Spring application name

`spring.application.name=copilot` — informational; jobs does not branch on it.

## What you do not configure for jobs

- No jobs cache TTL for entities (there is no entity cache)
- No jobs-specific thread pool
- No jobs SMTP or object-storage settings
- Description/page limits are **code constants** (`JobLimits`), not properties

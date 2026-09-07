# Configuration

Properties below are the ones the `user` service actually reads, plus shared settings that change how its endpoints behave. Placeholders replace secrets. Project-wide MySQL/JWT setup is included only where this service depends on it.

Source of defaults: Java `@ConfigurationProperties` classes. `src/main/resources/application.properties.example` documents a subset (resume caps, parsing attempts/text/version, MinIO, internal API key). User Redis and user rate-limit properties are **code defaults** unless you add them to a local properties file.

Do not commit real passwords, JWT secrets, or internal keys.

## Resume (`resume.*`) — `ResumeProperties`

| Property | Default | Purpose |
|---|---|---|
| `resume.max-resume-count` | `10` | Active PDFs per profile |
| `resume.max-file-size-mb` | `5` | Service check **and** servlet multipart max file size |
| `resume.parsing.max-attempts` | `3` | Parse retries before `FAILED` |
| `resume.parsing.max-text-length` | `200000` | Truncate extracted text |
| `resume.parsing.parser-version` | `v1` | Stamped on parsed rows; mismatch forces re-parse |
| `resume.parsing.max-pages` | `30` | Reject PDFs with more pages |
| `resume.parsing.timeout-seconds` | `15` | On-demand parse wait (minimum 1 in code) |

Multipart request size is `max-file-size-mb` plus 512 KB (`ResumeMultipartConfig`) so multipart headers do not consume the file budget.

## Profile (`user.profile.*`) — `UserProfileProperties`

| Property | Default | Purpose |
|---|---|---|
| `user.profile.max-child-items` | `20` | Cap per collection (experiences, educations, projects, additional-info, links) |

Not shown in `application.properties.example`; the default applies until overridden.

## User rate limits (`app.user.*`) — `UserRateLimitProperties`

Prefix `app.user` (not `app.user.rate-limit`).

| Property | Default | Purpose |
|---|---|---|
| `app.user.upload-per-minute` | `8` | `POST /api/v1/users/resumes` |
| `app.user.delete-per-minute` | `8` | `DELETE /api/v1/users/resumes/{id}` |
| `app.user.parse-per-minute` | `20` | Internal parsed-resume GETs |

Window is hardcoded to 60 seconds in `UserRateLimitFilter`. Setting a limit to `0` disables that bucket (filter treats limit ≤ 0 as pass-through).

## User Redis (`app.user.redis.*`) — `UserRedisProperties`

| Property | Default | Purpose |
|---|---|---|
| `app.user.redis.enabled` | `false` | Create Lettuce beans; use Redis for user rate limits |
| `app.user.redis.host` | `localhost` | |
| `app.user.redis.port` | `6379` | |
| `app.user.redis.password` | empty | Optional AUTH |
| `app.user.redis.database` | `0` | |
| `app.user.redis.timeout-ms` | `2000` | Command timeout |
| `app.user.redis.key-prefix` | `user` | Key namespace |

Enable for multi-instance. See [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md).

## Object storage (`storage.*`) — used on every resume upload/download/delete

| Property | Example / default | Purpose |
|---|---|---|
| `storage.provider` | `minio` | Must be `minio` or `s3` if set (`StorageConfig`) |
| `storage.endpoint` | `http://localhost:9000` | |
| `storage.access-key` | `<access-key>` | Not `minioadmin` on remote non-laptop deploys |
| `storage.secret-key` | `<secret-key>` | Same |
| `storage.bucket-name` | `copilot-resumes` | Resume objects |
| `storage.auto-create-bucket` | `true` locally | Must be `false` outside `local`/`dev` unless the endpoint is loopback |

Startup (`StorageStartupValidator`) initializes the bucket (create if allowed) and rejects default admin credentials on remote endpoints outside laptop profiles.

## Internal API (`internal.api.*`) — internal parse controller

| Property | Default | Purpose |
|---|---|---|
| `internal.api.enabled` | `true` | Key check; `false` only honored on `local`/`dev` |
| `internal.api.key` | (required in non-laptop) | Shared secret, min 32 chars outside laptop |
| `internal.api.previous-key` | empty | Rotation |
| `internal.api.header-name` | `X-Internal-Api-Key` | |
| `internal.api.path-prefix` | `/api/v1/internal` | Filter URL patterns |

## Common hallway limits (internal parse also consumes these)

| Property | Default |
|---|---|
| `app.common.internal-key-per-minute` | `60` |
| `app.common.internal-user-per-minute` | `30` |
| `app.common.redis.enabled` | `false` |
| `app.common.redis.*` | Same shape as user Redis; prefix default `common` |

## Authentication and CORS (apply to user HTTP)

User endpoints do not define JWT properties, but they require a valid access token:

- `app.jwt.secret` / `APP_JWT_SECRET` — HMAC secret (min 32 characters; placeholder values rejected at auth startup).
- Access expiry is `app.auth.access-expiry-ms` in `JwtService` (code default 900000 ms). The example file also shows `app.jwt.expiration`; that name is **not** what `JwtService` injects.

CORS: `cors.allowed-origins` (list). Defaults are localhost ports `5173`, `5174`, `3000` (and 127.0.0.1 equivalents). Wildcard `*` is ignored when credentials are allowed.

## Database (JPA)

User entities use the application datasource (`spring.datasource.url` MySQL) and `spring.jpa.hibernate.ddl-auto` as configured globally. There is no user-specific schema property.

JPA auditing is enabled in `JpaConfig`, which also registers `ResumeProperties` and `UserProfileProperties`.

## Parse executor (code, not properties)

`ResumeParsingAsyncConfig` is fixed: core pool 2, max 4, queue 50, thread name prefix `resume-parse-`, abort on rejection, wait 30 seconds on shutdown. There are no `application` knobs for pool size.

## OpenAPI

`UserOpenApiConfig` and `InternalOpenApiConfig` load only when the profile is not `prod` or `production`. `springdoc.swagger-ui.path` / `springdoc.api-docs.path` are application-wide.

## Example fragment (safe)

```properties
resume.max-resume-count=10
resume.max-file-size-mb=5
resume.parsing.max-attempts=3
resume.parsing.max-text-length=200000
resume.parsing.parser-version=v1

user.profile.max-child-items=20

app.user.upload-per-minute=8
app.user.delete-per-minute=8
app.user.parse-per-minute=20

app.user.redis.enabled=false
app.user.redis.host=localhost
app.user.redis.port=6379
app.user.redis.key-prefix=user

storage.provider=minio
storage.endpoint=http://localhost:9000
storage.access-key=<access-key>
storage.secret-key=<secret-key>
storage.bucket-name=copilot-resumes
storage.auto-create-bucket=true

internal.api.enabled=true
internal.api.key=<generate-a-long-random-internal-service-key>
```

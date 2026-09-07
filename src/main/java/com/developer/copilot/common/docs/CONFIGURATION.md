# Configuration

Common reads Boot `@ConfigurationProperties` and a few entries from `src/main/resources/application.properties.example`. Values below are **defaults from code** or **examples**, never production secrets.

Do not commit real `internal.api.key`, MinIO keys, or Redis passwords. Use environment variables or a secret store.

## Internal service-to-service API (`internal.api`)

Class: `InternalApiProperties`. Example file currently sets `enabled` and `key`.

| Property | Default | Purpose |
|---|---|---|
| `internal.api.enabled` | `true` | When `false`, the key check is skipped only on `local`/`dev`; other profiles still 401 and startup fails. |
| `internal.api.key` | none | Shared secret callers send in the header. Required and must be strong outside laptop profiles. |
| `internal.api.previous-key` | none | Optional previous secret during rotation. Same strength rules if set. Never logged. |
| `internal.api.header-name` | `X-Internal-Api-Key` | Header that carries the secret. |
| `internal.api.path-prefix` | `/api/v1/internal` | Prefix for both internal filters. Trailing slashes are stripped. |

Example (placeholders only):

```properties
internal.api.enabled=true
internal.api.key=<generate-a-long-random-internal-service-key>
```

The example key string is a **placeholder** and will fail `InternalApiStartupValidator` outside `local`/`dev` (it matches a disallowed token after normalization). Replace it with a random secret of at least 32 characters.

## Hallway rate limits (`app.common`)

Class: `CommonRateLimitProperties`. Not present in the example properties file; defaults apply.

| Property | Default | Purpose |
|---|---|---|
| `app.common.internal-key-per-minute` | `60` | Max internal requests per 60s for identity `"service"`. `0` disables. |
| `app.common.internal-user-per-minute` | `30` | Max internal requests per 60s per JWT user id. `0` disables. |

## Common Redis (`app.common.redis`)

Class: `CommonRedisProperties`. Not in the example file. Off by default.

| Property | Default | Purpose |
|---|---|---|
| `app.common.redis.enabled` | `false` | If `true`, Lettuce + rate-limit counters. If `false`, in-memory limiter. |
| `app.common.redis.host` | `localhost` | Standalone Redis host. |
| `app.common.redis.port` | `6379` | Port. |
| `app.common.redis.password` | empty | Optional AUTH password. |
| `app.common.redis.database` | `0` | Database index. |
| `app.common.redis.timeout-ms` | `2000` | Command timeout. |
| `app.common.redis.key-prefix` | `common` | Key namespace. |

Do not confuse with `app.auth.redis.*` or `app.jobextraction.redis.*`.

## Object storage (`storage`)

Class: `StorageProperties`. Example file:

```properties
storage.provider=minio
storage.endpoint=http://localhost:9000
storage.access-key=minioadmin
storage.secret-key=minioadmin
storage.bucket-name=copilot-resumes
storage.auto-create-bucket=true
```

| Property | Purpose |
|---|---|
| `storage.provider` | `minio` or `s3` (both use `MinioClient`). Other non-blank values fail context creation. Blank is allowed. |
| `storage.endpoint` | MinIO/S3 API endpoint. Loopback hosts may use default admin keys. |
| `storage.access-key` / `storage.secret-key` | Credentials. Must not be `minioadmin` on a remote endpoint outside `local`/`dev`. |
| `storage.bucket-name` | Bucket for all `FileStorageService` operations. |
| `storage.auto-create-bucket` | If the bucket is missing, create it. Must be `false` outside laptop profiles when the endpoint is not loopback. |

MinIO client timeouts are **code constants**, not properties: connect 10s, read/write 30s.

## OpenAPI (`springdoc` + profile)

From the example file:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs
```

`SwaggerConfig` is `@Profile("!prod & !production")`. Auth `SecurityConfig` also blocks those matchers on production profiles.

## Resume size (read by the global handler)

```properties
resume.max-resume-count=10
resume.max-file-size-mb=5
```

Common’s `GlobalExceptionHandler` uses `max-file-size-mb` only for the `MaxUploadSizeExceededException` message. Count and parsing settings are consumed by `user`.

## JPA (application-wide, relevant because of `JpaConfig`)

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/copilot
spring.datasource.username=<your-sql-username>
spring.datasource.password=<your-sql-password>
spring.jpa.hibernate.ddl-auto=update
```

Common has no entities. MySQL is required for the rest of the app; auditing is enabled by `JpaConfig`.

## Redis auto-config exclusion

`CopilotApplication` excludes:

- `DataRedisAutoConfiguration`
- `DataRedisReactiveAutoConfiguration`
- `DataRedisRepositoriesAutoConfiguration`

Setting `app.common.redis.enabled=true` is what creates common’s Lettuce factory. A Redis server is not required until then.

## Profiles that change common behavior

| Profiles | Effect |
|---|---|
| `local` or `dev` | May disable internal API; may use short keys; may use `minioadmin` and auto-create on remote endpoints. |
| anything else (including no profile, `staging`, `prod`, `production`) | Strong internal key required; internal API cannot be disabled; remote MinIO cannot use default admin or auto-create. |
| `prod` or `production` | `SwaggerConfig` not loaded; Swagger matchers not `permitAll`. |

Profile names are compared case-insensitively.

## Other example properties not owned by common

JWT (`app.jwt.*`), mail, Google/OpenAI (`spring.ai.*`, `app.ai.*`), auth Redis, job-extraction Redis, and resume parsing knobs are consumed by other packages. They appear in the same example file because this is a single Boot application.

# Configuration

Properties that affect `jobextraction`. Secrets are shown as placeholders only.

This module reads **`app.jobextraction.*`**. It also depends on shared JWT, CORS, AI timeout/model, and Springdoc settings owned elsewhere.

## Job-extraction properties

Prefix `app.jobextraction` (`JobExtractionRateLimitProperties`):

| Property | Default | Purpose |
| --- | --- | --- |
| `app.jobextraction.parse-per-minute` | `8` | Max `POST /api/v1/job-extraction/**` per identity per 60-second window (IP bucket and user bucket each use this number). `<= 0` disables the rate-limit filter. |

Prefix `app.jobextraction.redis` (`JobExtractionRedisProperties`):

| Property | Default | Purpose |
| --- | --- | --- |
| `app.jobextraction.redis.enabled` | `false` | When `true`, create Lettuce beans for distributed counters and preview JSON. |
| `app.jobextraction.redis.host` | `localhost` | Redis host |
| `app.jobextraction.redis.port` | `6379` | Redis port |
| `app.jobextraction.redis.password` | empty | Used only if non-blank |
| `app.jobextraction.redis.database` | `0` | Logical database index |
| `app.jobextraction.redis.timeout-ms` | `2000` | Lettuce command timeout |
| `app.jobextraction.redis.key-prefix` | `jobextraction` | First segment of every key |

Example file (`application.properties.example`) includes the Redis block with `enabled=false`. If local `application.properties` omits it, the Java defaults still apply.

**Example (no secrets):**

```properties
app.jobextraction.parse-per-minute=8
app.jobextraction.redis.enabled=false
app.jobextraction.redis.host=localhost
app.jobextraction.redis.port=6379
app.jobextraction.redis.database=0
app.jobextraction.redis.timeout-ms=2000
app.jobextraction.redis.key-prefix=jobextraction
# app.jobextraction.redis.password=${JOBEXTRACTION_REDIS_PASSWORD:}
```

For more than one application instance, set `enabled=true` and point `host` at shared Redis. See [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md).

## Shared configuration this service relies on

### Authentication

| Property | Role for parse |
| --- | --- |
| `app.jwt.secret` | JWT validation (auth module). Production profiles require `APP_JWT_SECRET` in the environment. |
| `app.jwt.expiration` | Token lifetime (auth). |

Do not put real signing keys in documentation or git.

### AI extraction (owned by `ai`)

Parse calls `AiService.extractJobInfo`, which uses:

| Property | Default / notes | Role |
| --- | --- | --- |
| `app.ai.default-model` | e.g. `gemini-flash-latest` | Model name on the ChatClient call |
| `app.ai.timeout-seconds` | `60` | `callWithTimeout` for extraction. `app.ai.streaming-timeout-seconds` still binds to the same field. |
| `app.ai.max-completion-tokens` | `2048` | `maxTokens` on the extraction options |
| `spring.ai.openai.api-key` | provider key | Required for a live extract |
| `spring.ai.openai.base-url` | provider base URL | OpenAI-compatible endpoint |

Temperature for this call is **hardcoded `0.0`** in `AiServiceImpl`, not a property.

### CORS

`cors.allowed-origins` (default localhost Vite/React ports). `Retry-After` is exposed. Wildcard `*` is not used with credentials.

### OpenAPI / Springdoc

| Property / profile | Effect |
| --- | --- |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` in the sample config |
| `springdoc.api-docs.path` | `/v3/api-docs` |
| `prod` / `production` | `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` |
| `JobExtractionOpenApiConfig` | `@Profile("!prod & !production")` — group `job-extraction` |

### Datasource

MySQL (`spring.datasource.*`) is required only because `JobRepository` runs `existsByUserIdAndSourceUrlHash`. This service does not add datasource properties.

### Redis auto-config

`CopilotApplication` excludes Spring Boot Data Redis auto-configuration. Enabling `app.jobextraction.redis.enabled` does **not** turn on Boot's default Redis repositories. Only this module's factory is created (auth and other modules have their own optional Redis configs).

## Hardcoded values (not properties)

| Value | Location |
| --- | --- |
| Preview TTL 3 minutes | `JobExtractionPreviewCache` |
| Rate-limit window 60 seconds | `JobExtractionRateLimitFilter` |
| Bulkhead 5 / circuit 3 failures / 30s open | `JobExtractionAiGuard` |
| Field max lengths | `JobExtractionLimits` |
| Filter order `-80` | `JobExtractionRateLimitConfig` |

Changing those requires a code change.

## Profiles

Use `prod` or `production` to keep swagger off. JWT secret handling for those profiles is defined in auth configuration, not in this package.

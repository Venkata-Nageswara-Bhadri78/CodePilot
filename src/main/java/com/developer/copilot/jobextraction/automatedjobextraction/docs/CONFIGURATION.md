# Configuration

Properties that affect `automatedjobextraction`. Secrets are shown as placeholders only.

This module reads **`app.automatedjobextraction.*`**. It also depends on shared JWT, CORS, AI timeout/model, manual-extraction Redis/rate-limit (gateway), and Springdoc settings owned elsewhere.

## Automated job-extraction properties

Prefix `app.automatedjobextraction` (`AutomatedJobExtractionRateLimitProperties`):

| Property | Default | Purpose |
| --- | --- | --- |
| `app.automatedjobextraction.parse-per-minute` | `5` | Max `POST /api/v1/automated-job-extraction/**` per identity per 60-second window (IP bucket and user bucket each use this number). `<= 0` disables the rate-limit filter. |

Prefix `app.automatedjobextraction.http` (`AutomatedJobExtractionHttpProperties`):

| Property | Default | Purpose |
| --- | --- | --- |
| `app.automatedjobextraction.http.connect-timeout-ms` | `5000` | JDK `HttpClient` connect timeout |
| `app.automatedjobextraction.http.request-timeout-ms` | `15000` | Per-GET request timeout |
| `app.automatedjobextraction.http.max-response-bytes` | `1500000` | Body cap; exceeding throws fetch `502` |
| `app.automatedjobextraction.http.max-redirects` | `3` | Manual redirect hops after SSRF |
| `app.automatedjobextraction.http.user-agent` | `CopilotJobExtraction/1.0` | `User-Agent` header; blank falls back to the same default in the client |

Prefix `app.automatedjobextraction.redis` (`AutomatedJobExtractionRedisProperties`):

| Property | Default | Purpose |
| --- | --- | --- |
| `app.automatedjobextraction.redis.enabled` | `false` | When `true`, create Lettuce beans for distributed counters and extracted-text cache |
| `app.automatedjobextraction.redis.host` | `localhost` | Redis host |
| `app.automatedjobextraction.redis.port` | `6379` | Redis port |
| `app.automatedjobextraction.redis.password` | empty | Used only if non-blank |
| `app.automatedjobextraction.redis.database` | `0` | Logical database index |
| `app.automatedjobextraction.redis.timeout-ms` | `2000` | Lettuce command timeout |
| `app.automatedjobextraction.redis.key-prefix` | `automatedjobextraction` | First segment of every key |

Example file (`application.properties.example`) includes the HTTP and Redis blocks. If local `application.properties` omits them, the Java defaults still apply.

**Example (no secrets):**

```properties
app.automatedjobextraction.parse-per-minute=5
app.automatedjobextraction.http.connect-timeout-ms=5000
app.automatedjobextraction.http.request-timeout-ms=15000
app.automatedjobextraction.http.max-response-bytes=1500000
app.automatedjobextraction.http.max-redirects=3
app.automatedjobextraction.http.user-agent=CopilotJobExtraction/1.0
app.automatedjobextraction.redis.enabled=false
app.automatedjobextraction.redis.host=localhost
app.automatedjobextraction.redis.port=6379
app.automatedjobextraction.redis.database=0
app.automatedjobextraction.redis.timeout-ms=2000
app.automatedjobextraction.redis.key-prefix=automatedjobextraction
# app.automatedjobextraction.redis.password=${AUTOMATEDJOBEXTRACTION_REDIS_PASSWORD:}
```

For more than one application instance, set `enabled=true` and point `host` at shared Redis. See [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md).

## Shared configuration this service relies on

### Authentication

| Property | Role for parse |
| --- | --- |
| `app.jwt.secret` | JWT validation (auth module). Production profiles require `APP_JWT_SECRET` in the environment. |
| `app.jwt.expiration` | Token lifetime (auth). |
| `app.extension.*` | Extension JWT minting and CORS origin; this prefix allows those tokens. |

Do not put real signing keys in documentation or git.

### Manual extraction and AI (owned by other modules)

The gateway calls `JobExtractionService.extractJobInfo`, which uses:

| Property | Role |
| --- | --- |
| `app.jobextraction.parse-per-minute` | Does **not** apply to this HTTP path. Separate filter. |
| `app.jobextraction.redis.*` | Optional preview-JSON cache and manual rate-limit Redis (if the user also calls manual parse). AI preview cache for the gateway uses this Redis when enabled. |
| `app.ai.default-model` | Model name on the ChatClient call |
| `app.ai.timeout-seconds` | `callWithTimeout` for extraction (default 60) |
| `app.ai.max-completion-tokens` | `maxTokens` on the extraction options |
| `spring.ai.openai.api-key` | Required for a live extract |
| `spring.ai.openai.base-url` | OpenAI-compatible endpoint |

Temperature for this call is **hardcoded `0.0`** in `AiServiceImpl`, not a property.

### CORS

`cors.allowed-origins` (default localhost Vite/React ports). Extension origin may be added. `Retry-After` is exposed. Wildcard `*` is not used with credentials.

### OpenAPI / Springdoc

| Property / profile | Effect |
| --- | --- |
| `springdoc.swagger-ui.path` / `springdoc.api-docs.path` | Example `/swagger-ui.html` and `/v3/api-docs` |
| `AutomatedJobExtractionOpenApiConfig` | `@Profile("!prod & !production")` — group `automated-job-extraction` |
| `prod` / `production` | `SecurityConfig` does not `permitAll` swagger paths. Production tests assert `/v3/api-docs/automated-job-extraction` is not HTTP 200. |

### Datasource

MySQL (`spring.datasource.*`) is required only because `JobRepository` runs `existsByUserIdAndSourceUrlHash` inside the gateway. This service does not add datasource properties.

### Redis auto-config

`CopilotApplication` excludes Spring Boot Data Redis auto-configuration. Enabling `app.automatedjobextraction.redis.enabled` does **not** turn on Boot's default Redis repositories. Only this module's factory is created (auth, jobextraction, and other modules have their own optional Redis configs).

## Hardcoded values (not properties)

| Value | Location |
| --- | --- |
| Extracted-text TTL 3 minutes | `ExtractedJobContentCache` |
| Rate-limit window 60 seconds | `AutomatedJobExtractionRateLimitFilter` |
| Fetch bulkhead 8 / circuit 3 failures / 30s open | `AutomatedJobExtractionLimits` / `JobPageFetchGuard` |
| Min identifiable job text 120 characters | `AutomatedJobExtractionLimits.MIN_JOB_TEXT_LENGTH` |
| Field max lengths / extracted-text cap | `AutomatedJobExtractionLimits` / `JobExtractionLimits` |
| Filter order `-80` | `AutomatedJobExtractionRateLimitConfig` |

Changing those requires a code change.

## Profiles

Use `prod` or `production` to keep swagger off. JWT secret handling for those profiles is defined in auth configuration, not in this package.

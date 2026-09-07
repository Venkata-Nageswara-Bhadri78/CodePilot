# Configuration

Chat assistant binds two property prefixes. They are **not** currently set in `application.properties` or `application.properties.example`; the values below are the **Java defaults** on the `@ConfigurationProperties` classes until you override them.

Do not put Redis passwords or LLM API keys in documentation or git. Use environment variables or your secret manager.

## Chat assistant rate limit

Prefix `app.chatassistant` — `ChatAssistantRateLimitProperties`.

| Property | Default | Meaning |
| --- | --- | --- |
| `app.chatassistant.messages-per-minute` | `8` | Max `POST /api/v1/chat-assistant/jobs/{jobId}/messages` per identity per 60-second window (IP bucket and user bucket each use this number) |

The 60-second window is hard-coded in `ChatAssistantRateLimitFilter` (`WINDOW_SECONDS = 60`), not a property.

## Chat assistant Redis

Prefix `app.chatassistant.redis` — `ChatAssistantRedisProperties`.

| Property | Default | Meaning |
| --- | --- | --- |
| `app.chatassistant.redis.enabled` | `false` | When `true`, create a dedicated Lettuce factory and use Redis counters. When `false` (local/tests), in-memory limiter only |
| `app.chatassistant.redis.host` | `localhost` | Standalone Redis host |
| `app.chatassistant.redis.port` | `6379` | Port |
| `app.chatassistant.redis.password` | unset | Optional AUTH password |
| `app.chatassistant.redis.database` | `0` | Redis logical database |
| `app.chatassistant.redis.timeout-ms` | `2000` | Lettuce command timeout |
| `app.chatassistant.redis.key-prefix` | `chatassistant` | Prefix for counter keys |

Enable Redis for multi-instance deployments so the 8/minute limit is global. See [REDIS-INFRASTRUCTURE.md](./REDIS-INFRASTRUCTURE.md).

## Persistence (shared JPA)

Chat tables are created/updated with the application’s Hibernate DDL setting (`spring.jpa.hibernate.ddl-auto`, `update` in the current properties files). Chat assistant does not define a separate datasource. MySQL is required because that is what the app uses for JPA.

JPA auditing (`created_at` / `updated_at` on `BaseEntity`) is enabled in `JpaConfig`.

## OpenAPI

`ChatAssistantOpenApiConfig` is `@Profile("!prod & !production")`. Non-prod builds expose a Springdoc group `chat-assistant` for `/api/v1/chat-assistant/**`. Production profiles omit this group (and typically omit Swagger permit-all in `SecurityConfig`).

Documented servers in that customizer are `http://localhost:8080` and a placeholder `https://api.yourdomain.com` (docs only).

## Configuration this service depends on but does not own

Send calls `AiService.continueJobChat`. Relevant **AI** defaults (for wait time and model window):

| Property | Default (AI module) | Why chat assistant cares |
| --- | --- | --- |
| `app.ai.timeout-seconds` | `60` | Send can block on the order of this timeout |
| `app.ai.max-prior-turns-sent` | `16` | Chat assistant already loads 16 turns; AI may trim further if this is lowered |
| `app.ai.default-model` / provider settings | AI module | Provider and model are not selected in chat assistant |

JWT, CORS (`cors.allowed-origins`), and the auth Redis instance are configured in the auth module. Chat assistant does not read those prefixes.

## Example overrides (placeholders)

```properties
app.chatassistant.messages-per-minute=8
app.chatassistant.redis.enabled=true
app.chatassistant.redis.host=localhost
app.chatassistant.redis.port=6379
app.chatassistant.redis.password=${CHATASSISTANT_REDIS_PASSWORD:}
app.chatassistant.redis.database=0
app.chatassistant.redis.timeout-ms=2000
app.chatassistant.redis.key-prefix=chatassistant
```

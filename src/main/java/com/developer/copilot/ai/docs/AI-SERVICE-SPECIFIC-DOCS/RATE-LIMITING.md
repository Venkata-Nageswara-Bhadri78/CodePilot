# Rate limiting

The `ai` service meters two expensive/sensitive HTTP surfaces so a stolen JWT or a single IP cannot freely burn provider quota or scrape resume PII.

Limits are **not** applied to `GET /api/v1/ai/health` or `GET /api/v1/ai/config`. Job extraction and chat-assistant have their own filters in those modules.

## Where it runs

`AiRateLimitFilter` is a servlet `OncePerRequestFilter` registered at order **`-80`**, after `springSecurityFilterChain` (default `-100`). JWT has already populated `SecurityContext` when the user counter runs. The filter is **not** added inside the security chain, so a request is counted once.

URL patterns: `/api/v1/ai`, `/api/v1/ai/*`, `/api/v1/ai/chat/*`.

## Buckets

| HTTP | Bucket | Property | Default |
|---|---|---|---|
| `POST /api/v1/ai/chat` | `chat` | `app.ai.chat-per-minute` | 8 |
| `POST /api/v1/ai/chat/stream` | `chat` (same) | same | 8 |
| `GET /api/v1/ai/resume-context` | `resume-context` | `app.ai.resume-context-per-minute` | 20 |
| Anything else under `/api/v1/ai` | `other` | — | not limited (`limit 0`) |

Window length in the filter is fixed at **60 seconds**.

Chat and stream share one budget. Hitting `/chat` then `/chat/stream` from the same user counts twice toward the same 8.

## Identities

For a limited request the filter:

1. Consumes `{bucket}-ip` keyed by client IP.
2. If `CustomUserDetails` has a user id, consumes `{bucket}-user` keyed by that id.

Both must succeed. Tests show the same user is limited across different `remoteAddr` values.

**IP:** first value in `X-Forwarded-For` (comma-separated), else `request.getRemoteAddr()`, else `unknown`. There is no trusted-proxy check in this filter.

If the user is somehow unauthenticated but the filter still ran, only the IP bucket is used.

## Store

`AiRateLimitServiceImpl`:

- Prefer Redis `INCR` + TTL when `AiRedisService` exists.
- On Redis exception: log a warning and use an in-memory `ConcurrentHashMap` of timestamp deques (sliding window).
- `limit <= 0` always allows (how health/config skip metering).

Redis is a **fixed** window (TTL set when count becomes 1). Memory is a **sliding** window. See [REDIS-INFRASTRUCTURE.md](../REDIS-INFRASTRUCTURE.md).

`consumeOrThrow` exists on the interface and is tested; the HTTP filter uses `consume` and writes the response directly.

## Denied response

- Status `429`
- Header `Retry-After: <seconds>`
- Body `ApiResponse` with `success: false`, message `Too many requests. Please try again later.`

`GlobalExceptionHandler` maps `com.developer.copilot.ai.ratelimit.exception.RateLimitExceededException` the same way if something throws it.

## Multi-instance

With `app.ai.redis.enabled=false` (default), each JVM has its own counters. Turn Redis on for shared limits.

Circuit/bulkhead in `AiChatGuard` is a separate, in-process throttle (max 5 concurrent provider calls). It is not a substitute for these HTTP limits.

## Changing limits

Set `app.ai.chat-per-minute` and `app.ai.resume-context-per-minute`. Keep `AiRateLimitFilterTest` updated if you add paths or split chat vs stream into different buckets.

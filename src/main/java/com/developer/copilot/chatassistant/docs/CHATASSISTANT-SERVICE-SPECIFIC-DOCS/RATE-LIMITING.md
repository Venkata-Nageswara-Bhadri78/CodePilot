# Rate limiting

Paid job-chat sends are limited so one user or one IP cannot flood the model. History, list, and delete are not limited by this filter.

Implementation: `ChatAssistantRateLimitFilter` → `ChatAssistantRateLimitServiceImpl` → optional Redis.

## What is limited

| Request | Bucket | Limited? |
| --- | --- | --- |
| `POST /api/v1/chat-assistant/jobs/{jobId}/messages` | `messages` | Yes |
| `GET /api/v1/chat-assistant/jobs/{jobId}` | `other` | No |
| `GET /api/v1/chat-assistant` | `other` | No |
| `DELETE /api/v1/chat-assistant/jobs/{jobId}` | `other` | No |
| Any other path (filter still registered on `/api/v1/chat-assistant*`) | `other` | No |

`bucketFor` requires method `POST`, path under `/api/v1/chat-assistant`, containing `/jobs/`, and ending with `/messages`.

`limitFor("messages")` is `app.chatassistant.messages-per-minute` (default **8**). `limitFor("other")` is **0**, which means “do not consume; pass through”.

Window: **60 seconds** (`WINDOW_SECONDS` on the filter).

## Two identities

Each limited request consumes **IP first, then user**:

1. `messages-ip` + client IP  
2. If `CustomUserDetails` has a user id: `messages-user` + that id  

Both use the same numeric limit. Exhausting either bucket returns 429 **without** calling the controller.

Because the filter runs **after** JWT (order -80 vs security -100), a stolen token is counted on the **victim user id**, not only on the attacker’s IP. Tests cover the same user hitting the limit from three different IPs.

IP resolution: `X-Forwarded-For` first comma-separated value, else `HttpServletRequest.getRemoteAddr()`, else `"unknown"`.

Unauthenticated requests should not reach this filter with a successful send (security returns 401 first). If they did, only the IP bucket would apply.

## Redis vs memory

| Mode | Algorithm | When |
| --- | --- | --- |
| Redis | `INCR` + `EXPIRE` on first hit (fixed window) | `app.chatassistant.redis.enabled=true` and increment succeeds |
| Memory | Deque of timestamps, drop older than window (sliding) | Redis bean missing, or increment throws |

Deny: `RateLimitResult.deny(retryAfterSeconds)` with a minimum of 1 second. Redis retry-after is key TTL; if TTL is 0, the filter uses the full 60s.

429 is written in the filter (not `RateLimitExceededException`). Body message: `"Too many requests. Please try again later."` Header: `Retry-After`.

`consumeOrThrow` exists on the service interface and is tested; the HTTP filter uses `consume` + write instead.

## Filter registration

`FilterRegistrationBean`:

- URL patterns: `/api/v1/chat-assistant`, `/api/v1/chat-assistant/*`
- Order: `-80`
- Enabled: true
- **Not** added to `SecurityFilterChain` (comment: avoids double-counting)

## Operational notes

- Default `enabled=false` means **each app instance** has its own 8/minute memory window
- Turn Redis on for horizontal scale
- Redis outage degrades to memory; sends are not failed closed
- Counters are not cleared on chat DELETE; they expire with the window
- This limiter is independent of auth-module and AI-module rate limits

See [REDIS-INFRASTRUCTURE.md](../REDIS-INFRASTRUCTURE.md) and [CONFIGURATION.md](../CONFIGURATION.md).

# Jobs Redis Infrastructure

Redis is **optional** and is used **only for jobs rate-limit counters**. It is not a cache for job lists, `GET /{id}`, search results, or URL hashes.

Boot’s Data Redis auto-configuration is excluded on `CopilotApplication`. Jobs creates its own Lettuce connection when `app.jobs.redis.enabled=true`. Local development and tests can run with Redis off.

## Why Redis exists here

Rate limits must stay correct across multiple application instances (for example ECS). An in-memory map is per JVM: two instances would each allow a full budget. Redis provides a shared counter. When Redis is disabled or a Redis call throws, `JobsRateLimitServiceImpl` falls back to that in-memory map so a single instance still limits traffic.

## What is stored

Each consume is an increment of a string key with a TTL. There are no hashes, lists, or job payloads.

Key format (`JobsRedisKeyBuilder`):

```
{keyPrefix}:{namespace}:{identity}
```

Default prefix: `jobs`.

Namespace used by the limiter: `rl-` plus the filter bucket and side, for example `rl-post-ip`, `rl-post-user`, `rl-mutate-ip`, `rl-search-user`.

Identity: client IP or numeric user id. Values are trimmed, lowercased, and `:` is replaced with `_` so IPv6 addresses cannot collide with the key delimiter.

Example: user `7` on the POST user bucket → `jobs:rl-post-user:7`.

Blank identity becomes `unknown`.

## Increment and expiry

`JobsRedisRepository.increment`:

1. `INCR` the key
2. If the new value is `1` and TTL is positive, `EXPIRE` the key for the window (`Duration.ofSeconds(60)` from the filter)

Later increments in the same window do **not** refresh TTL. Redis mode is a **fixed window** that starts at the first hit. Remaining time comes from `TTL` in seconds (`ttlSeconds`). Values below 0 are treated as 0; the limiter then uses the full window as `Retry-After` if the counter is already over limit.

In-memory fallback is a **sliding window** of timestamps, not a fixed Redis-style counter. Behavior is similar at human timescales but not identical.

## Component roles

| Component | Responsibility |
|---|---|
| `JobsRedisProperties` | `enabled`, host, port, password, database, timeout, key prefix |
| `JobsRedisConfig.Enabled` | Beans only when `enabled=true` |
| `JobsRedisKeyBuilder` | Namespaced, sanitized keys |
| `JobsRedisRepository` | `INCR` / `EXPIRE` / `TTL` via `StringRedisTemplate` |
| `JobsRedisService` / `Impl` | `increment(namespace, identity, ttl)` and `ttlSeconds` |
| `JobsRateLimitServiceImpl` | Prefer Redis; log a warning and use memory on `RuntimeException` |

`LettuceConnectionFactory.setValidateConnection(false)`. Command timeout defaults to 2000 ms.

## Failure behavior

| Situation | Behavior |
|---|---|
| `enabled=false` (default) | No jobs Redis beans. Limiter constructed with `redisService == null` → memory only |
| Redis enabled and reachable | Distributed fixed-window counters |
| Redis throws at increment | WARN log `"Jobs Redis rate-limit failed; using in-memory fallback"` then memory for that call |
| `limit <= 0` | Permit without touching Redis |

A Redis outage therefore **fails open to local limits**, not to unlimited traffic on that node, but budgets are no longer cluster-wide until Redis recovers.

## Configuration

Prefix `app.jobs.redis` (Java defaults in `JobsRedisProperties`):

| Property | Default | Purpose |
|---|---|---|
| `app.jobs.redis.enabled` | `false` | Create connection and use Redis for counters |
| `app.jobs.redis.host` | `localhost` | Redis host |
| `app.jobs.redis.port` | `6379` | Redis port |
| `app.jobs.redis.password` | unset | Optional AUTH |
| `app.jobs.redis.database` | `0` | Redis logical DB |
| `app.jobs.redis.timeout-ms` | `2000` | Lettuce command timeout |
| `app.jobs.redis.key-prefix` | `jobs` | First segment of every key |

These keys are **not** listed in `application.properties.example` at the time of writing; the Java defaults still apply until you add them.

Password must not be committed. Use an environment placeholder in real deployments.

Rate-limit numeric budgets live under `app.jobs.*` (not `app.jobs.redis.*`) and are described in [CONFIGURATION.md](CONFIGURATION.md) and [RATE-LIMITING.md](JOBS-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

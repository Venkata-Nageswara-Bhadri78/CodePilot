# Redis and shared data-store architecture

Redis is **optional infrastructure**, not the system of record. Boot’s Data Redis auto-configuration is **excluded** on `CopilotApplication` so a missing Redis does not fail startup. Each module that uses Redis defines its own Lettuce connection when `app.<module>.redis.enabled=true`.

Default in `application.properties.example`: **all Redis flags false**. Local and tests use in-memory maps.

## Why Redis exists

When multiple app instances run, in-memory rate limits and caches do not share state. Redis provides:

1. **Distributed sliding-window / INCR counters** for rate limits (and auth mail cooldown / login-fail).
2. **Short JSON caches** for job-extraction previews and automated extracted page content (TTL 3 minutes).

Redis is **not** used for JWT sessions, Spring Session, or MySQL offload.

## Module ownership

Each module owns keys under its prefix. Do not share one connection bean across modules.

| Module | Enable property | Default prefix | What is stored |
| --- | --- | --- | --- |
| auth | `app.auth.redis.enabled` | `auth` | Rate-limit INCR, mail cooldown (`SET NX`), login-fail counts |
| common | `app.common.redis.enabled` | `common` | Internal `/api/v1/internal/**` hallway counters |
| user | `app.user.redis.enabled` | `user` | Resume upload/delete/parse rate limits |
| jobs | `app.jobs.redis.enabled` | `jobs` | Jobs API rate limits |
| ai | `app.ai.redis.enabled` | `ai` | Chat and resume-context rate limits |
| chatassistant | `app.chatassistant.redis.enabled` | `chatassistant` | Send-message rate limits |
| jobextraction (manual) | `app.jobextraction.redis.enabled` | `jobextraction` | Parse rate limits + preview JSON |
| automatedjobextraction | `app.automatedjobextraction.redis.enabled` | `automatedjobextraction` | Parse rate limits + extracted content cache |

Typical connection settings per module: `host`, `port`, `database`, `timeout-ms`, optional `password`, `key-prefix`.

## Key convention

Builders produce:

```text
{prefix}:{namespace}:{identity}
```

Colons inside identity (IPv6, emails) are replaced with `_` so they cannot collide with the delimiter.

Rate-limit namespaces look like `rl-<bucket>` (e.g. `rl-login`). Auth also uses `mail` and `login-fail`. Manual extraction preview namespace is `preview`; identity is `{userId}_{urlHash}`.

TTL on rate-limit keys matches the window (typically 60 seconds). Preview/content caches: **3 minutes**.

## Caching vs rate limiting

| Kind | Behavior |
| --- | --- |
| Rate limits | INCR + TTL; over limit → 429. Redis errors → **in-memory fallback** on that instance (still limited, not fail-open). |
| Preview cache | GET/SET JSON. Cache key is user + URL hash, **not** pasted body. Failed extracts are not cached. Duplicate job check still runs on hits. |
| Automated content cache | Same TTL pattern for fetched/extracted text before calling manual parse |

## Authentication and Redis

Auth does **not** store JWTs in Redis. Optional Redis only backs abuse counters so lockout and cooldowns work across instances.

## Failure behavior

- Redis disabled: memory maps; **not** correct across multiple JVMs.
- Redis enabled but command fails: log WARN, fall back to memory for that call.
- This is **not** a strong consistency guarantee for rate limits during Redis outages.

## Configuration (names only)

See [CONFIGURATION.md](CONFIGURATION.md). Do not put Redis passwords in documentation.

Module details: each package’s `REDIS-INFRASTRUCTURE.md` (auth, user, jobs, ai, chatassistant, common, manual jobextraction). Automated extraction follows the same pattern in code (`AutomatedJobExtractionRedisConfig`).

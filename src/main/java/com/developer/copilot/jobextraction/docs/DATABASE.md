# Database

`jobextraction` **does not own a schema**. There is no `job_extraction` table, entity, or repository in this package. Parse never calls `save` / `persist`.

The only database interaction is a **read-only duplicate pre-check** on the `jobs` table owned by the `jobs` module.

## Why MySQL is involved

Duplicate detection must match what `POST /api/v1/jobs` will enforce (`uk_job_user_source_url_hash`). If parse skipped this check, the user could wait on the model and then fail on save. The check uses the same canonical URL hash the jobs module stores.

## Entity used (read-only)

`JobEntity` / table `jobs`. Fields this service cares about:

| Column | Role for parse |
| --- | --- |
| `user_id` | Duplicate scope. Only the **current** user's rows. |
| `source_url_hash` | SHA-256 hex (64 chars) of the **canonical** `source_url`. |
| `source_url` | Not queried by parse. Hash is the lookup key. |

Uniqueness:

```text
UNIQUE uk_job_user_source_url_hash (user_id, source_url_hash)
```

Two different users may store the same hash. The same user may not.

```mermaid
erDiagram
    USER ||--o{ JOBS : saves
    USER {
        bigint id PK
    }
    JOBS {
        bigint id PK
        bigint user_id FK
        varchar source_url
        char source_url_hash
    }
```

Other `jobs` columns (title, skills, descriptions, and so on) are unused by this module.

`auth.User` is loaded as the security principal (JWT filter / `CurrentUserService`), not queried by a job-extraction repository.

## Query

```text
JobRepository.existsByUserIdAndSourceUrlHash(currentUser.getId(), urlHash)
```

`urlHash` is `UrlNormalizationUtil.sha256Hex(normalizedUrl)` after `normalizeStrict`.

If the method returns true, parse throws `DuplicateJobException` with `This post was already added to your records.` → HTTP **409**. The AI is not called.

## Transactions

`JobExtractionServiceImpl.extractJobInfo` is **not** `@Transactional`.

Spring Data still runs `existsBy...` in a short repository transaction. The AI call is outside that transaction so a connection is not held for the model timeout.

There are no job-extraction-specific isolation or locking hints.

## Lifecycle

Parse does not create, update, expire, or delete rows. After the client saves via `jobs`, the next parse of the same canonical URL for that user hits this exists-check and returns 409 even if a preview is still in Redis/memory.

## What not to add here

Do not treat preview cache JSON in Redis as a database. See [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md).

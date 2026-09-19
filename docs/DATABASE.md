# Global data architecture

MySQL via Spring Data JPA. Schema is updated with `spring.jpa.hibernate.ddl-auto` (typically `update`). There is **no** Flyway/Liquibase module. `common` owns **no** tables; it enables JPA auditing (`created_at` / `updated_at` on `BaseEntity`).

All user-owned business data is scoped by `users.id`. There is no shared/global job board table.

```mermaid
erDiagram
    USER ||--o{ REFRESH_TOKEN : has
    USER ||--o{ EMAIL_VERIFICATION : has
    USER ||--o{ PASSWORD_RESET_TOKEN : has
    USER ||--o| USER_PROFILE : has
    USER ||--o{ JOB : owns
    USER ||--o{ CHAT_SESSION : lists
    USER_PROFILE ||--o{ RESUME : has
    USER_PROFILE ||--o{ WORK_EXPERIENCE : has
    USER_PROFILE ||--o{ EDUCATION : has
    USER_PROFILE ||--o{ PROJECT : has
    USER_PROFILE ||--o{ ADDITIONAL_INFO : has
    USER_PROFILE ||--o{ PROFILE_LINK : has
    RESUME ||--o| RESUME_PARSED_DATA : parsed
    JOB ||--o{ JOB_SKILL : skills
    JOB ||--o| CHAT_SESSION : one_chat
    CHAT_SESSION ||--o{ CHAT_MESSAGE : turns

    USER {
        Long id PK
        String username UK
        String email UK
        Boolean enabled
        Boolean emailVerified
        Integer tokenVersion
        String role
    }
    USER_PROFILE {
        Long id PK
        Long user_id UK
    }
    RESUME {
        Long id PK
        String checksum
        String storageKey UK
        Boolean highPriority
    }
    JOB {
        Long id PK
        Long user_id
        String sourceUrlHash UK_with_user
    }
    CHAT_SESSION {
        Long id PK
        Long job_id UK
        Long user_id
    }
```

## Domain ownership

| Domain | Owner module | Tables |
| --- | --- | --- |
| Identity | auth | `users`, `refresh_token`, `email_verification`, `password_reset_token` |
| Career profile | user | `user_profiles`, `work_experience`, `education`, `project`, additional-info, profile-link tables |
| Resumes | user | `resumes`, `resume_parsed_data` |
| Job notebook | jobs | `jobs`, `job_skills` |
| Job chat | chatassistant | `chat_sessions`, `chat_messages` |
| Extraction | — | **No tables.** Preview only |

## Auth

- **User:** unique `username` and `email`. Default `Role.USER`. New accounts `enabled=false`, `emailVerified=false`. `tokenVersion` starts at 0; incremented on logout-all and password reset.
- **RefreshToken:** unique token hash, `revoked`, `replacedByToken` for rotation chain, `expiresAt`.
- **EmailVerification:** OTP hash, expiry, `verified`, `failedAttempts`.
- **PasswordResetToken:** unique token hash, `used`, `usedAt`.

Hourly `AuthTokenCleanupJob` deletes expired/used rows. That is housekeeping, not business expiry enforcement (expiry is still checked on use).

## User profile and children

- **UserProfile:** one-to-one with `User` (`user_id` unique). Headline, summary, technical skills.
- Child rows (`WorkExperience`, `Education`, `Project`, `AdditionalProfileInformation`, `ProfileLink`): many-to-one profile. Application cap `user.profile.max-child-items` (default 20) per collection — not a DB unique constraint on count.
- Identity fields on profile **responses** are copied from auth `User`; they are not edited in this module.

## Resumes and parse

- **Resume:** belongs to profile. Unique `(user_profile_id, checksum)`. Unique `storageKey`. `highPriority` (column `is_primary`). User delete **hard-deletes** so the checksum can be reused.
- **ResumeParsedData:** one-to-one `resume_id`. Status `PENDING` / `COMPLETED` / `FAILED`. Stores `rawText`, section JSON, contact-ish fields, attempt count, parser version.

Object **bytes** live in MinIO, not in MySQL.

## Jobs

- **JobEntity:** many-to-one `User`. Required `sourceUrl`, `sourceUrlHash` (SHA-256 of canonical URL), `originalDescription`, `title`, `company`.
- Uniqueness: `uk_job_user_source_url_hash` on `(user_id, source_url_hash)` — same posting twice for one user is rejected; different users may save the same URL.
- **job_skills:** element collection of skill strings.

Deleting a job cascades the chat session (`ON DELETE CASCADE` on `chat_sessions.job_id`).

## Chat

- **ChatSession:** unique `job_id` (one chat per job). Denormalized `user_id` for listing. `chatTitle` is not unique.
- **ChatMessage:** unique `(chat_session_id, turn_number)`. Append-only turns (`user_prompt` + `ai_response`). Cascade delete with session.

## Cross-module reads (no extra tables)

- Manual/automated extraction: `JobRepository` exists-check by user + URL hash.
- AI: loads the caller’s resume parse and/or job description.
- Chat: loads owned `JobEntity` then writes chat tables.

## Transactions (system-level)

- Auth writes commit before sending email (after-commit mail).
- Chat: AI call is **outside** the DB transaction; then persist the turn. Concurrent first-send uses uniqueness + `ChatConflictException` (409).
- Resume upload persists metadata then queues async parse on a dedicated executor.

## What is not in the database

LLM transcripts for `POST /api/v1/ai/chat` are not stored. Extraction previews may sit in Redis/memory for ~3 minutes only.

Module-level field lists: `*/docs/DATABASE.md` under each package.

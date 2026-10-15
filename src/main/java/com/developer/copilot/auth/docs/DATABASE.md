# Auth Database

Auth persists six JPA entities in MySQL. Table names and columns come from the entity mappings. Schema creation/update follows the application’s `spring.jpa.hibernate.ddl-auto` (typically `update` in local properties). There are no auth-specific Flyway/Liquibase migrations in this package.

Production must apply the SQL at the bottom **before** a deploy that relies on a nullable `username` or `password`. Hibernate `update` does not reliably drop `NOT NULL` on existing columns. Production `ddl-auto` should stay `validate` or `none`.

## Relationship overview

```mermaid
erDiagram
    users ||--o{ user_identities : has
    users ||--o{ email_verification : has
    users ||--o{ password_setup_otp : has
    users ||--o{ password_reset_token : has
    users ||--o{ refresh_token : has

    users {
        bigint id PK
        varchar username UK "nullable"
        varchar full_name
        varchar email UK
        varchar password "nullable"
        varchar role
        boolean enabled
        boolean email_verified
        int token_version
        datetime created_at
        datetime updated_at
    }

    user_identities {
        bigint id PK
        bigint user_id FK
        varchar provider
        varchar provider_subject
        varchar provider_email
        datetime created_at
        datetime updated_at
    }

    password_setup_otp {
        bigint id PK
        bigint user_id FK
        varchar otp
        datetime expires_at
        boolean used
        int failed_attempts
        datetime created_at
        datetime updated_at
    }

    email_verification {
        bigint id PK
        bigint user_id FK
        varchar otp
        datetime expires_at
        boolean verified
        int failed_attempts
        datetime created_at
        datetime updated_at
    }

    password_reset_token {
        bigint id PK
        varchar token UK
        bigint user_id FK
        datetime expires_at
        boolean used
        datetime used_at
        datetime created_at
        datetime updated_at
    }

    refresh_token {
        bigint id PK
        varchar token UK
        bigint user_id FK
        datetime expires_at
        boolean revoked
        varchar replaced_by_token
        datetime created_at
        datetime updated_at
    }
```

All six entities extend `BaseEntity`: `createdAt` (`created_at`, not updatable) and `updatedAt` (`updated_at`), filled by Spring Data JPA auditing (`@EnableJpaAuditing` in common `JpaConfig`). The auth `Clock` bean is UTC; auditing timestamps use the JPA auditing clock (application default) unless configured otherwise.

## `users` (`User`)

Identity record for the whole application, not only auth HTTP.

| Field | Constraints / meaning |
| --- | --- |
| `id` | Identity PK. JWT `sub`. |
| `username` | Length 50. Unique. Stored lowercase. **Nullable** until Google username completion. MySQL unique allows multiple NULLs. Password registration always sets it. |
| `fullName` | Column `full_name`, required, length 100. |
| `email` | Required, length 255. Unique. Stored lowercase. Login key. |
| `password` | BCrypt hash, length 255. **Nullable** for Google-only accounts. Never mapped to API responses. |
| `role` | Enum string, default `USER`. Also `ADMIN`. |
| `enabled` | Default `false`. Must be true to authenticate. |
| `emailVerified` | Column `email_verified`. Default `false`. |
| `tokenVersion` | Column `token_version`. Default `0`. Compared to JWT claim `tv`. |

**Lifecycle:** insert on register (`enabled`/`emailVerified` false, `tokenVersion` 0, `Role.USER`, username and password set). Verify-email sets both flags true. Google sign-in inserts `enabled`/`emailVerified` true, `Role.USER`, and null username/password when the email is new. `complete-username` sets the username. Password setup or reset stores a BCrypt hash. Reset password, password-setup confirm, and logout-all increment `tokenVersion`. There is no delete-user flow in auth. Onboarding is complete exactly when `username` is not null. There is no separate flag.

**Uniqueness:** DB unique constraints on `username` and `email`. Register also checks `existsByUsername` / `existsByEmail` and treats `DataIntegrityViolationException` on save as a silent duplicate.

## `user_identities` (`UserIdentity`)

Google subject link. The login key is `(provider, provider_subject)`, not email.

| Field | Meaning |
| --- | --- |
| `provider` | Enum string. v1 value: `GOOGLE`. |
| `providerSubject` | Column `provider_subject`. Google `sub`, stored as a string, max 255. |
| `providerEmail` | Column `provider_email`. Email observed at the latest sign-in. The account email on `users` is not rewritten from this. |

Unique: `uk_user_identities_provider_subject` on `(provider, provider_subject)`. Index: `idx_user_identities_user` on `user_id`. Foreign key `user_id` → `users.id`.

A race on that unique key re-reads the identity and continues as the existing user. A race that finds the email already on `users` without this subject returns 409 and does not attach the Google subject.

## `password_setup_otp` (`PasswordSetupOtp`)

Separate from registration `email_verification` so a password-setup code cannot verify a new account.

| Field | Meaning |
| --- | --- |
| `otp` | HMAC-SHA256 hex of the 6-digit code (length 64). |
| `expiresAt` | Default now + `otpExpiryMinutes` (10). |
| `used` | Set true when the code succeeds. |
| `failedAttempts` | Incremented on HMAC mismatch. Cap `maxOtpAttempts` (5). |

Index: `idx_password_setup_otp_user` on `user_id`. Issuing a code deletes previous rows for that user. Hourly cleanup deletes expired or used rows.

## `email_verification` (`EmailVerification`)

| Field | Meaning |
| --- | --- |
| `otp` | HMAC-SHA256 hex of the 6-digit code (length 64), not the plaintext. |
| `expiresAt` | Default now + `otpExpiryMinutes` (10). |
| `verified` | Set true when the code succeeds. |
| `failedAttempts` | Incremented on HMAC mismatch. Cap `maxOtpAttempts` (5). |

Index: `idx_email_verification_user` on `user_id`.

**Lifecycle:** issuing an OTP deletes all rows for that `user_id` then inserts one. Verify reads the latest row by user email with a pessimistic write lock. Hourly job deletes expired or already-verified rows.

## `password_reset_token` (`PasswordResetToken`)

| Field | Meaning |
| --- | --- |
| `token` | SHA-256 hex of the emailed UUID. Unique. |
| `expiresAt` | Default now + `resetExpiryMinutes` (15). |
| `used` / `usedAt` | Set when reset succeeds. |

Indexes: `idx_password_reset_token` on `token`; `idx_password_reset_user_used` on `user_id, used`.

**Lifecycle:** forgot-password deletes unused tokens for the user, then inserts one. Reset marks used. Hourly job deletes used or expired rows.

## `refresh_token` (`RefreshToken`)

| Field | Meaning |
| --- | --- |
| `token` | SHA-256 hex of the UUID given to the client. Unique. |
| `expiresAt` | Default now + `refreshExpiryDays` (30). |
| `revoked` | True after rotation, logout, logout-all, reset, reuse detection, or session cap. |
| `replacedByToken` | Hash of the successor token after rotation. Null for login-created tokens and logout-revoked tokens. |

Indexes: `idx_refresh_token_token`; `idx_refresh_token_user_revoked` on `user_id, revoked`.

**Lifecycle:**

- Login: maybe revoke oldest active tokens, insert new row (`revoked=false`, `replacedByToken=null`).
- Refresh: lock row by hash; mark revoked; insert successor; set `replacedByToken` to the new hash.
- Logout: set `revoked=true` on that row only.
- Logout-all / reset / reuse of a rotated token: revoke all `revoked=false` rows for the user.

Hourly job deletes revoked or expired rows.

## Important queries and transactions

Auth use cases on `AuthServiceImpl` are `@Transactional`. Mail is scheduled `afterCommit` so SMTP runs only if the transaction succeeds.

| Operation | Persistence behavior |
| --- | --- |
| Register | Save user; delete OTP by user id; save OTP. |
| Verify OTP | Lock latest verification by email; save verification + user. |
| Refresh | `findByTokenForUpdate`; save old + new refresh; maybe `saveAll` on family revoke. |
| Login success | `findAllByUserIdAndRevokedFalseOrderByCreatedAtAsc` for the cap; save new refresh. |
| Cleanup job | Bulk JPQL deletes; `@Transactional` on the job method. |

`UserRepository.findByUsername` exists but is unused by `AuthServiceImpl` (register uses `existsByUsername`).

## What is not stored

- Access JWTs (web and browser-extension) are not stored. Invalidation is `tokenVersion` + expiry.
- Redis does not persist users or tokens.
- Plain OTP, refresh UUID, and reset UUID are not stored.

## Other packages

`CurrentUserService` and the JWT filter load this `User` entity. Profile/resume tables in the user package are separate; they are not documented here.

## Production SQL

Apply this before the application version that expects nullable credentials. Local `ddl-auto=update` may create new tables and still leave existing `username` / `password` columns `NOT NULL`.

```sql
ALTER TABLE users MODIFY username VARCHAR(50) NULL;
ALTER TABLE users MODIFY password VARCHAR(255) NULL;

CREATE TABLE user_identities (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_subject VARCHAR(255) NOT NULL,
  provider_email VARCHAR(255),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_identities_provider_subject (provider, provider_subject),
  KEY idx_user_identities_user (user_id),
  CONSTRAINT fk_user_identities_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE password_setup_otp (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  otp VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used BIT(1) NOT NULL,
  failed_attempts INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_password_setup_otp_user (user_id),
  CONSTRAINT fk_password_setup_otp_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

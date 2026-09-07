# Jobs Database

Jobs persists to MySQL through JPA. Schema updates in the example configuration use `spring.jpa.hibernate.ddl-auto=update`. There is no jobs-specific Flyway/Liquibase changelog in this project.

## Tables and relationships

```mermaid
erDiagram
    users ||--o{ jobs : owns
    jobs ||--o{ job_skills : has

    users {
        bigint id PK
        string username
        string email
    }

    jobs {
        bigint id PK
        bigint user_id FK
        varchar source_url
        varchar source_url_hash
        text original_description
        text description
        varchar title
        varchar company
        varchar location
        varchar employment_type
        varchar work_mode
        varchar experience
        varchar salary
        varchar department
        varchar education
        varchar industry
        varchar source_platform
        datetime created_at
        datetime updated_at
    }

    job_skills {
        bigint job_id FK
        varchar skill
    }
```

`users` is owned by the auth module. Jobs only stores `user_id`.

## `JobEntity` (`jobs`)

Purpose: one saved posting for one user.

| Concern | Implementation |
|---|---|
| Identity | `Long id`, `GenerationType.IDENTITY` |
| Owner | `@ManyToOne(LAZY)` `User`, column `user_id`, `nullable = false` |
| Source URL | `sourceUrl`, length 2000, mandatory. Canonical form after normalization |
| Dedupe key | `sourceUrlHash`, length 64, mandatory. SHA-256 hex of the canonical URL |
| Original text | `originalDescription`, `TEXT`, mandatory. What the user pasted |
| Cleaned text | `description`, `TEXT`, optional |
| Core fields | `title` and `company` mandatory (`nullable = false`) |
| Optional strings | location, employmentType, workMode, experience, salary, department, education, industry, sourcePlatform |
| Audit | `createdAt` / `updatedAt` from `BaseEntity` and `@EnableJpaAuditing` |
| Uniqueness | `@UniqueConstraint` name `uk_job_user_source_url_hash` on `(user_id, source_url_hash)` |

JPA default string length is 255 unless a column sets otherwise. That matches bean-validation max lengths on title/company (255) and the shorter optional fields.

`source_url_hash` exists because InnoDB cannot place a practical unique index on `VARCHAR(2000)`. Equality of postings is “same user + same hash”, not raw pasted URL.

Lifecycle:

- **Insert** — create path sets owner, fields, canonical URL, hash, then `save`
- **Update** — dirty fields + optional URL re-hash; `updatedAt` changes via auditing
- **Delete** — `jobRepository.delete(job)`; element-collection skills are removed with the job

There is no soft delete and no expiry column.

## `job_skills`

`@ElementCollection` with `@CollectionTable(name = "job_skills", joinColumns = job_id)` and column `skill`. Fetch is `LAZY`. List queries that need skills use `@EntityGraph(attributePaths = "skills")`.

There is no uniqueness on skill text and no documented maximum list size in code — only each skill string `@Size(max = 255)`.

On `PUT`, omitted or null `skills` **clears** the collection. On general `PATCH`, omitted `skills` **leaves** the collection; an explicit empty array clears it.

## Repository operations

| Method | Use |
|---|---|
| `findByIdAndUserId` | All get/update/delete-by-id paths |
| `findAllByUserId` | List without search (entity graph includes skills) |
| `searchJobsByUserId` | List with prepared search string |
| `existsByUserIdAndSourceUrlHash` | Duplicate check on create |
| `existsByUserIdAndSourceUrlHashAndIdNot` | Duplicate check on update |

Search JPQL: `LOWER(field) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\'` on title, company, location, industry, `sourcePlatform`. Bind parameters prevent SQL injection; escaping prevents wildcard abuse.

## Transactions

| Methods | Annotation |
|---|---|
| `getAllJobs`, `getJobById` | `@Transactional(readOnly = true)` |
| create, update, patch, delete, all field updates | `@Transactional` |

`saveJob` catches `DataIntegrityViolationException`. If the cause text contains `uk_job_user_source_url_hash` (case-insensitive), it becomes `DuplicateJobException`. Any other integrity failure is rethrown; the global handler then returns a generic `409` without SQL detail.

## Integrations that touch these tables

- **Job extraction** — `existsByUserIdAndSourceUrlHash` only (no insert through jobs service)
- **AI / chat assistant** — `findByIdAndUserId` to load text for prompts
- **Chat sessions** — separate `chat_sessions.job_id` with `ON DELETE CASCADE` in the chat-assistant entity. Deleting a job can delete that chat session at the database

Jobs does not map chat tables and does not write them.

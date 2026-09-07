# Flows

This document covers the workflows that are non-trivial in the current `user` service. Trivial GET-by-id after a profile lookup is omitted. Every diagram matches the implementation.

Related deep dives: [RESUME-PARSING.md](USER-SERVICE-SPECIFIC-DOCS/RESUME-PARSING.md), [RESUME-STORAGE-AND-LIFECYCLE.md](USER-SERVICE-SPECIFIC-DOCS/RESUME-STORAGE-AND-LIFECYCLE.md), [PROFILE-AND-CHILD-COLLECTIONS.md](USER-SERVICE-SPECIFIC-DOCS/PROFILE-AND-CHILD-COLLECTIONS.md), [RATE-LIMITING.md](USER-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

## 1. Typical authenticated request

All `/api/v1/users/**` and `/api/v1/internal/resumes/**` routes require authentication. The JWT filter only populates the security context when the token is valid **and** the user row is enabled and email-verified. Otherwise the chain continues unauthenticated and Spring Security returns `401` with `"Unauthorized."`

```mermaid
sequenceDiagram
    participant Client
    participant JWT as JwtAuthenticationFilter
    participant RL as User / internal rate-limit filters
    participant Ctrl as Controller
    participant Svc as Service
    participant DB as MySQL
    participant GEH as GlobalExceptionHandler

    Client->>JWT: Authorization Bearer access JWT
    alt Missing, invalid, disabled, or unverified
        JWT-->>Client: 401 Unauthorized
    else Authenticated
        JWT->>RL: Principal present
        alt Budget exceeded
            RL-->>Client: 429 plus Retry-After
        else Allowed
            RL->>Ctrl: Validated DTO or multipart
            Ctrl->>Svc: CurrentUserService.getCurrentUser
            Svc->>DB: Profile-scoped query
            alt Business or validation failure
                Svc->>GEH: Service exception
                GEH-->>Client: ApiResponse success false
            else Success
                Ctrl-->>Client: 200/201 ApiResponse or PDF bytes
            end
        end
    end
```

Internal parse requests also pass `InternalApiKeyFilter` (after JWT, before rate limits). A missing or wrong key is `401` with `"Invalid or missing internal service key."` and is **not** counted toward rate limits.

## 2. Create profile, then use the rest of the API

Resume upload and every child collection require an existing profile. Creating a second profile is `409`.

```mermaid
flowchart TD
    A[POST /api/v1/users/profile] --> B{Profile exists for this user?}
    B -->|yes| C[409 DuplicateUserProfileException]
    B -->|no| D[Insert user_profiles]
    D --> E[201 with nested empty children]
    E --> F[POST children and/or POST /resumes]
    F --> G{Profile still exists?}
    G -->|no| H[404 User profile not found]
    G -->|yes| I[Continue]
```

`fullName` and `email` in the response come from the auth `User`, not from the request body. All three profile fields are optional.

## 3. Replace profile scalars (PUT)

PUT is a full replace of `headline`, `summary`, and `technicalSkills`. Jackson binds missing JSON keys to `null`, and the service writes those values onto the entity. Child collections are **not** in this body and are unchanged.

```mermaid
flowchart LR
    Req["PUT body headline / summary / technicalSkills"] --> Write[Set fields including null]
    Write --> Save[Save user_profiles]
    Save --> Nested[Reload all children for the response]
```

There is no PATCH for these three fields.

## 4. Add a child row with a cap

Each collection (experiences, educations, projects, additional-info, links) has its own table and a max of `user.profile.max-child-items` (default 20). Add operations lock the profile row so two concurrent POSTs cannot both pass the count check.

```mermaid
flowchart TD
    P[POST child] --> Lock[Pessimistic lock on user_profiles]
    Lock --> Count{count >= max?}
    Count -->|yes| Cap[400 ProfileItemLimitExceededException]
    Count -->|no| Val{Bean Validation}
    Val -->|fail| Bad[400 field messages]
    Val -->|ok| Ins[Insert child row]
    Ins --> Created[201 child response]
```

Updates and deletes load the child with `findByIdAndUserProfile`. A row that exists but belongs to another user is `404` with the collection's not-found message.

## 5. Resume upload

Upload is the most involved public write. It requires a profile, holds a write lock, and never leaves an orphan object if the database insert fails.

```mermaid
flowchart TD
    U[POST multipart field file] --> RL{Upload rate limit IP then user}
    RL -->|deny| R429[429]
    RL -->|allow| Prof{Profile exists?}
    Prof -->|no| NF[404]
    Prof -->|yes| Cap{Active resumes >= max?}
    Cap -->|yes| L400[400 resume limit]
    Cap -->|no| File{Empty, not PDF, oversize, or filename too long?}
    File -->|yes| Inv[400 InvalidResumeException]
    File -->|no| Store[MinIO upload under users/id/resumes]
    Store --> Dup{Checksum already on an active resume?}
    Dup -->|yes| DelObj[Delete new object] --> D409[409 Duplicate resume]
    Dup -->|no| First{Zero active resumes?}
    First -->|yes| Primary[highPriority true]
    First -->|no| NotP[highPriority false]
    Primary --> Save[saveAndFlush resumes]
    NotP --> Save
    Save -->|DataIntegrityViolation| Comp[Delete object] --> D409
    Save -->|other RuntimeException| Comp2[Delete object] --> Err
    Save -->|ok| Pend[Insert PENDING parsed row]
    Pend --> After[After commit: queue parseAndPersist]
    After --> C201[201 resumeId]
```

List and upload responses do not include parse status. The client learns parse state only through the internal API (or in-process `ResumeParsingService`).

## 6. Delete resume and promote primary

Delete is a hard delete. The unique checksum constraint is on `(user_profile_id, checksum)` without an `active` flag, so a soft-delete would block re-upload of the same PDF.

```mermaid
flowchart TD
    D[DELETE /resumes/id] --> RL{Delete rate limit}
    RL -->|deny| R429[429]
    RL -->|allow| Find[Lock profile, load active resume]
    Find -->|missing| NF[404]
    Find -->|found| PD[Delete parsed data]
    PD --> Row[Hard-delete resume row]
    Row --> Was{Was highPriority?}
    Was -->|yes| Promo[Set most recently created remaining resume as primary]
    Was -->|no| Skip[No promotion]
    Promo --> AC[After commit: MinIO delete]
    Skip --> AC
    AC -->|storage failure| Log[Log minioDeleteFailure; HTTP still 200]
    AC -->|ok| OK[200]
```

If the deleted resume was the last one, nothing is promoted.

## 7. Set high-priority resume

`PATCH /api/v1/users/resumes/{resumeId}/high-priority` has an empty body. There is no query flag to unset primary.

The service clears `highPriority` on every active resume for the profile, then sets the selected row to `true`. It is not rate-limited.

## 8. Background parse after upload

```mermaid
sequenceDiagram
    participant US as UserServiceImpl
    participant RPS as ResumeParsingServiceImpl
    participant DB as resume_parsed_data
    participant Tx as After commit
    participant W as ResumeParsingWorker
    participant P as ResumeParser
    participant MinIO as FileStorageService

    US->>RPS: initializeAndScheduleParsing
    RPS->>DB: Insert PENDING if none
    US-->>US: Transaction commits
    Tx->>W: parseAndPersist resumeId
    W->>DB: Load resume and existing row
    alt Resume gone or already COMPLETED/FAILED
        W-->>W: Skip
    else Still PENDING
        W->>MinIO: Download PDF
        W->>P: parseWithRetry
        P-->>W: COMPLETED or FAILED
        W->>DB: persist REQUIRES_NEW
    end
```

If the executor queue is full (`AbortPolicy`), the worker is not submitted and the row stays `PENDING`. An internal GET then either waits (`422` still in progress) or, if there is no matching-version row, may parse on demand.

## 9. Internal parsed-resume read

Callers send the **user's** JWT (ownership) and the internal service key (calling service). `resumeId == null` means the high-priority active resume.

```mermaid
flowchart TD
    G[GET parsed] --> Auth{JWT plus internal key}
    Auth -->|fail| U401[401]
    Auth -->|ok| RL{User parse limit then common internal hallway}
    RL -->|deny| R429[429]
    RL -->|allow| Prof{Profile?}
    Prof -->|no| N404[404]
    Prof -->|yes| Res{Resume id or high-priority active?}
    Res -->|missing or other owner| R404[404 Resume not found]
    Res -->|found| Row[Load resume_parsed_data]
    Row --> Ver{Row exists and parserVersion matches config?}
    Ver -->|COMPLETED| OK[200 mapped response]
    Ver -->|FAILED| F422[422 lastError]
    Ver -->|PENDING| P422[422 still in progress]
    Ver -->|no usable row| File{Object exists?}
    File -->|no| R404
    File -->|yes| OD[parseWithRetry on executor with timeout]
    OD -->|timeout / busy / interrupt| T422[422]
    OD -->|FAILED| PersistF[persistAsync] --> F422
    OD -->|COMPLETED| Map[Map including contextText]
    Map --> Persist[persistAsync] --> OK
```

On-demand persistence is best-effort: the HTTP response is built first; a full persist queue logs a warning and does not fail the request.

A stored row whose `parserVersion` does not match `resume.parsing.parser-version` is treated as unusable and is re-parsed.

## 10. Delete profile

DELETE profile is a cascade owned by this service, not by JPA `cascade = ALL` on the profile entity.

Order: load all resumes → delete their parsed rows → collect storage keys → delete resume rows → delete every child collection → delete the profile → after commit, delete each stored PDF. Auth `users` is untouched.

Storage delete failures are logged (`minioDeleteFailure`); the API still returns `200` after a successful database commit.

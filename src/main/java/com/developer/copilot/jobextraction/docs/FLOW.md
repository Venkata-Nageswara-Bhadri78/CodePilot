# Flows

Workflows actually implemented by `jobextraction`. Each diagram matches `JobExtractionServiceImpl`, the rate-limit filter, the preview cache, and `JobExtractionAiGuard`.

## Parse request lifecycle

End-to-end path for `POST /api/v1/job-extraction/parse`.

```mermaid
sequenceDiagram
    participant C as Client
    participant JWT as JwtAuthenticationFilter
    participant RL as JobExtractionRateLimitFilter
    participant Ctrl as JobExtractionController
    participant Svc as JobExtractionServiceImpl
    participant Jobs as JobRepository
    participant Cache as JobExtractionPreviewCache
    participant Guard as JobExtractionAiGuard
    participant AI as AiService
    participant Map as JobExtractionMapper

    C->>JWT: Authorization Bearer
    alt missing/invalid JWT, disabled, or emailVerified not true
        JWT-->>C: 401 Unauthorized.
    else authenticated
        JWT->>RL: POST /api/v1/job-extraction/parse
        alt parse budget exceeded
            RL-->>C: 429 + Retry-After
        else allowed
            RL->>Ctrl: @Valid body
            alt Bean Validation or malformed JSON
                Ctrl-->>C: 400
            else valid
                Ctrl->>Svc: extractJobInfo
                Svc->>Svc: CurrentUserService
                alt emailVerified not TRUE
                    Svc-->>C: 403
                else
                    Svc->>Svc: normalizeStrict + sha256Hex
                    alt invalid URL
                        Svc-->>C: 400
                    else
                        Svc->>Jobs: existsByUserIdAndSourceUrlHash
                        alt already saved
                            Svc-->>C: 409
                        else new for this user
                            Svc->>Cache: computeIfAbsent
                            alt cache hit
                                Cache-->>Svc: cached preview
                            else miss
                                Cache->>Guard: call AI
                                Guard->>AI: extractJobInfo
                                AI-->>Guard: JobExtractionAiResponse
                                Guard-->>Cache: 
                                Cache->>Map: toResultResponse
                            end
                            Svc-->>C: 200 preview
                        end
                    end
                end
            end
        end
    end
```

Notes:

- `200` with `requiresManualReview: true` is still success. The client should highlight title/company before save.
- The AI invocation is outside any service `@Transactional`.
- Try-it-out against a live model can take up to the AI timeout (default 60 seconds).

## URL, hash, and duplicate decision

Paid extraction is skipped when the URL is unusable or this user already has the posting.

```mermaid
flowchart TD
    A[Raw sourceUrl] --> B{normalizeStrict}
    B -->|InvalidJobUrlException| C[400 Job URL must be a valid absolute http or https link]
    B -->|canonical URL| D[sha256Hex]
    D --> E{existsByUserIdAndSourceUrlHash currentUser.id, hash}
    E -->|true| F[409 This post was already added to your records]
    E -->|false| G[Continue to cache / AI]
```

Another user may already have the same hash. That does not produce `409` for the caller. See [URL-AND-DEDUPLICATION.md](JOBEXTRACTION-SERVICE-SPECIFIC-DOCS/URL-AND-DEDUPLICATION.md).

## Preview cache and in-flight coalescing

`computeIfAbsent(userId, urlHash, loader)`:

```mermaid
flowchart TD
    A[computeIfAbsent] --> B{get Redis then memory}
    B -->|hit| C[Return cached preview]
    B -->|miss| D{inFlight.putIfAbsent}
    D -->|another thread already loading| E[await same Future]
    D -->|this thread owns the load| F[Run AI + mapper]
    F -->|success| G[put TTL 3 minutes]
    F -->|RuntimeException| H[completeExceptionally and rethrow]
    G --> I[complete Future]
```

Implications:

- Same user + same canonical URL within 3 minutes → one AI call.
- Different users → different cache keys.
- Cache key does **not** include `rawJobText`. A second paste for the same URL in the TTL window returns the first preview (and the first `originalDescription`).
- Duplicate check still runs on every request, including cache hits. Saving via `POST /api/v1/jobs` after a preview makes the next parse a `409`.

Redis get/put failures log a warning and fall back to memory on that instance.

## AI guard: circuit and bulkhead

Wraps only the `aiService.extractJobInfo` call.

```mermaid
flowchart TD
    A[aiGuard.call] --> B{now less than openUntilEpochMs?}
    B -->|yes| C[503 circuit: temporarily unavailable]
    B -->|no| D{tryAcquire semaphore 5}
    D -->|no| E[503 bulkhead: AI service is busy]
    D -->|yes| F[callable.call]
    F -->|success| G[consecutiveFailures = 0]
    F -->|AiServiceException or other Exception| H[increment failures]
    H --> I{failures >= 3?}
    I -->|yes| J[open 30s, reset counter]
    I -->|no| K[propagate exception]
    F --> L[release permit]
    G --> L
    H --> L
```

- `JobExtractionAiUnavailableException` from an inner call is rethrown without counting as a new circuit failure.
- After three `AiServiceException`s, the **fourth** call fails fast with `503` and does not hit the provider.
- Guard state is **per JVM**. Multiple ECS tasks each have their own circuit and semaphore.

Provider timeouts and parse failures surface as `AiServiceException` → `502`. See [AI-EXTRACTION.md](JOBEXTRACTION-SERVICE-SPECIFIC-DOCS/AI-EXTRACTION.md).

## Rate-limit flow

Runs after JWT so a stolen token is limited by user id, not only by IP.

```mermaid
flowchart TD
    A[POST under /api/v1/job-extraction] --> B{parsePerMinute > 0?}
    B -->|no| C[Pass through]
    B -->|yes| D[consume parse-ip, client IP]
    D -->|denied| E[429 generic message + Retry-After]
    D -->|allowed| F{CustomUserDetails with user.id?}
    F -->|no| G[Continue filter chain]
    F -->|yes| H[consume parse-user, user id]
    H -->|denied| E
    H -->|allowed| G
```

GET (there is no GET API) and other URL prefixes are not limited by this filter. Same JSON body for IP and user exhaustion. Details: [RATE-LIMITING.md](JOBEXTRACTION-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

## Client save flow (outside this module)

This service stops at preview. The intended product sequence:

```mermaid
sequenceDiagram
    participant C as Client
    participant JE as jobextraction
    participant J as jobs

    C->>JE: POST /parse sourceUrl + rawJobText
    JE-->>C: 200 data including canonical sourceUrl
    Note over C: User edits fields. If requiresManualReview, fill title/company.
    C->>J: POST /api/v1/jobs JobRequest omit requiresManualReview
    J-->>C: 201 saved job
```

Field mapping: [PREVIEW-AND-SAVE.md](JOBEXTRACTION-SERVICE-SPECIFIC-DOCS/PREVIEW-AND-SAVE.md).

## Authentication vs email verification

```mermaid
flowchart TD
    A[Bearer JWT] --> B{user exists, enabled, emailVerified, token valid?}
    B -->|no| C[401 Unauthorized. Filter never sets Authentication]
    B -->|yes| D[Controller / service]
    D --> E{Boolean.TRUE.equals emailVerified?}
    E -->|no| F[403 Please verify your email...]
    E -->|yes| G[Continue parse]
```

In production filter behavior, unverified users normally never reach the service (`401`). The `403` path is implemented and tested at the service/controller-advice layer. Do not document `403` as the only unverified-email outcome.

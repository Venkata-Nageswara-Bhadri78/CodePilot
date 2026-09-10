# Flows

Workflows actually implemented by `automatedjobextraction`. Each diagram matches `AutomatedJobExtractionServiceImpl`, the rate-limit filter, SSRF, fetch, extraction pipeline, extracted-text cache, and `ManualJobExtractionGateway`.

## Parse request lifecycle

End-to-end path for `POST /api/v1/automated-job-extraction/parse`.

```mermaid
sequenceDiagram
    participant C as Client
    participant JWT as JwtAuthenticationFilter
    participant RL as AutomatedJobExtractionRateLimitFilter
    participant Ctrl as AutomatedJobExtractionController
    participant Svc as AutomatedJobExtractionServiceImpl
    participant Ssrf as SsrfProtectionService
    participant Cache as ExtractedJobContentCache
    participant Fetch as JobPageFetcher
    participant Pipe as JobExtractionPipeline
    participant Gw as ManualJobExtractionGateway
    participant Manual as JobExtractionService

    C->>JWT: Authorization Bearer
    alt missing/invalid JWT, disabled, or emailVerified not true
        JWT-->>C: 401 Unauthorized.
    else authenticated
        JWT->>RL: POST /api/v1/automated-job-extraction/parse
        alt parse budget exceeded
            RL-->>C: 429 + Retry-After
        else allowed
            RL->>Ctrl: @Valid body
            alt Bean Validation or malformed JSON
                Ctrl-->>C: 400
            else valid
                Ctrl->>Svc: extractFromUrl
                Svc->>Svc: CurrentUserService
                alt emailVerified not TRUE
                    Svc-->>C: 403
                else
                    Svc->>Svc: normalizeStrict
                    alt InvalidJobUrlException
                        Svc-->>C: 400 format message
                    else
                        Svc->>Ssrf: validate canonical URI
                        alt SSRF reject
                            Svc-->>C: 400 INVALID JOB URL
                        else
                            Svc->>Cache: computeIfAbsent
                            alt cache hit
                                Cache-->>Svc: extracted text
                            else miss
                                Cache->>Fetch: fetch
                                Fetch-->>Cache: FetchedJobPage
                                Cache->>Pipe: extractJobText
                                Pipe-->>Cache: labeled text
                            end
                            Svc->>Gw: parseExtractedContent
                            Gw->>Manual: extractJobInfo
                            Manual-->>Gw: preview or error
                            Svc-->>C: 200 preview
                        end
                    end
                end
            end
        end
    end
```

Notes:

- `200` with `requiresManualReview: true` is still success.
- Duplicate `409` is produced **inside** the gateway, **after** a successful fetch and extract on a cache miss. The automated service does not query `JobRepository` itself.
- Fetch and AI are outside any service `@Transactional`.
- Try-it-out against a live model can take the fetch timeouts plus the AI timeout (AI default 60 seconds).

## URL, SSRF, and fetch vs invalid-job decision

Paid extraction is skipped when the URL is unusable as a format, as an SSRF target, or as a job posting.

```mermaid
flowchart TD
    A[Raw sourceUrl] --> B{normalizeStrict}
    B -->|InvalidJobUrlException| C[400 Job URL must be a valid absolute http or https link]
    B -->|canonical URL| D{SsrfProtectionService.validate}
    D -->|private / blocked / bad DNS| E[400 INVALID JOB URL]
    D -->|public http or https| F[sha256Hex]
    F --> G[Fetch and extract or cache]
    G -->|quality fail, 404, captcha, listing| E
    G -->|labeled text| H[ManualJobExtractionGateway]
    H -->|existsByUserIdAndSourceUrlHash| I{already saved?}
    I -->|true| J[409 This post was already added to your records]
    I -->|false| K[AI preview]
```

Another user may already have the same hash. That does not produce `409` for the caller. See [PREVIEW-AND-SAVE.md](AUTOMATEDJOBEXTRACTION-SERVICE-SPECIFIC-DOCS/PREVIEW-AND-SAVE.md).

`javascript:` and missing hosts fail at `normalizeStrict` (format message). `http://localhost/...` is a valid absolute URL and fails at SSRF (`INVALID JOB URL`). Tests lock this split in.

## Extracted-text cache and in-flight coalescing

`computeIfAbsent(userId, urlHash, loader)`:

```mermaid
flowchart TD
    A[computeIfAbsent] --> B{get Redis then memory}
    B -->|hit| C[Return cached labeled text]
    B -->|miss| D{inFlight.putIfAbsent}
    D -->|another thread already loading| E[await same Future]
    D -->|this thread owns the load| F[fetch then pipeline]
    F -->|success| G[put TTL 3 minutes]
    F -->|RuntimeException| H[completeExceptionally and rethrow]
    G --> I[complete Future]
```

Implications:

- Same user + same canonical URL within 3 minutes → one outbound fetch.
- Different users → different cache keys.
- Cache key does **not** include live HTML. A second parse for the same URL in the TTL window returns the first extracted text even if the career site changed.
- The gateway still runs on cache hits, so AI preview cache and duplicate check still apply. Tests assert fetch once and gateway twice on a second call.
- Duplicate check therefore still runs on every request. Saving via `POST /api/v1/jobs` after a preview makes the next parse a `409` without a second fetch if the text cache is still warm.

Redis get/put failures log a warning and fall back to memory on that instance.

## Page fetch, redirects, and Workday CXS

```mermaid
flowchart TD
    A[JobPageFetcher.fetch] --> B{fetchGuard circuit or bulkhead}
    B -->|closed or full| C[503 The job page could not be retrieved]
    B -->|acquired| D[SSRF validate]
    D --> E[HTTP GET no auto-redirect]
    E --> F{status}
    F -->|301 302 303 307 308| G{depth less than maxRedirects}
    G -->|no or bad Location| H[400 INVALID JOB URL]
    G -->|yes| I[resolve Location, SSRF next hop]
    I --> E
    F -->|401 403 404 407 410 451 or other 4xx| H
    F -->|5xx or other non-2xx| J[502 fetch failure]
    F -->|2xx| K{Workday host and SPA widget?}
    K -->|yes| L[GET same-host CXS JSON]
    L -->|200 jobPostingInfo| M[use JSON body]
    L -->|fail| N[keep original body]
    K -->|no| N
    M --> O{captcha without job markers?}
    N --> O
    O -->|yes| H
    O -->|no| P[FetchedJobPage]
```

`InvalidAutomatedJobUrlException` from fetch does not open the fetch circuit. Timeouts, oversized bodies, and 5xx do.

Details: [SSRF-AND-PAGE-FETCH.md](AUTOMATEDJOBEXTRACTION-SERVICE-SPECIFIC-DOCS/SSRF-AND-PAGE-FETCH.md).

## Extraction pipeline

```mermaid
flowchart TD
    A[FetchedJobPage] --> B{blank body?}
    B -->|yes| C[400 INVALID JOB URL]
    B -->|no| D{JSON payload?}
    D -->|yes| E[wrap as JSON-LD HTML]
    D -->|no| F[parse HTML]
    E --> F
    F --> G[HtmlNoiseStripper on clone]
    G --> H[strategies by order]
    H --> I[FieldMerger first-wins scalars]
    I --> J[ExtractedJobTextFormatter]
    J --> K{JobContentQualityValidator}
    K -->|reject| C
    K -->|accept| L[labeled text to gateway]
```

A strategy that throws is skipped (`debug` log); the pipeline continues. See [EXTRACTION-PIPELINE.md](AUTOMATEDJOBEXTRACTION-SERVICE-SPECIFIC-DOCS/EXTRACTION-PIPELINE.md).

## Fetch guard: circuit and bulkhead

Wraps only the outbound fetch callable inside `JobPageFetcher.fetch`.

```mermaid
flowchart TD
    A[fetchGuard.call] --> B{now less than openUntilEpochMs?}
    B -->|yes| C[503 circuit]
    B -->|no| D{tryAcquire 8}
    D -->|no| E[503 bulkhead]
    D -->|yes| F[callable]
    F -->|success| G[reset consecutiveFailures]
    F -->|InvalidAutomatedJobUrlException| H[rethrow no circuit increment]
    F -->|AutomatedJobPageFetchException or unexpected| I[increment failures]
    I --> J{failures greater or equal 3?}
    J -->|yes| K[open 30 seconds]
```

A success resets the consecutive-failure counter. `AutomatedJobExtractionUnavailableException` itself does not count as a new fetch failure.

The **AI** circuit (`JobExtractionAiGuard`: 5 concurrent, 3 failures / 30s) still applies later inside manual extraction. A caller can pass the fetch guard and still get AI `503`.

## Gateway / manual parse (after labeled text exists)

```mermaid
sequenceDiagram
    participant Gw as ManualJobExtractionGateway
    participant M as JobExtractionServiceImpl
    participant Jobs as JobRepository
    participant PCache as JobExtractionPreviewCache
    participant AiGuard as JobExtractionAiGuard
    participant AI as AiService

    Gw->>M: extractJobInfo canonicalUrl plus labeled text
    M->>M: email verified
    M->>M: normalizeStrict plus sha256Hex
    M->>Jobs: existsByUserIdAndSourceUrlHash
    alt duplicate
        M-->>Gw: DuplicateJobException
    else new
        M->>PCache: computeIfAbsent
        alt preview cache hit
            PCache-->>M: JobExtractionResultResponse
        else miss
            PCache->>AiGuard: extractJobInfo
            AiGuard->>AI: structured extract
            AI-->>M: mapper preview
        end
    end
```

`originalDescription` on the preview is the **labeled extracted text**, not the original HTML. That is what the gateway puts in `rawJobText`.

## Rate-limit filter

See [RATE-LIMITING.md](AUTOMATEDJOBEXTRACTION-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md). POST only; IP then user; `429` written by the filter before the controller.

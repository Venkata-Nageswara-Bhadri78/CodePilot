# Flows

This document covers workflows **implemented in common**. Feature-specific create/update flows are documented in those services; they appear here only where they cross the shared kernel.

## 1. Typical authenticated request (no internal prefix)

```mermaid
sequenceDiagram
    participant C as Client
    participant S as auth SecurityFilterChain
    participant FC as Feature controller
    participant CU as CurrentUserService
    participant G as GlobalExceptionHandler

    C->>S: HTTP + Bearer JWT
    alt JWT missing or invalid
        S-->>C: 401 ApiResponse "Unauthorized."
    else JWT valid, user enabled and verified
        S->>FC: Authenticated request
        FC->>CU: getCurrentUser()
        alt Principal is CustomUserDetails with User
            CU-->>FC: User
            FC-->>C: 200 ApiResponse success
        else Missing or wrong principal
            CU-->>G: InvalidCredentialsException
            G-->>C: 401 ApiResponse "User is not authenticated."
        end
    end
```

`JsonAuthenticationEntryPoint` (auth) writes `"Unauthorized."` for security-chain failures. `CurrentUserServiceImpl` throws `InvalidCredentialsException` with `"User is not authenticated."` when a controller asks for the user but the principal is not a loaded `CustomUserDetails`.

## 2. Internal service-to-service request

Applies to every URL under `internal.api.path-prefix` (default `/api/v1/internal`). Today the only controllers on that prefix live in `user` (`InternalResumeController`). Common still runs for any future internal controller on the same prefix.

```mermaid
flowchart TD
    A[Request to /api/v1/internal/**] --> B[SecurityFilterChain JWT]
    B -->|401 Unauthorized.| Z[Response]
    B --> C{internal.api.enabled?}
    C -->|false and local/dev| E[Skip key check]
    C -->|false otherwise| D[401 same client message]
    C -->|true, key blank| D
    C -->|true, header missing or wrong| D
    C -->|true, current or previous key matches| E
    E --> F{Hallway rate limit}
    F -->|internal-key bucket exceeded| G[429 Retry-After]
    F -->|JWT user id present and user bucket exceeded| G
    F -->|allowed| H[Feature controller]
    H --> I[Feature service]
    I --> J[200 or mapped error via GlobalExceptionHandler]
    D --> Z
    G --> Z
    J --> Z
```

Every key failure uses the **same** client body: `"Invalid or missing internal service key."` Reasons (`disabled`, `unconfigured`, `missing`, `invalid`) are only tagged on the Micrometer counter `copilot.internal.auth.failure`.

Key comparison is `MessageDigest.isEqual` on UTF-8 bytes (constant-time). `internal.api.previous-key` is accepted during rotation.

Hallway limits (see [INTERNAL-API.md](COMMON-SERVICE-SPECIFIC-DOCS/INTERNAL-API.md)):

1. Bucket `internal-key`, identity `"service"`, limit `app.common.internal-key-per-minute` (default 60), window 60 seconds.
2. If the JWT principal has a user id: bucket `internal-user`, identity that id, limit `app.common.internal-user-per-minute` (default 30).

A limit of `0` disables that bucket. Non-internal paths are not limited by this filter (even if the filter instance were invoked).

## 3. Exception to HTTP response

```mermaid
flowchart TD
    TH[Throwable from controller, service, or validation] --> H{GlobalExceptionHandler match?}
    H -->|Dedicated @ExceptionHandler| M[Status + client message]
    H -->|IllegalArgumentException with known prefix| B400[400 original message]
    H -->|IllegalArgumentException otherwise| B500[500 Something went wrong.]
    H -->|Exception catch-all| B500
    M --> AR[ApiResponse success false]
    B400 --> AR
    B500 --> AR
```

Filters that already wrote the body (internal key, hallway 429) never reach this handler.

Chat assistant additionally has a controller-scoped advice (`ChatAssistantExceptionHandler`) with highest precedence for a subset of exceptions on that controller only. Auth also has `RateLimitExceptionHandler` for auth’s own `RateLimitExceededException`; `GlobalExceptionHandler` maps the same type as well.

Full status table: [ERROR-HANDLING.md](ERROR-HANDLING.md).

## 4. Object storage at startup

```mermaid
sequenceDiagram
    participant Boot as Spring startup
    participant V as StorageStartupValidator
    participant FS as FileStorageServiceImpl
    participant M as MinIO

    Boot->>V: @PostConstruct initialize()
    alt Not local/dev, remote endpoint, minioadmin or auto-create
        V-->>Boot: IllegalStateException fail boot
    else Credentials acceptable
        V->>FS: initializeStorage()
        FS->>M: bucketExists
        alt Bucket exists
            M-->>FS: true
        else Missing and autoCreateBucket
            FS->>M: makeBucket
        else Missing and auto-create off
            FS-->>Boot: IllegalStateException
        end
        V->>V: copilot.storage.boot result=success
    end
```

Laptop profiles (`local`, `dev`) skip the remote-credential rules. Loopback endpoints (`localhost`, `127.0.0.1`, `[::1]`) may still use `minioadmin` even without those profiles.

## 5. PDF upload

```mermaid
flowchart TD
    U[upload MultipartFile, folderPath] --> P[Normalize folder: slashes, trim]
    P --> S{Unsafe path?}
    S -->|.. // empty segment bad charset| IF[InvalidFileException]
    S --> O{JWT CustomUserDetails present?}
    O -->|yes and not under users/userId/| IF
    O -->|no principal or owned prefix| PDF{PDF checks}
    PDF -->|empty, non-pdf type/name, magic not %PDF| IF
    PDF --> BYTES[Read bytes, SHA-256, UUID.pdf key]
    BYTES --> PUT[MinIO putObject]
    PUT -->|success| SF[StoredFile]
    PUT -->|unexpected error| ST[StorageException]
```

Download, delete, and exists run the same path/key validation (and ownership when a JWT user is on the thread) before talking to MinIO. A missing object on download is `StorageObjectNotFoundException` (HTTP 404 `"File not found."`), not a storage outage.

## 6. Job URL canonicalization

Used by `jobs` and `jobextraction` on create/extract (`normalizeStrict` + `sha256Hex`). `normalizeLenient` is implemented and tested but has no production caller today.

```mermaid
flowchart TD
    RAW[Raw source URL] --> ST{normalizeStrict}
    ST -->|null or blank| E1["InvalidJobUrlException<br/>Job URL must not be empty."]
    ST -->|not absolute http/https| E2["InvalidJobUrlException<br/>Job URL must be a valid absolute http or https link."]
    ST --> N[Lowercase scheme and host, strip www., default ports, trailing slash except root]
    N --> Q[Drop tracking params, sort remaining query keys]
    Q --> C[Canonical string, fragment and user-info dropped]
    C --> H[sha256Hex 64-char digest]
```

Unsafe schemes (`javascript`, `data`, `file`, `vbscript`) are rejected even on the lenient path so they are never stored. Exception messages do not echo the input URL.

## 7. Redis-backed hallway counter (when enabled)

```mermaid
flowchart TD
    C[consume bucket, identity, limit, window] --> L{limit <= 0?}
    L -->|yes| P[Permit]
    L --> R{CommonRedisService bean present?}
    R -->|yes| INCR[INCR namespaced key, set TTL on first count]
    INCR -->|count <= limit| P
    INCR -->|count > limit| D[Deny with Redis TTL or window]
    INCR -->|RuntimeException| M[WARN log, in-memory sliding window]
    R -->|no| M
    M --> P
    M --> D
```

Redis keys look like `common:rl-internal-key:service` and `common:rl-internal-user:7` with the default prefix. Colons inside identity (for example IPv6) are replaced with underscores so they cannot shift the key structure.

## 8. Application startup checks owned by common

| Check | Failure |
|---|---|
| `InternalApiStartupValidator` | Disabled internal API, or weak/placeholder key, outside `local`/`dev` → `IllegalStateException`, process does not start. |
| `StorageStartupValidator` | Remote default MinIO admin or `auto-create-bucket=true` outside laptop profiles; or bucket missing with auto-create off; or MinIO unreachable → `IllegalStateException`. |
| `StorageConfig.validateProvider` | `storage.provider` set to anything other than `minio` or `s3` (blank is allowed). |

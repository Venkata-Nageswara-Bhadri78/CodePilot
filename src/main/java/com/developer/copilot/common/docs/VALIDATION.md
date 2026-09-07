# Validation

Validation in common is **not** Bean Validation on a common request DTO (there is none). It is path/file/URL/security validation inside shared services and filters, plus the global translation of *other packages’* `@Valid` failures.

## Categories

| Category | Where | What happens on failure |
|---|---|---|
| Input | Upload PDF checks, URL parse, MVC/Bean Validation mapped in `GlobalExceptionHandler` | 400 (or 415/405 as listed in errors) |
| Business | Allow-listed `IllegalArgumentException` prefixes; feature exceptions mapped globally | 400/409/422 as mapped |
| Security | Internal API key, storage path ownership, unsafe URL schemes | 401 or 400 `InvalidFileException` / `InvalidJobUrlException` |
| Infrastructure / existence | MinIO missing object, bucket at boot | 404 or failed startup |

---

## Input validation

### Bean Validation and MVC (global handler)

Common does not declare `@NotNull` DTOs. When a feature controller uses `@Valid`:

- Field errors become `field: defaultMessage` joined by commas (400).
- `ConstraintViolationException` messages are joined (400).
- Malformed JSON → `Request body is missing or malformed JSON.`
- Wrong HTTP method → `Method not allowed.`
- Wrong Content-Type → `Unsupported media type.`
- Missing required query/form parameter → `Required request parameter is missing.`
- Type mismatch: parameter name `jobId` → `Invalid job id.`; any other name → `Invalid request parameter.`

### Multipart size

`MaxUploadSizeExceededException` → `Maximum file size is {maxFileSizeMb} MB.` `ResumeProperties` is optional on the handler; if the bean is absent the message uses **5**. Servlet multipart caps themselves are configured in `user` (`ResumeMultipartConfig`), not common.

### PDF upload (`FileStorageServiceImpl.validatePdfUpload`)

All must pass:

- File non-null and non-empty → else `Uploaded file is empty.`
- If `Content-Type` is present, it must contain `pdf` (case-insensitive). `application/x-pdf` is accepted; `image/png` is not even with `%PDF` magic.
- If original filename is present, it must end with `.pdf` (case-insensitive).
- First four bytes must be `%PDF`.

Stored `contentType` is always `application/pdf`.

### Storage folder and key

`folderPath` is required. `storageKey` is required.

Normalization: `\` → `/`, strip leading slashes; folders also strip trailing slashes.

Rejected as `Invalid storage path.`:

- `..` or `//` anywhere
- empty, `.`, or `..` path segments
- characters other than letter, digit, `-`, `_`, `.`

### Job URLs (`UrlNormalizationUtil`)

**Strict** (`normalizeStrict`) — used by `jobs` and `jobextraction`:

- null/blank → `Job URL must not be empty.`
- not a parseable absolute `http`/`https` URL with a host → `Job URL must be a valid absolute http or https link.`

**Lenient** (`normalizeLenient`): null → null; blank → trimmed empty string; garbage http-less strings returned trimmed; `javascript`/`data`/`file`/`vbscript` still throw the absolute-http message.

Canonicalization rules (tracking param lists, www strip, port, query sort) are in [URL-NORMALIZATION.md](COMMON-SERVICE-SPECIFIC-DOCS/URL-NORMALIZATION.md). They are identity rules, not user-facing validation messages.

---

## Business validation

Common does not implement feature rules (duplicate email, resume count, job title required). It maps those exceptions when feature code throws them.

`IllegalArgumentException` is only treated as client input when the message starts with the prefixes in [ERROR-HANDLING.md](ERROR-HANDLING.md). That is a compatibility allow-list, not a validator API.

`sha256Hex(null)` returns null; it does not throw.

---

## Security validation

### Internal API key

See [SECURITY.md](SECURITY.md). Runtime: header present and constant-time equal to current or previous key. Startup: length ≥ 32 and not a placeholder outside `local`/`dev`.

### Storage ownership

When `SecurityContext` has `CustomUserDetails` with a non-null user id, folder/key must be `users/{id}` or `users/{id}/...`. Otherwise `Invalid storage path.` (400), and MinIO is not called.

No JWT (parse workers, some tests): ownership check skipped; character checks still run.

### Callers still responsible

`FileStorageService` documents that `folderPath` / `storageKey` must be built from the authenticated user id, never from a raw client parameter. The ownership check is a backstop, not a substitute for that.

---

## Resource existence

| Check | Result |
|---|---|
| `exists(storageKey)` | `false` for MinIO `NoSuchKey` / `NoSuchObject` / `NotFound`; other MinIO errors → `StorageException` |
| `download` missing object | `StorageObjectNotFoundException` → 404 `File not found.` |
| Bucket missing at startup, auto-create off | `IllegalStateException` (boot fails) |

Common has no uniqueness checks of its own (no tables). Duplicate jobs/resumes are feature exceptions mapped to 409.

---

## Limits and constraints

| Limit | Default | Enforced by |
|---|---|---|
| Internal calls per key-identity `"service"` / minute | 60 | `InternalApiRateLimitFilter` |
| Internal calls per JWT user / minute | 30 | same |
| Redis/in-memory window | 60 seconds | same |
| MinIO HTTP connect / read-write | 10s / 30s | `StorageConfig` OkHttp client |
| Internal key length (non-laptop) | 32 characters | `InternalApiStartupValidator` |
| Upload PDF only | — | `FileStorageServiceImpl` |
| Max upload size message | 5 MB unless `ResumeProperties` says otherwise | `GlobalExceptionHandler` |

Setting an internal per-minute property to `0` disables that bucket (always allow).

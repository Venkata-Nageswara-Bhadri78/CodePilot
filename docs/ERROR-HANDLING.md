# Global error architecture

Almost every JSON error is:

```json
{
  "success": false,
  "message": "<client-safe string>",
  "data": null,
  "timestamp": "<LocalDateTime>"
}
```

Defined by `com.developer.copilot.common.dto.ApiResponse`. Filters that reject before a controller (JWT entry point, internal key, some rate-limit filters) still serialize this shape.

```text
Failure
  → Exception or filter write
  → @RestControllerAdvice (or filter)
  → HTTP status + ApiResponse
```

## Handlers

| Handler | Scope |
| --- | --- |
| `GlobalExceptionHandler` | Application-wide mappings |
| `RateLimitExceptionHandler` (auth package) | Auth `RateLimitExceededException` → 429 + `Retry-After` (also mapped in global) |
| `ChatAssistantExceptionHandler` | Highest precedence, **only** `ChatAssistantController`: type mismatch → `"Invalid job id."`; chat conflict |

Unhandled `Exception`: **500** `"Something went wrong."` (logged).

`IllegalArgumentException`: **400** only if the message starts with a small allowlist of known client prefixes; otherwise **500** `"Something went wrong."`

## HTTP status map (implemented)

| Status | Typical causes |
| --- | --- |
| 400 | Bean Validation; malformed JSON; invalid job URL; OTP/reset token problems; job field validation; invalid file; resume/profile limits; illegal page/size; `INVALID JOB URL`; multipart errors; oversize upload |
| 401 | Login; refresh; missing/invalid JWT; missing/wrong internal key |
| 403 | Extension client on non-extraction path; `EmailNotVerifiedException`; `BrowserExtensionAccessDeniedException` |
| 404 | Job/profile/resume/child/storage object not found (ownership treated as not found) |
| 405 | Method not allowed |
| 409 | Duplicate job/resume/profile; chat concurrent create; `AiResumePendingException`; data integrity |
| 415 | Unsupported media type |
| 422 | Resume parse failed/empty (`ResumeParsingException`) |
| 429 | Any module `RateLimitExceededException` + `Retry-After` |
| 502 | `AiServiceException`; automated page fetch failure |
| 503 | AI/extraction/fetch circuit or bulkhead; SMTP `EmailDeliveryException`; `AiUnavailableException`; `JobExtractionAiUnavailableException`; `AutomatedJobExtractionUnavailableException` |
| 500 | Unhandled; generic storage infrastructure failure (client message is generic) |

Storage: `InvalidFileException` → 400; `StorageObjectNotFoundException` → 404 `"File not found."`; other `StorageException` → 500 generic file-storage message.

## Authentication vs authorization failures

- **401** `"Unauthorized."` — security entry point (no valid web/extension authentication on a protected route). Distinct from login’s `"Invalid email or password."`
- **403** — authenticated but not allowed (extension channel, or extraction email-verified check in service).

## Module-specific exception types (mapped globally)

Auth: credentials, OTP, reset, refresh, email delivery, `ResourceAlreadyExistsException` (409; register still avoids leaking existence at the controller).  
Jobs: `JobNotFoundException`, `DuplicateJobException`, `JobValidationException`.  
User: profile/child/resume exceptions listed in `GlobalExceptionHandler`.  
AI: `AiServiceException`, `AiUnavailableException`, `AiResumePendingException`.  
Extraction: `EmailNotVerifiedException`, `InvalidJobUrlException`, automated URL/fetch/unavailable exceptions.  
Chat: `ChatConflictException`.  
Common: `InvalidJobUrlException`, storage exceptions, common rate limit.

## What clients must not assume

- Stream endpoints may return HTTP 200 and still fail via SSE `error`.
- 200 on extraction with `requiresManualReview=true` is **not** an error.
- Chat GET history with empty messages is **200**, not 404.
- Infrastructure details (SQL, SMTP, MinIO, provider dumps) are stripped.

Module error catalogs: `*/docs/ERROR-HANDLING.md`.

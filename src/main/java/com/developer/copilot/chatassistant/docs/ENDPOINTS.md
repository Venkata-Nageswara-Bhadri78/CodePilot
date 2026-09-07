# Endpoints

Base path: `/api/v1/chat-assistant`

All four endpoints require a Bearer JWT. There is no public or internal-only variant. None of them accept a session id as input; **job id is the chat key**.

Common envelope (`ApiResponse`):

```json
{
  "success": true,
  "message": "…",
  "data": {},
  "timestamp": "2026-08-24T15:30:00"
}
```

On error, `success` is `false`, `data` is omitted/null, and `message` is client-safe. Timestamps are server `LocalDateTime` with **no offset**.

Required header on every call:

```http
Authorization: Bearer <access-token>
```

Obtain the token from `POST /api/v1/auth/login` (auth service).

---

## POST `/api/v1/chat-assistant/jobs/{jobId}/messages`

Create a new turn for the given job. Creates the chat session on the first successful send for that job.

| | |
| --- | --- |
| Auth | Authenticated JWT |
| Rate limit | 8 sends/minute per IP **and** per user (default). **429** + `Retry-After` |
| Side effects | May insert `chat_sessions`; inserts one `chat_messages` row; calls the AI service; bumps `updatedAt` |

### Path parameters

| Name | Type | Rules |
| --- | --- | --- |
| `jobId` | long | Saved job owned by the current user. Non-numeric → **400** `"Invalid job id."` |

### Request body (`SendChatMessageRequest`)

| Field | Validation |
| --- | --- |
| `prompt` | Required. Not blank (whitespace-only rejected). Max **8000** characters (8001 → 400) |

Unknown JSON fields (for example `resumeId`) are **ignored**. The service always sends `resumeId`/`customResumeText` as null to the AI module.

```json
{
  "prompt": "How well do I match the required experience for this role?"
}
```

### Success — 201 Created

`message`: `"Message sent successfully."`

`data` is `SendChatMessageResponse` — session identity plus **only the new turn**:

```json
{
  "success": true,
  "message": "Message sent successfully.",
  "data": {
    "chatSessionId": 15,
    "chatTitle": "Amazon - SDE 1",
    "latestTurn": {
      "id": 42,
      "turnNumber": 1,
      "userPrompt": "How well do I match the required experience for this role?",
      "aiResponse": "### Match\nYou match **4 of 6** required skills.",
      "createdAt": "2026-08-24T15:30:00"
    }
  },
  "timestamp": "2026-08-24T15:30:00"
}
```

`turnNumber` is 1-based. Later sends still return **201** (a message was created), not 200.

Poll `GET /api/v1/chat-assistant/jobs/{jobId}` for the rest of the thread. This call can take on the order of the AI timeout (default 60 seconds).

### Errors

| Status | When | Typical message |
| --- | --- | --- |
| 400 | Blank/oversized prompt, malformed JSON, illegal `jobId` | `"prompt: Prompt cannot be blank."`, `"Prompt cannot exceed 8000 characters."`, `"Request body is missing or malformed JSON."`, `"Invalid job id."` |
| 401 | Missing or invalid JWT | `"Unauthorized."` |
| 404 | Job missing or not owned by the caller | `"Job not found."` |
| 409 | In-flight send on same job, turn unique clash, or resume still parsing | `"This chat was updated at the same time. Please retry."` or `"Your resume is still being processed. Please try again in a few moments."` |
| 422 | Resume parse failed or empty (from AI/resume path) | e.g. `"Your resume could not be parsed. Please upload a different PDF and try again."` |
| 429 | Send rate limit | `"Too many requests. Please try again later."` + `Retry-After` |
| 502 | Provider failure, timeout, or blank reply after sanitization | AI/provider message, or `"The AI service returned an empty response. Please try again."` |
| 503 | AI circuit open or bulkhead full | `"The AI service is busy. Please try again shortly."` or `"The AI service is temporarily unavailable. Please try again shortly."` |

Malformed JSON is handled by `GlobalExceptionHandler` (`HttpMessageNotReadableException`). Bean validation uses `MethodArgumentNotValidException` (`"prompt: …"`).

---

## GET `/api/v1/chat-assistant/jobs/{jobId}`

Page the conversation for a job. **Does not call the AI service.**

| | |
| --- | --- |
| Auth | Authenticated JWT |
| Rate limit | None |
| Side effects | None |

### Path / query

| Name | Default | Rules |
| --- | --- | --- |
| `jobId` | — | Owned job; same 404 rule as send |
| `page` | `0` | Zero-based; `page < 0` → 400 |
| `size` | `50` | `1..50`; `0` or `> 50` → 400 |

Sort is fixed: `turnNumber ASC`. Clients cannot request another sort.

Error for illegal paging: `"page must be >= 0 and size must be between 1 and 50."`

### Success — 200 OK

`message`: `"Chat history retrieved successfully."`

When a chat exists:

```json
{
  "success": true,
  "message": "Chat history retrieved successfully.",
  "data": {
    "chatSessionId": 15,
    "jobId": 42,
    "chatTitle": "Amazon - SDE 1",
    "messages": [
      {
        "id": 1,
        "turnNumber": 1,
        "userPrompt": "Q1",
        "aiResponse": "A1",
        "createdAt": "2026-08-24T15:30:00"
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-08-24T15:30:00"
}
```

When **no chat has been started** (job exists and is yours):

```json
{
  "chatSessionId": null,
  "jobId": 42,
  "chatTitle": null,
  "messages": [],
  "page": 0,
  "size": 50,
  "totalElements": 0,
  "totalPages": 0
}
```

That empty shape is **200**, not 404.

### Errors

| Status | When |
| --- | --- |
| 400 | Illegal page/size, or a path/query value that does not bind (non-numeric `jobId` is `"Invalid job id."`; this controller’s type-mismatch handler uses that same message for any `MethodArgumentTypeMismatchException`) |
| 401 | Missing or invalid JWT |
| 404 | Job not found or not owned (`"Job not found."`) |

---

## GET `/api/v1/chat-assistant`

List the current user’s job chats, newest-updated first. **Does not call the AI service.**

| | |
| --- | --- |
| Auth | Authenticated JWT |
| Rate limit | None |
| Side effects | None |

### Query

Same `page` / `size` rules as history (`default 0 / 50`, max size 50).

### Success — 200 OK

`message`: `"Chats retrieved successfully."`

`data` is `ChatSessionListResponse`:

```json
{
  "success": true,
  "message": "Chats retrieved successfully.",
  "data": {
    "chats": [
      {
        "chatSessionId": 15,
        "jobId": 42,
        "jobTitle": "SDE 1",
        "company": "Amazon",
        "chatTitle": "Amazon - SDE 1",
        "updatedAt": "2026-08-24T15:30:00"
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-08-24T15:30:00"
}
```

`updatedAt` is the **session row**, updated when a turn is saved — not the latest message’s `createdAt` field specifically, though they usually move together.

Empty account: `"chats": []` with `totalElements` 0, still 200.

### Errors

| Status | When |
| --- | --- |
| 400 | Illegal page/size |
| 401 | Missing or invalid JWT |

This endpoint does not take a job id, so it does not return 404 for jobs.

---

## DELETE `/api/v1/chat-assistant/jobs/{jobId}`

Permanently delete all messages and the session for that job. **Does not delete the job.** **Does not call the AI service.**

| | |
| --- | --- |
| Auth | Authenticated JWT |
| Rate limit | None |
| Side effects | Bulk-delete messages, then delete session |

### Path

`jobId` — must be an owned job (otherwise 404), even if no chat exists.

### Success — 200 OK

`message`: `"Chat deleted successfully."`  
`data` is empty/null.

If no session exists, the handler still returns 200 (idempotent REST delete).

### Errors

| Status | When |
| --- | --- |
| 400 | Non-numeric job id (`"Invalid job id."`) |
| 401 | Missing or invalid JWT |
| 404 | Job not found or not owned |

---

## Shared HTTP behavior

| Status | Source |
| --- | --- |
| 401 | Spring Security entry point, or `InvalidCredentialsException` from `CurrentUserService` |
| 405 | Wrong HTTP method (`"Method not allowed."`) |
| 415 | Wrong Content-Type on POST (`"Unsupported media type."`) |
| 409 | Also `DataIntegrityViolationException` → `"The request conflicts with existing data. Please retry."` if it escapes the service |
| 500 | Unhandled exceptions → `"Something went wrong."` |

There is no admin, webhook, or test-only endpoint in this package.

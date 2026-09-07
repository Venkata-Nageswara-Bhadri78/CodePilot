# Endpoints

All public HTTP endpoints of the `ai` service live under `/api/v1/ai`. They require a Bearer JWT for an enabled, email-verified user. None of them use `X-Internal-Api-Key`.

Successful JSON responses use the shared envelope:

```json
{
  "success": true,
  "message": "...",
  "data": {},
  "timestamp": "2026-01-01T12:00:00"
}
```

`timestamp` is server `LocalDateTime` without a timezone offset. Error responses use the same envelope with `success: false` and `data` omitted or null.

Job extraction and job-history chat are **not** AI HTTP endpoints. They are in-process methods documented at the bottom of this file.

---

## Shared rules for chat and stream

Both `POST /chat` and `POST /chat/stream` accept the same body (`AiChatRequest`) and the same grounding rules.

### Authentication

Header: `Authorization: Bearer <access-token>` from `POST /api/v1/auth/login`.

Missing, garbage, disabled-user, or unverified-email tokens → `401` with message `Unauthorized.` (security entry point) or, if the controller runs without a principal, `InvalidCredentialsException` mapped to `401`.

### Rate limit

Shared **chat** bucket: default **8** requests per IP per 60 seconds **and** 8 per authenticated user id per 60 seconds (`app.ai.chat-per-minute`). Exceeded → `429`, header `Retry-After`, message `Too many requests. Please try again later.`

### Request body (`AiChatRequest`)

| Field | Required | Constraints | Behavior |
|---|---|---|---|
| `prompt` | Yes | Non-blank, max 8000 | User request |
| `customResumeText` | No | Max 16000 | Trimmed and used as resume. Skips DB. Wins over `resumeId`. |
| `resumeId` | No | Long | Authenticated user’s **active** resume only. Ignored if custom text is present. Missing/foreign → `404`. |
| (neither resume field) | — | — | High-priority completed parse if present; otherwise chat continues with empty resume context |
| `jobDescription` | No | Max 16000 | Trimmed JD. Skips jobs table. Wins over `jobId`. |
| `jobId` | No | Long | Row owned by the current user. Uses `description` if non-blank, else `originalDescription`. Foreign/missing → `404`. |
| (neither job field) | — | — | No job-description section in the prompt |
| `mode` | No | Enum | Default `GENERAL_CHAT`. Invalid value → `400`. |
| `temperature` | No | 0.0–2.0 | Per-call OpenAI-compatible option. Omitted → not set on the request options. |

Unknown JSON fields are ignored.

`mode` values: `GENERAL_CHAT`, `RESUME_REVIEW`, `COVER_LETTER`, `COLD_EMAIL`, `INTERVIEW_PREP`, `MATCH_ANALYSIS`.

`COVER_LETTER` uses `app.ai.cover-letter-max-completion-tokens` (default 4096). Other modes use `app.ai.max-completion-tokens` (default 2048).

---

## `POST /api/v1/ai/chat`

One complete career-assistance completion.

**Content-Type:** `application/json`

### Example request

```json
{
  "prompt": "Rate my resume against this job and suggest 3 improvements.",
  "mode": "MATCH_ANALYSIS",
  "temperature": 0.7
}
```

### Success (`200`)

Message: `AI completion generated successfully.`

`data` (`AiChatResponse`):

| Field | Notes |
|---|---|
| `content` | Model text (may be empty string if the provider returned no output) |
| `model` | Always `app.ai.default-model` (example: `gemini-flash-latest`), not a live probe of the upstream name |
| `finishReason` | From provider metadata, or `STOP` if absent |
| `mode` | Echo of the request mode |
| `promptTokens`, `completionTokens`, `totalTokens` | From provider usage; may be `null` |
| `timestamp` | Generation time |

### Error statuses

| Status | When |
|---|---|
| `400` | Validation, blank prompt, invalid `mode`, malformed/missing JSON, temperature out of range |
| `401` | Missing or invalid JWT |
| `404` | Explicit `resumeId` not found for this user, profile missing when `resumeId` is set, or `jobId` not owned |
| `409` | Resume still being processed |
| `422` | Resume parse failed or extracted text empty |
| `429` | Chat rate limit |
| `502` | Provider / timeout / unparsable upstream (`AiServiceException`) |
| `503` | Circuit open or bulkhead full (`AiUnavailableException`) |
| `500` | Unexpected server error (`Something went wrong.`) |

Provider error text is sanitized. Clients never receive API keys, `app.ai.default-model` paths, or raw upstream dumps.

---

## `POST /api/v1/ai/chat/stream`

Same body and grounding as `POST /chat`. Produces `text/event-stream`.

**Accept:** `text/event-stream`

Domain failures that happen **before** the stream opens (validation, 401, 404, 409, 422, 429, 503 from a circuit already open) are JSON `ApiResponse` like chat.

Once the stream is open, HTTP status may remain `200` even if the provider fails. Inspect the terminal event.

### SSE events

| Event | When | `isCompleted` | `finishReason` |
|---|---|---|---|
| `message` | Non-empty token | `false` | unset |
| `done` | Successful end | `true` | `STOP` (or any non-`ERROR` completed chunk) |
| `error` | Provider/timeout after open | `true` | `ERROR` |

`content` on error chunks is prefixed with `AI Service Error: ` plus a friendly message.

### Example events

```
event:message
data:{"content":"Hello","isCompleted":false,"model":"gemini-flash-latest"}

event:done
data:{"content":"","isCompleted":true,"finishReason":"STOP","model":"gemini-flash-latest"}
```

Swagger Try-it-out often buffers SSE. Use an unbuffered client (`curl -N`).

---

## `GET /api/v1/ai/resume-context`

Returns the authenticated user’s completed, non-empty **high-priority** parsed resume as a string. No query parameter. The payload is PII.

### Rate limit

Separate **resume-context** bucket: default **20** per IP and per user per 60 seconds (`app.ai.resume-context-per-minute`).

### Success (`200`)

Message: `Candidate resume context retrieved successfully.`

`data` is a string, for example:

```text
NAME: Jane Doe
EMAIL: jane@example.com
```

### Error statuses

| Status | When |
|---|---|
| `401` | Missing or invalid JWT |
| `404` | No profile, or no active high-priority resume |
| `409` | Parse still in progress |
| `422` | Parse failed or `contextText` blank |
| `429` | Resume-context rate limit |

Unlike chat, this endpoint does **not** succeed with empty context when no resume exists.

---

## `GET /api/v1/ai/health`

## `GET /api/v1/ai/config`

These two paths share one handler and return the same payload. They are **not** a live Gemini probe and must not be used as Kubernetes readiness. They do not verify API keys or network connectivity. JWT is still required. They are **not** rate-limited by `AiRateLimitFilter`.

### Success (`200`)

Message: `AI configuration metadata (does not verify provider connectivity).`

`data`:

| Field | Value |
|---|---|
| `status` | Always `"UP"` if this handler ran |
| `healthCheckType` | `"configuration"` |
| `activeModel` | `"{defaultModel} (Provider: {provider})"` e.g. `gemini-flash-latest (Provider: gemini)` |
| `streamingSupported` | Always `true` |
| `timestamp` | Now |

`401` if unauthenticated.

---

## Not AI HTTP: job extraction and job chat

| Capability | HTTP owner | AI method |
|---|---|---|
| Extract job fields from pasted text | `POST /api/v1/job-extraction/parse` | `AiService.extractJobInfo(JobExtractionAiRequest)` |
| Chat about a saved job | chat-assistant send-message API | `AiService.continueJobChat(JobChatAiRequest, userEmail)` |

`JobExtractionAiRequest` requires `jobUrl` (max 2048) and `rawJobText` (max 100000).

`JobChatAiRequest` requires `jobId` and `newPrompt` (max 8000). Optional `resumeId` / `customResumeText`. `priorTurns` max 40; each turn needs non-blank `userPrompt` (max 8000) and `aiResponse` (max 16000). The service still sends at most `app.ai.max-prior-turns-sent` (default 16) newest turns to the model.

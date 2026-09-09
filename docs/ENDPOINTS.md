# Global API catalog

JSON successes and errors use `ApiResponse` (`success`, `message`, `data`, `timestamp`) unless noted.

**Default auth:** web access JWT (`Authorization: Bearer`). User must be enabled and email-verified or the JWT filter does not authenticate → **401** `"Unauthorized."`

**Extension JWT:** allowed only on job-extraction prefixes.

**Internal:** JWT **plus** `X-Internal-Api-Key`.

Per-endpoint DTO fields: see module `ENDPOINTS.md` files.

---

## Auth — `/api/v1/auth`

| Method | Path | Auth | Purpose | Major I/O | Status notes |
| --- | --- | --- | --- | --- | --- |
| POST | `/register` | Public | Create unverified account; email OTP | Body: username, fullName, email, password | 201 always on valid input (including duplicates). 429 |
| POST | `/login` | Public | Issue access JWT + refresh UUID | Body: email, password → `AuthResponse` | 401 generic login message. 429 |
| POST | `/extension-token` | Web JWT | Mint restricted extension JWT | → `ExtensionAuthResponse` (no refresh) | 403 if already extension or extension disabled. 429 |
| POST | `/verify-email` | Public | Activate account | Body: email, 6-digit OTP | 400 invalid/expired/used OTP. 429 |
| POST | `/resend-otp` | Public | Resend OTP (generic success) | Body: email | 200 always if valid. Mail cooldown. 429 |
| POST | `/forgot-password` | Public | Request reset mail (generic success) | Body: email | 200 always if valid. 429 |
| POST | `/reset-password` | Public | Set password; revoke sessions | Body: token, new password | 400 bad/used/expired token |
| GET | `/me` | Web JWT | Current user | → `UserResponse` | 401 |
| POST | `/refresh-token` | Public (UUID is the secret) | Rotate refresh; new access JWT | Body: refreshToken → `AuthResponse` | 401 invalid/expired/revoked/reuse. 429 |
| POST | `/logout` | Web JWT | Revoke this refresh | Body: refreshToken | Does not bump `tokenVersion` |
| POST | `/logout-all` | Web JWT | Revoke all refresh; bump `tokenVersion` | — | Immediate JWT invalidation |

Rate limits (defaults): login/register 5/min, verify 10, resend/forgot 3, refresh 10, extension-token 10 (IP + identity where implemented). See auth docs.

---

## User — profile `/api/v1/users/profile`

All **web JWT**. One profile per user. Child collections capped (`user.profile.max-child-items`, default 20).

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/` | Create profile (201). 409 if exists |
| GET | `/` | Get profile |
| PUT | `/` | Replace headline/summary/technicalSkills (JSON null clears) |
| DELETE | `/` | Delete profile |
| POST/GET | `/experiences` | Add / list work experience |
| PUT/DELETE | `/experiences/{id}` | Update / delete |
| POST/GET | `/educations` | Education |
| PUT/DELETE | `/educations/{id}` | Update / delete |
| POST/GET | `/projects` | Projects |
| PUT/DELETE | `/projects/{id}` | Update / delete |
| POST/GET | `/additional-info` | Additional information |
| PUT/DELETE | `/additional-info/{id}` | Update / delete |
| POST/GET | `/links` | Profile links |
| PUT/DELETE | `/links/{id}` | Update / delete |

Missing child ids → **404**. Limit exceeded → **400**.

---

## User — resumes `/api/v1/users`

All **web JWT**. Profile must exist.

| Method | Path | Purpose | Side effects / notes |
| --- | --- | --- | --- |
| POST | `/resumes` | Upload PDF (`file` part) | 201. MinIO write + DB row + async parse. Max 10, max 5 MB (config). Duplicate checksum 409. 429 |
| GET | `/resumes` | List active resumes | No parse status in list |
| GET | `/resumes/{resumeId}` | Download PDF bytes | **Not** `ApiResponse`. `Content-Disposition` attachment |
| DELETE | `/resumes/{resumeId}` | Hard-delete row + object | Allows same checksum to be uploaded again. 429 |
| PATCH | `/resumes/{resumeId}/high-priority` | Set primary resume | Empty body. No unset flag |

---

## Internal — `/api/v1/internal/resumes`

**Web JWT + internal key.** PII. Not for the SPA.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/parsed` | Completed parse of high-priority resume |
| GET | `/{resumeId}/parsed` | Parsed data for that resume if owned |

401 missing JWT or key. 404 no profile/resume. 422 PENDING or FAILED. 429 hallway + user parse limits.

---

## Jobs — `/api/v1/jobs`

All **web JWT**. Foreign job ids look like **404**. Duplicate canonical URL → **409**.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/` | Create job (201). Normalizes URL |
| GET | `/` | Paged list; optional search/sort query. Page size max 50 |
| GET | `/{id}` | Full job |
| PUT | `/{id}` | Full replace (omitting skills clears them) |
| PATCH | `/{id}` | Dirty fields only |
| DELETE | `/{id}` | Delete job (cascades chat via FK) |
| PATCH | `/{id}/location` | Field update |
| PATCH | `/{id}/title` | |
| PATCH | `/{id}/company` | |
| PATCH | `/{id}/employment-type` | |
| PATCH | `/{id}/work-mode` | |
| PATCH | `/{id}/experience` | |
| PATCH | `/{id}/salary` | |
| PATCH | `/{id}/education` | |
| PATCH | `/{id}/department` | |
| PATCH | `/{id}/industry` | |
| PATCH | `/{id}/source-platform` | |
| PATCH | `/{id}/source-url` | Re-normalize; uniqueness |
| PATCH | `/{id}/skills` | `[]` clears skills |
| PATCH | `/{id}/description` | |
| PATCH | `/{id}/original-description` | |

Default rate limits: POST 15/min; mutate 30; bare list 60; search 20; GET by id 60 (per IP and user).

---

## Manual job extraction — `/api/v1/job-extraction`

**JWT (web or extension).** Service also requires verified email (403 if that path is reached).

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/parse` | Preview from `sourceUrl` + pasted `rawJobText`. **No DB write** |

200 even if `requiresManualReview` is true. 409 already saved. 429 default 8/min. 502 AI. 503 circuit/bulkhead.

---

## Automated job extraction — `/api/v1/automated-job-extraction`

**JWT (web or extension).**

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/parse` | Fetch URL, extract job text, reuse manual parse. **No DB write** |

Body: `sourceUrl`. Unusable URLs: **400** `"INVALID JOB URL"` (generic; no SSRF details). Fetch failure **502**. Fetch circuit **503**. Parse rate default **5/min**.

---

## AI — `/api/v1/ai`

All **web JWT**. No internal key.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/chat` | One JSON completion (`AiChatRequest` → `AiChatResponse`) |
| POST | `/chat/stream` | SSE `text/event-stream` (`message` / `done` / `error`) |
| GET | `/resume-context` | High-priority completed parse as string (PII) |
| GET | `/health` | Configuration metadata (`status=UP` is **not** a live provider probe) |
| GET | `/config` | Same payload as `/health` |

Chat rate default 8/min; resume-context 20/min. Explicit `resumeId` / `jobId` not owned → 404. Pending parse 409. Failed/empty parse 422.

---

## Chat assistant — `/api/v1/chat-assistant`

All **web JWT**.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/jobs/{jobId}/messages` | Send turn; 201. Creates session on first success |
| GET | `/jobs/{jobId}` | Paged history (`page`, `size` max 50). Empty messages if no chat yet |
| GET | `/` | Paged session summaries for current user |
| DELETE | `/jobs/{jobId}` | Delete session + messages. Idempotent 200. Does not delete the job |

Send rate default 8/min. Concurrent send collision **409**.

---

## Development / test-only

| Method | Path | When present | Auth | Purpose |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/test` | `@Profile("dev")` `TestController` | Web JWT (not public) | Confirms JWT authentication |
| GET | `/swagger-ui.html`, `/v3/api-docs/**` | Non-`prod`/`production` | Anonymous | OpenAPI UI. Disabled on production profile |

---

## Module endpoint docs

- Auth: `src/main/java/com/developer/copilot/auth/docs/ENDPOINTS.md`
- User: `src/main/java/com/developer/copilot/user/docs/ENDPOINTS.md`
- Jobs: `src/main/java/com/developer/copilot/jobs/docs/ENDPOINTS.md`
- Manual extraction: `src/main/java/com/developer/copilot/jobextraction/manualextraction/docs/ENDPOINTS.md`
- AI: `src/main/java/com/developer/copilot/ai/docs/ENDPOINTS.md`
- Chat: `src/main/java/com/developer/copilot/chatassistant/docs/ENDPOINTS.md`
- Common (internal hallway): `src/main/java/com/developer/copilot/common/docs/ENDPOINTS.md`

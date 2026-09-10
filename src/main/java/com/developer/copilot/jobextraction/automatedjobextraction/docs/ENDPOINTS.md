# Endpoints

`automatedjobextraction` exposes **one** REST endpoint. Saving a job is `POST /api/v1/jobs` in the `jobs` module, not documented here as an automated-extraction API.

All responses use the shared envelope:

```json
{
  "success": true,
  "message": "...",
  "data": {},
  "timestamp": "2026-01-15T10:30:00"
}
```

`timestamp` is server `LocalDateTime` without a timezone offset. On errors, `data` is null.

---

## `POST /api/v1/automated-job-extraction/parse`

**Purpose:** Normalize the job URL, block non-public targets, fetch the page, extract that job’s content, then reuse manual job-extraction to return a **preview**. Nothing is persisted.

**Access:** Authenticated. Web frontend **or** browser-extension JWT. Not a public or internal-only route. Not a test-only endpoint.

### Authentication and authorization

| Requirement | Behavior |
| --- | --- |
| Header `Authorization: Bearer <JWT>` | Required. Missing or invalid token → `401` with message `Unauthorized.` |
| Account `enabled` | JWT filter does not authenticate disabled users → `401` |
| Account `emailVerified` | JWT filter does not authenticate unverified users → `401`. If the service still sees `emailVerified != true` → `403` `Please verify your email before using this feature.` |
| Client type | `SecurityConfig` matches `/api/v1/automated-job-extraction/**` with `authenticated()`. Extension JWTs are allowed. Other APIs still reject extension tokens. |
| Roles | No extra role check. |

Obtain a web JWT from `POST /api/v1/auth/login`, or an extension JWT from `POST /api/v1/auth/extension-token` (auth module).

### Request

- **Content-Type:** `application/json`. Other types → `415` `Unsupported media type.`
- **Body:** `AutomatedJobExtractionRequest`

| Field | Required | Constraints | Notes |
| --- | --- | --- | --- |
| `sourceUrl` | Yes | `@NotBlank`, max **2000** characters | Copied URL; may include tracking params. **Not** `@Pattern`. `javascript:` and missing hosts fail after validation with `400` format message. Private hosts fail later with `INVALID JOB URL`. |

There is **no** `rawJobText` field. The server fetches the page.

Unknown JSON properties are ignored (they do not fail the request).

**Example request**

```json
{
  "sourceUrl": "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W?share_id=LinkedIn_corporate_page&utm_source=linkedin"
}
```

### Successful response

- **HTTP 200**
- **Message:** `Job information extracted successfully. Review and edit before saving.`
- **`data`:** `JobExtractionResultResponse` (same type as `POST /api/v1/job-extraction/parse`)

| Field | Source | Notes |
| --- | --- | --- |
| `sourceUrl` | Backend canonical URL | Use this on save, not the raw request URL. |
| `originalDescription` | Formatted extracted text | Labeled fields from the pipeline, **not** the raw HTML. Echoed by the mapper from the gateway’s `rawJobText`. |
| `description` | AI, clipped | Empty string if the model found no coherent description. |
| `title` | AI, clipped to 255 | |
| `company` | AI, clipped to 255 | |
| `location` | AI, clipped to 255 | |
| `employmentType` | AI, clipped to 100 | |
| `workMode` | AI, clipped to 50 | |
| `experience` | AI, clipped to 100 | |
| `salary` | AI, clipped to 100 | |
| `education` | AI, clipped to 255 | |
| `department` | AI, clipped to 100 | |
| `industry` | AI, clipped to 100 | Empty unless the extracted text names an industry. |
| `sourcePlatform` | AI, clipped to 50 | Empty unless the extracted text names the platform. |
| `skills` | AI | Never null. Max 50 items, each 255 chars. Blank items dropped. |
| `requiresManualReview` | Computed in manual mapper | `true` if title or company is blank **or** was truncated. **Omit on save.** |

**Example success (shape)**

```json
{
  "success": true,
  "message": "Job information extracted successfully. Review and edit before saving.",
  "data": {
    "sourceUrl": "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W",
    "originalDescription": "Source URL: https://...\nJob Title: Program Manager, Sr. Consultant\n...",
    "description": "...",
    "title": "Program Manager, Sr. Consultant",
    "company": "Visa",
    "location": "",
    "employmentType": "",
    "workMode": "",
    "experience": "",
    "salary": "",
    "education": "",
    "department": "",
    "industry": "",
    "sourcePlatform": "",
    "skills": ["Java"],
    "requiresManualReview": false
  },
  "timestamp": "2026-01-15T10:30:00"
}
```

`requiresManualReview: true` is still **200**, not an error.

### HTTP status codes

| Status | When |
| --- | --- |
| 200 | Preview built (including manual-review flag true). |
| 400 | Bean Validation, missing/malformed JSON, URL not absolute `http`/`https`, or not a usable job posting (`INVALID JOB URL`). |
| 401 | Missing/invalid JWT, disabled account, or JWT filter rejected unverified email. Also `InvalidCredentialsException` from `CurrentUserService`. |
| 403 | `EmailNotVerifiedException` from the service (or gateway). |
| 409 | This user already saved this canonical URL. |
| 415 | Body is not JSON. |
| 429 | Parse rate limit (per IP or per user). Header `Retry-After` is seconds. |
| 502 | Page fetch technical failure **or** `AiServiceException` (provider error, timeout, unparseable structured output). |
| 503 | Fetch circuit/bulkhead **or** AI circuit/bulkhead. Messages differ (see [ERROR-HANDLING.md](ERROR-HANDLING.md)). |
| 500 | Unhandled exception. Message is `Something went wrong.` (no stack traces). |
| 405 | Method other than POST on this path. Message `Method not allowed.` |

### Important error bodies

Validation (example: blank URL):

```json
{
  "success": false,
  "message": "sourceUrl: Job URL cannot be blank.",
  "data": null,
  "timestamp": "2026-01-15T10:30:00"
}
```

Invalid URL **format** (message does **not** echo the submitted URL):

```json
{
  "success": false,
  "message": "Job URL must be a valid absolute http or https link.",
  "data": null,
  "timestamp": "2026-01-15T10:30:00"
}
```

Not a usable job posting (SSRF, 404, listing page, captcha, quality fail). Tests assert the message does not contain `http`:

```json
{
  "success": false,
  "message": "INVALID JOB URL",
  "data": null,
  "timestamp": "2026-01-15T10:30:00"
}
```

Duplicate:

```json
{
  "success": false,
  "message": "This post was already added to your records.",
  "data": null,
  "timestamp": "2026-01-15T10:30:00"
}
```

Fetch technical failure:

```json
{
  "success": false,
  "message": "Unable to access the job posting. Please try again later.",
  "data": null,
  "timestamp": "2026-01-15T10:30:00"
}
```

Fetch unavailable (circuit/bulkhead):

```json
{
  "success": false,
  "message": "The job page could not be retrieved. Please try again shortly.",
  "data": null,
  "timestamp": "2026-01-15T10:30:00"
}
```

Rate limit (filter or handler):

```json
{
  "success": false,
  "message": "Too many requests. Please try again later.",
  "data": null,
  "timestamp": "2026-01-15T10:30:00"
}
```

### Request limits

- Default **5** POSTs per minute per client IP **and** per authenticated user id (`app.automatedjobextraction.parse-per-minute`). Set to `0` or less to disable.
- `sourceUrl` max 2,000 characters.
- Fetch: default connect 5s, per-request 15s, max body 1,500,000 bytes, max **3** redirects.
- Fetch in-process: max **8** concurrent; circuit opens after **3** consecutive fetch failures for **30** seconds.
- Downstream AI: max **5** concurrent extracts; circuit 3 failures / 30 seconds (manual module). Extracted text is clipped to 50,000 characters before the gateway.

### Side effects

- **No database writes.**
- Outbound GET(s) to the job URL (and possibly Workday CXS on the same host).
- May **INCR** Redis rate-limit keys and **SET** extracted text when Redis is enabled.
- May store a 3-minute in-memory extracted-text entry on the instance.
- Manual extraction may SET preview JSON in **its** Redis/memory cache and consume AI quota on preview-cache miss.
- Logs metric lines (`automatedjobextraction metric=success|invalidUrl|fetchFailure|cacheHit`).

### After a successful parse

Map `data` into the jobs create body. Use `data.sourceUrl` and `data.originalDescription`. Drop `requiresManualReview`. See [PREVIEW-AND-SAVE.md](AUTOMATEDJOBEXTRACTION-SERVICE-SPECIFIC-DOCS/PREVIEW-AND-SAVE.md).

---

## OpenAPI (non-production)

When the profile is not `prod` or `production`, Springdoc group `automated-job-extraction` documents `/api/v1/automated-job-extraction/**`. Production profiles disable public swagger (`springdoc` off; security does not `permitAll` swagger paths). Production tests assert `GET /v3/api-docs/automated-job-extraction` is not HTTP 200. This is documentation UI, not a second API.

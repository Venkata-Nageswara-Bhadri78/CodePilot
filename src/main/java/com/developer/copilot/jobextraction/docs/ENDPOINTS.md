# Endpoints

`jobextraction` exposes **one** REST endpoint. Saving a job is `POST /api/v1/jobs` in the `jobs` module, not documented here as a job-extraction API.

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

## `POST /api/v1/job-extraction/parse`

**Purpose:** Normalize the pasted job URL, reject it if this user already saved that canonical URL, extract structured fields from `rawJobText` via the AI module, and return a **preview**. Nothing is persisted.

**Access:** Authenticated. Not a public or internal-only route. Not a test-only endpoint.

### Authentication and authorization

| Requirement | Behavior |
| --- | --- |
| Header `Authorization: Bearer <JWT>` | Required. Missing or invalid token → `401` with message `Unauthorized.` |
| Account `enabled` | JWT filter does not authenticate disabled users → `401` |
| Account `emailVerified` | JWT filter does not authenticate unverified users → `401`. If the service still sees `emailVerified != true` → `403` `Please verify your email before using this feature.` |
| Roles | No extra role check. Any authenticated user who passes the filters may parse. |

Obtain a JWT from `POST /api/v1/auth/login` (auth module).

### Request

- **Content-Type:** `application/json`. Other types → `415` `Unsupported media type.`
- **Body:** `JobExtractionRequest`

| Field | Required | Constraints | Notes |
| --- | --- | --- | --- |
| `sourceUrl` | Yes | `@NotBlank`, max **2000** characters | Copied URL; may include tracking params. **Not** `@Pattern`. `javascript:` and missing hosts fail after validation with `400`. |
| `rawJobText` | Yes | `@NotBlank`, max **50000** characters | Full paste from the posting page. AI is instructed to ignore nav/cookie noise. |

Unknown JSON properties are ignored (they do not fail the request). Extra fields such as `requiresManualReview` on the request have no effect.

**Example request**

```json
{
  "sourceUrl": "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W?share_id=LinkedIn_corporate_page&utm_source=linkedin",
  "rawJobText": "Program Manager, Sr. Consultant ... Full job description ..."
}
```

### Successful response

- **HTTP 200**
- **Message:** `Job information extracted successfully. Review and edit before saving.`
- **`data`:** `JobExtractionResultResponse`

| Field | Source | Notes |
| --- | --- | --- |
| `sourceUrl` | Backend canonical URL | Use this on save, not the raw request URL. |
| `originalDescription` | Request `rawJobText` | Echoed verbatim. |
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
| `industry` | AI, clipped to 100 | Empty unless the paste names an industry. |
| `sourcePlatform` | AI, clipped to 50 | Empty unless the paste names the platform. |
| `skills` | AI | Never null. Max 50 items, each 255 chars. Blank items dropped. |
| `requiresManualReview` | Computed here | `true` if title or company is blank **or** was truncated. Not produced by the model. **Omit on save.** |

**Example success (shape)**

```json
{
  "success": true,
  "message": "Job information extracted successfully. Review and edit before saving.",
  "data": {
    "sourceUrl": "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W",
    "originalDescription": "Program Manager, Sr. Consultant ... Full job description ...",
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
| 400 | Bean Validation, missing/malformed JSON, or URL not absolute `http`/`https`. |
| 401 | Missing/invalid JWT, disabled account, or JWT filter rejected unverified email. Also `InvalidCredentialsException` from `CurrentUserService`. |
| 403 | `EmailNotVerifiedException` from the service. |
| 409 | This user already saved this canonical URL. |
| 415 | Body is not JSON. |
| 429 | Parse rate limit (IP or user). Header `Retry-After` is seconds. |
| 502 | `AiServiceException` (provider error, timeout, unparseable structured output). |
| 503 | Circuit open or bulkhead full (`JobExtractionAiUnavailableException`). |
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

Invalid URL (message does **not** echo the submitted URL):

```json
{
  "success": false,
  "message": "Job URL must be a valid absolute http or https link.",
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

- Default **8** POSTs per minute per client IP **and** per authenticated user id (`app.jobextraction.parse-per-minute`). Set to `0` or less to disable.
- `rawJobText` max 50,000; `sourceUrl` max 2,000.
- In-process: max **5** concurrent AI extracts; circuit opens after **3** consecutive AI failures for **30** seconds.

### Side effects

- **No database writes.**
- May **INCR** Redis rate-limit keys and **SET** preview JSON when Redis is enabled.
- May store a 3-minute in-memory preview on the instance.
- Logs metric lines (`jobextraction metric=success|duplicate|badUrl|aiFailure|cacheHit`).
- Consumes AI provider quota on cache miss.

### After a successful parse

Map `data` into the jobs create body. Use `data.sourceUrl` and `data.originalDescription`. Drop `requiresManualReview`. See [PREVIEW-AND-SAVE.md](JOBEXTRACTION-SERVICE-SPECIFIC-DOCS/PREVIEW-AND-SAVE.md).

---

## OpenAPI (non-production)

When the profile is not `prod` or `production`, Springdoc group `job-extraction` documents `/api/v1/job-extraction/**`. Production profiles disable public swagger (`springdoc` off; security does not `permitAll` swagger paths). This is documentation UI, not a second API.

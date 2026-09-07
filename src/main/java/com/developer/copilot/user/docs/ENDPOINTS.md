# Endpoints

All endpoints below belong to the `user` service. Unless noted, they:

- Require header `Authorization: Bearer <access JWT>`.
- Require the user to be **enabled** and **email-verified** (otherwise `401` with `"Unauthorized."`).
- Return JSON `ApiResponse` (`success`, `message`, `data`, `timestamp`) except resume **download**.
- Scope every lookup to the authenticated user's profile. Another user's id is `404`, not `403`.

CORS is configured application-wide (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`). Exposed headers include `Retry-After`.

There are no test-only or development-only controllers in this package. Swagger UI for these groups is disabled on `prod` / `production`; the endpoints themselves still exist.

---

## Shared error envelope

```json
{
  "success": false,
  "message": "User profile not found.",
  "data": null,
  "timestamp": "2026-09-07T12:00:00"
}
```

`401` from Spring Security uses `"Unauthorized."` Internal key failures use `"Invalid or missing internal service key."` Rate-limit filters use `"Too many requests. Please try again later."` plus header `Retry-After` (seconds).

---

## Profile — `/api/v1/users/profile`

No user-service rate limit on these routes. Bean Validation on request bodies (`@Valid`).

### `POST /api/v1/users/profile`

Create the single career profile for the current user.

| | |
|---|---|
| Auth | JWT |
| Body | `UserProfileRequest` — all fields optional |
| Success | `201` — nested `UserProfileResponse` (children empty lists) |
| Errors | `400` validation; `401`; `409` `"A profile already exists for this user."` |

Request fields:

| Field | Constraints |
|---|---|
| `headline` | max 300 |
| `summary` | max 5000 |
| `technicalSkills` | max 3000 |

```json
{
  "headline": "Backend engineer",
  "summary": "Builds APIs in Java.",
  "technicalSkills": "Java, Spring Boot"
}
```

`{}` is valid and creates an empty profile. Response `fullName` / `email` are from the auth user.

### `GET /api/v1/users/profile`

Return the profile with all child collections nested (not paginated).

| | |
|---|---|
| Success | `200` |
| Errors | `401`; `404` `"User profile not found."` |

Response `data` shape (children may be empty arrays):

```json
{
  "id": 1,
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "headline": "Backend engineer",
  "summary": "Builds APIs in Java.",
  "technicalSkills": "Java, Spring Boot",
  "workExperiences": [],
  "educations": [],
  "projects": [],
  "additionalInformation": [],
  "profileLinks": [],
  "createdAt": "2026-09-07T12:00:00",
  "updatedAt": "2026-09-07T12:00:00"
}
```

### `PUT /api/v1/users/profile`

Replace `headline`, `summary`, and `technicalSkills`. JSON `null` or an omitted key clears the stored column. Children are not accepted in this body.

| | |
|---|---|
| Success | `200` — full nested profile |
| Errors | `400`; `401`; `404` |

### `DELETE /api/v1/users/profile`

Delete the profile, all child rows, resume metadata, parsed data, and stored PDFs (object deletes after DB commit). The auth user remains.

| | |
|---|---|
| Success | `200` — `data` null, message `"Profile deleted successfully."` |
| Errors | `401`; `404`; `500` if storage throws during a path that still surfaces `StorageException` (after-commit MinIO failures are logged and do not fail this response) |

---

## Child collections

Each collection is independent CRUD under `/api/v1/users/profile`. Adding a 21st item (default cap 20) is `400` `"Maximum of {n} {name} records allowed."`

Common statuses: `201` create, `200` list/update/delete, `400` validation or cap, `401`, `404` no profile or unknown/foreign id.

### Work experience — `/experiences`

| Method | Path |
|---|---|
| `POST` | `/api/v1/users/profile/experiences` |
| `GET` | `/api/v1/users/profile/experiences` |
| `PUT` | `/api/v1/users/profile/experiences/{id}` |
| `DELETE` | `/api/v1/users/profile/experiences/{id}` |

Request (`WorkExperienceRequest`):

| Field | Rules |
|---|---|
| `companyName` | required, max 200 |
| `jobTitle` | required, max 200 |
| `startYear` | required, 1900–2100 |
| `endYear` | optional, 1900–2100; if present must be ≥ `startYear` |
| `description` | optional, max 5000 |

Response includes `id`, those fields, `createdAt`, `updatedAt`. Unknown id: `"Work experience not found."`

### Education — `/educations`

Same four methods on `/api/v1/users/profile/educations` and `/educations/{id}`.

| Field | Rules |
|---|---|
| `institutionName` | required, max 300 |
| `field` | required, max 300 |
| `startYear` | required, 1900–2100 |
| `endYear` | optional, 1900–2100; if present ≥ `startYear` |
| `scoreOrGrade` | optional, max 50 |

Unknown id: `"Education record not found."`

### Projects — `/projects`

| Field | Rules |
|---|---|
| `projectTitle` | required, max 300 |
| `projectDescription` | optional, max 5000 |
| `projectLink` | optional; if present must be `http://` or `https://` (not `javascript:`, `data:`, `file:`), max 500 |

Unknown id: `"Project not found."`

### Additional information — `/additional-info`

| Field | Rules |
|---|---|
| `type` | required, max 100 |
| `description` | optional, max 5000 |
| `link` | optional; same URL rules as `projectLink`, max 500 |

Unknown id: `"Additional profile information not found."`

### Profile links — `/links`

| Field | Rules |
|---|---|
| `url` | required, `http`/`https` only, max 500 |

Unknown id: `"Profile link not found."`

Example create link:

```http
POST /api/v1/users/profile/links
Authorization: Bearer <jwt>
Content-Type: application/json

{"url":"https://github.com/me"}
```

`201` `data`: `{ "id": 3, "url": "https://github.com/me", "createdAt": "...", "updatedAt": "..." }`.

---

## Resumes — `/api/v1/users/resumes`

Requires an existing profile (`404` `"User profile not found."` if missing).

**Rate limits** (defaults, 60-second window, both IP and user):

| Operation | Property | Default |
|---|---|---|
| `POST` collection | `app.user.upload-per-minute` | 8 |
| `DELETE` item | `app.user.delete-per-minute` | 8 |

`GET` list, `GET` download, and `PATCH` high-priority are **not** counted.

### `POST /api/v1/users/resumes`

Upload a PDF. Multipart field name is **`file`** (`multipart/form-data`).

| | |
|---|---|
| Auth | JWT |
| Success | `201` |
| Errors | `400` invalid/empty/oversize/filename/limit; `401`; `404` no profile; `409` duplicate checksum; `429`; `500` storage |

Side effects: object stored under `users/{userId}/resumes/{uuid}.pdf`; first active resume is high-priority; `PENDING` parse row created; background parse queued after commit.

Service validation (before/around storage):

- Not empty.
- `Content-Type` `application/pdf` **and** `%PDF` magic bytes.
- Size ≤ `resume.max-file-size-mb` (default 5). Servlet multipart cap is aligned to the same value.
- Original filename length ≤ 255.
- Active resume count < `resume.max-resume-count` (default 10).
- SHA-256 checksum not already present on an **active** resume for this profile. Unique constraint races also map to `409`.

Success body:

```json
{
  "success": true,
  "message": "Resume uploaded successfully.",
  "data": {
    "resumeId": 12,
    "message": "Resume uploaded successfully."
  },
  "timestamp": "2026-09-07T12:00:00"
}
```

Parse status is not included.

### `GET /api/v1/users/resumes`

List **active** resumes only. No parse status.

`200` `data` array of `{ "id", "originalFilename", "fileSize", "highPriority" }`.

### `GET /api/v1/users/resumes/{resumeId}`

Download the PDF. **Not** wrapped in `ApiResponse`.

| | |
|---|---|
| Success | `200` `Content-Type: application/pdf`; `Content-Disposition: attachment; filename="<sanitized>"` |
| Errors | `401`; `404` profile or resume; `404` `"File not found."` if the object is missing (`StorageObjectNotFoundException`) |

Filename allowlist: `[A-Za-z0-9._-]`, max 255. Anything else (spaces, quotes, CR/LF, Unicode, paths) becomes `resume.pdf`.

### `DELETE /api/v1/users/resumes/{resumeId}`

Hard-delete the row and parsed data. If it was primary, the newest remaining active resume becomes primary. Object delete runs after commit.

| | |
|---|---|
| Success | `200` |
| Errors | `401`; `404`; `429`; `500` storage on the synchronous path |

### `PATCH /api/v1/users/resumes/{resumeId}/high-priority`

Empty body. Clears primary on other active resumes, then sets this one.

| | |
|---|---|
| Success | `200` `"High priority resume updated successfully."` |
| Errors | `401`; `404` |

There is no API to clear primary without selecting another resume.

---

## Internal parsed resume — `/api/v1/internal/resumes`

**Not for the SPA.** Requires:

1. `Authorization: Bearer <user JWT>` (ownership).
2. `X-Internal-Api-Key: <shared secret>` (calling service), unless `internal.api.enabled=false` on profile `local`/`dev` only.

**Rate limits:** user parse budget (`app.user.parse-per-minute`, default 20) **and** common hallway (`app.common.internal-key-per-minute` default 60, `app.common.internal-user-per-minute` default 30).

Payload is PII (`rawText`, contact fields, `contextText`). `200` only when parsing is `COMPLETED`. `PENDING` and `FAILED` are `422`.

### `GET /api/v1/internal/resumes/parsed`

Parsed data for the caller's **high-priority** active resume.

| | |
|---|---|
| Success | `200` `ResumeParsedDataResponse` |
| Errors | `401` JWT or key; `404` no profile or no high-priority resume; `422` pending/failed/timeout/busy; `429` |

### `GET /api/v1/internal/resumes/{resumeId}/parsed`

Same contract for a specific resume. The resume must belong to the JWT user (`404` otherwise). Foreign ids are indistinguishable from missing ids.

Side effect: if there is no usable parsed row (missing, or `parserVersion` ≠ `resume.parsing.parser-version`), the service parses on demand (timeout `resume.parsing.timeout-seconds`, default 15) and persists asynchronously.

Example `200` `data` (trimmed):

```json
{
  "resumeId": 5,
  "originalFilename": "resume.pdf",
  "highPriority": true,
  "status": "COMPLETED",
  "attemptCount": 1,
  "lastError": null,
  "parserVersion": "v1",
  "parsedAt": "2026-09-07T12:00:00",
  "pageCount": 2,
  "characterCount": 4200,
  "truncated": false,
  "candidateName": "Jane Doe",
  "email": "jane@example.com",
  "phone": "+1 555 0100",
  "location": "Austin, TX",
  "linkedinUrl": "linkedin.com/in/jane",
  "githubUrl": "github.com/jane",
  "sections": {
    "SUMMARY": "Backend engineer."
  },
  "rawText": "...",
  "contextText": "================================================================================\nCANDIDATE RESUME PROFILE\n..."
}
```

`contextText` is null unless status is `COMPLETED`. Callers should treat any status other than `COMPLETED` as unusable; in practice those statuses are returned as `422` rather than `200`.

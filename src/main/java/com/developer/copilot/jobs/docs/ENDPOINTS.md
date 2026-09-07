# Jobs Endpoints

Base path: `/api/v1/jobs`

All endpoints require `Authorization: Bearer <access-jwt>`. Unauthenticated calls return **`401`** `"Unauthorized."` Content type for bodies is `application/json`.

Success and error bodies use the shared envelope:

```json
{
  "success": true,
  "message": "Job created successfully.",
  "data": {},
  "timestamp": "2026-01-15T10:30:00"
}
```

On error, `success` is `false`, `data` is null, and `message` is client-safe.

**Ownership:** a path `{id}` that is missing or belongs to another user is **`404`** `"Job not found with id: {id}"`.

**Rate limits:** 60-second windows, per IP and per authenticated user. Exceeding a budget is **`429`** `"Too many requests. Please try again later."` plus `Retry-After`. See [RATE-LIMITING.md](JOBS-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md). Default budgets: POST 15/min, PUT/PATCH/DELETE 30/min, collection GET without query 60/min, collection GET with query 20/min, GET by id 60/min.

None of these routes are test-only or profile-gated. OpenAPI for the group is omitted in `prod` / `production`, but the HTTP API itself is not.

---

## `POST /api/v1/jobs`

Creates a job for the current user.

**Auth:** JWT required.

**Rate-limit bucket:** `post`.

**Body:** `JobRequest`

| Field | Required | Rules |
|---|---|---|
| `sourceUrl` | yes | Not blank, max 2000. Must be absolute http/https after trim. Tracking params stripped on save |
| `originalDescription` | yes | Not blank, max 50_000 |
| `title` | yes | Not blank, max 255 |
| `company` | yes | Not blank, max 255 |
| `description` | no | Max 50_000 |
| `location` | no | Max 255 |
| `employmentType` | no | Max 100 |
| `workMode` | no | Max 50 |
| `experience` | no | Max 100 |
| `salary` | no | Max 100 (free text, not numeric) |
| `education` | no | Max 255 |
| `department` | no | Max 100 |
| `industry` | no | Max 100 |
| `sourcePlatform` | no | Max 50 |
| `skills` | no | Each item max 255. Null → empty list |

**Success:** `201` `"Job created successfully."` `data` is `JobResponse` (canonical `sourceUrl`, no `sourceUrlHash`).

**Errors:** `400` validation or invalid URL; `401`; `409` `"This post was already added to your records."`; `429`; `500`.

**Side effects:** insert `jobs` row and any `job_skills` rows.

Example request:

```json
{
  "sourceUrl": "https://www.linkedin.com/jobs/view/1234?utm_source=linkedin",
  "originalDescription": "We are hiring a Software Engineer...",
  "title": "Software Engineer",
  "company": "Acme Corp",
  "location": "Bengaluru, India",
  "employmentType": "Full Time",
  "workMode": "Hybrid",
  "experience": "2-4 years",
  "salary": "15-20 LPA",
  "sourcePlatform": "LinkedIn",
  "skills": ["Java", "Spring Boot"]
}
```

Stored `sourceUrl` in the response is typically `https://linkedin.com/jobs/view/1234` (www and utm removed).

---

## `GET /api/v1/jobs`

Paginated list of the current user’s jobs.

**Auth:** JWT required.

**Rate-limit bucket:** `list` if the request has **no** query string; `search` if it has any query string.

**Query parameters:**

| Name | Default | Rules |
|---|---|---|
| `search` | omitted | Optional. Trimmed length max 100. Contains-match on title, company, location, industry, `sourcePlatform`. `%` and `_` are literals |
| `page` | `0` | Integer 0 … 10_000 |
| `size` | `10` | Integer 1 … 50 |
| `sortBy` | `createdAt` | One of: `createdAt`, `updatedAt`, `title`, `company`, `location`, `employmentType`, `workMode`, `experience`, `department`, `education`, `industry`, `sourcePlatform`, `sourceUrl`. **Not** `salary` |
| `sortDir` | `desc` | `asc` (any case) is ascending; any other value is descending |

**Success:** `200` `"Jobs retrieved successfully."` `data` is a Spring `Page` of `JobSummaryResponse`: `content`, `totalElements`, `totalPages`, `number`, `size`, `sort`. Summaries include skills and omit descriptions and `sourceUrl`.

**Errors:** `400` invalid page/size/search/sort; `401`; `429`.

Blank `search` behaves as no search.

---

## `GET /api/v1/jobs/{id}`

Full details for one owned job.

**Auth:** JWT required.

**Rate-limit bucket:** `read`.

**Path:** `id` — numeric job id.

**Success:** `200` `"Job details retrieved successfully."` `data` is `JobResponse`.

**Errors:** `401`; `404`; `429`.

---

## `PUT /api/v1/jobs/{id}`

Full replace. Same body rules as create (`JobRequest`). Recalculates URL hash when `sourceUrl` changes. **Omitting `skills` clears skills.** Omitted optional JSON properties are written as `null` (this is a full replace, not a merge).

**Auth:** JWT required.

**Rate-limit bucket:** `mutate`.

**Success:** `200` `"Job updated successfully."`

**Errors:** `400`; `401`; `404`; `409` duplicate URL; `429`.

---

## `PATCH /api/v1/jobs/{id}`

Partial update (`JobPatchRequest`). Only JSON fields that are present are applied. Size limits match create. There is no `@NotBlank` on the DTO; blank `title`, `company`, or `originalDescription` is rejected in the service (`400` `"Title cannot be blank."` and similar).

- `"skills": []` clears skills
- omitting `skills` leaves the current list
- empty string on optional fields stores the empty value
- `sourceUrl` if present is normalized and uniqueness-checked

**Auth:** JWT required.

**Rate-limit bucket:** `mutate`.

**Success:** `200` `"Job updated successfully."`

**Errors:** `400` (including blank mandatory field or invalid URL); `401`; `404`; `409`; `429`.

---

## `DELETE /api/v1/jobs/{id}`

Permanently deletes the job and its skills.

**Auth:** JWT required.

**Rate-limit bucket:** `mutate`.

**Success:** `200` `"Job deleted successfully."` `data` is omitted/null.

**Errors:** `401`; `404`; `429`.

**Side effects:** row removed; related chat session may cascade-delete at the database (chat-assistant mapping).

---

## Field PATCH routes

All of the following require JWT, use bucket `mutate`, return `200` with `JobResponse`, and share `400` / `401` / `404` / `429`. `PATCH .../source-url` can also return `409`.

| Method and path | Body | Clear / blank behavior | Success message |
|---|---|---|---|
| `PATCH /api/v1/jobs/{id}/title` | `{ "title": "..." }` | `@NotBlank`, max 255 | Job title updated successfully. |
| `PATCH /api/v1/jobs/{id}/company` | `{ "company": "..." }` | `@NotBlank`, max 255 | Job company updated successfully. |
| `PATCH /api/v1/jobs/{id}/location` | `{ "location": "..." }` | `@NotNull`; `""` clears; max 255 | Job location updated successfully. |
| `PATCH /api/v1/jobs/{id}/employment-type` | `{ "employmentType": "..." }` | `@NotNull`; `""` clears; max 100 | Job employment type updated successfully. |
| `PATCH /api/v1/jobs/{id}/work-mode` | `{ "workMode": "..." }` | `@NotNull`; `""` clears; max 50 | Job work mode updated successfully. |
| `PATCH /api/v1/jobs/{id}/experience` | `{ "experience": "..." }` | `@NotNull`; `""` clears; max 100 | Job experience updated successfully. |
| `PATCH /api/v1/jobs/{id}/salary` | `{ "salary": "..." }` | `@NotNull`; `""` clears; max 100 | Job salary updated successfully. |
| `PATCH /api/v1/jobs/{id}/education` | `{ "education": "..." }` | `@NotNull`; `""` clears; max 255 | Job education updated successfully. |
| `PATCH /api/v1/jobs/{id}/department` | `{ "department": "..." }` | `@NotNull`; `""` clears; max 100 | Job department updated successfully. |
| `PATCH /api/v1/jobs/{id}/industry` | `{ "industry": "..." }` | `@NotNull`; `""` clears; max 100 | Job industry updated successfully. |
| `PATCH /api/v1/jobs/{id}/source-platform` | `{ "sourcePlatform": "..." }` | `@NotNull`; `""` clears; max 50 | Job source platform updated successfully. |
| `PATCH /api/v1/jobs/{id}/source-url` | `{ "sourceUrl": "..." }` | `@NotBlank`, max 2000; normalize + dedupe | Job source URL updated successfully. |
| `PATCH /api/v1/jobs/{id}/skills` | `{ "skills": ["Java"] }` | `@NotNull`; `[]` clears; each skill max 255 | Job skills updated successfully. |
| `PATCH /api/v1/jobs/{id}/description` | `{ "description": "..." }` | `@NotNull`; `""` clears; max 50_000 | Job description updated successfully. |
| `PATCH /api/v1/jobs/{id}/original-description` | `{ "originalDescription": "..." }` | `@NotBlank`; max 50_000 | Job original description updated successfully. |

Missing JSON property on a field route fails bean validation (`@NotNull` / `@NotBlank`), not “leave unchanged”. These routes are always a write of that one attribute.

Example clear location:

```json
{ "location": "" }
```

Example clear skills:

```json
{ "skills": [] }
```

---

## `JobResponse` fields

Returned by create, get-by-id, and all updates:

`id`, `sourceUrl` (canonical), `originalDescription`, `description`, `title`, `company`, `location`, `employmentType`, `workMode`, `experience`, `salary`, `education`, `department`, `industry`, `sourcePlatform`, `skills`, `createdAt`, `updatedAt`.

## Common error shapes

Malformed JSON: `400` `"Request body is missing or malformed JSON."`

Bean validation: `400` with messages like `title: Title cannot be blank.` (field name prefix from `MethodArgumentNotValidException`).

Invalid URL: `400` `"Job URL must be a valid absolute http or https link."` or `"Job URL must not be empty."`

Type mismatch on path/query: `400` `"Invalid request parameter."` (jobs path variable is `id`, not `jobId`).

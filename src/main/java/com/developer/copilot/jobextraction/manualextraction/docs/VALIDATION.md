# Validation

Rules that are actually enforced on the parse path. Categories match the implementation: HTTP input, URL/security shape, business rules, mapper post-processing.

## 1. Input validation (Bean Validation)

`JobExtractionController.parseJobInfo` uses `@Valid @RequestBody JobExtractionRequest`. Failures → `400` with `field: message` (comma-separated if several).

| Field | Annotations | Message |
| --- | --- | --- |
| `sourceUrl` | `@NotBlank` | `Job URL cannot be blank.` |
| `sourceUrl` | `@Size(max = 2000)` | `Job URL cannot exceed 2000 characters.` |
| `rawJobText` | `@NotBlank` | `Pasted job text cannot be blank.` |
| `rawJobText` | `@Size(max = 50000)` | `Pasted job text cannot exceed 50000 characters.` |

`50000` is `JobLimits.MAX_DESCRIPTION_LENGTH` via `JobExtractionLimits.MAX_DESCRIPTION_LENGTH`. Max length **is** accepted (`50000` returns 200 in controller tests when the service is stubbed).

There is **no** `@Pattern` on `sourceUrl`. `javascript:alert(1)` is not blank and is short enough; it fails later in URL normalization.

Missing body / malformed JSON → `400` `Request body is missing or malformed JSON.`

Whitespace-only strings fail `@NotBlank`.

Unknown JSON properties are ignored; they are not a validation error.

## 2. URL validation (after Bean Validation)

`UrlNormalizationUtil.normalizeStrict` (shared `common` util):

| Rejected | Result |
| --- | --- |
| Null/blank (if it reached the util) | `Job URL must not be empty.` |
| Not parseable as URI | `Job URL must be a valid absolute http or https link.` |
| Scheme not `http`/`https` | Same fixed message (`javascript:`, `data:`, `ftp:`, …) |
| Missing host (`https:///path`) | Same fixed message |

Accepted examples from tests: `http://localhost/jobs/1`; `https://example.com:443/jobs/42/` canonicalizes to `https://example.com/jobs/42`.

Tracking query names are **stripped**, not rejected (`utm_*`, `share_id`, `access_token`, `token`, `auth`, and others listed in the util). Remaining query keys are sorted. That is canonicalization, not a 400.

See [URL-AND-DEDUPLICATION.md](JOBEXTRACTION-SERVICE-SPECIFIC-DOCS/URL-AND-DEDUPLICATION.md).

## 3. Security-related validation

| Check | Where | Outcome |
| --- | --- | --- |
| JWT / enabled / emailVerified | `JwtAuthenticationFilter` | Unauthenticated → `401` |
| Principal present | `CurrentUserService` | `401` |
| `emailVerified == true` | `JobExtractionServiceImpl` | `403` |
| Rate limit | Filter | `429` |
| Unsafe URI schemes in **extracted** fields | Mapper `blankUnsafeUriScheme` | Field becomes `""` (may set `requiresManualReview` if title) |
| Control characters in extracted fields | Mapper `stripControls` | C0 controls removed except `\t` `\n` `\r` |

HTML in AI title is **kept** as text. Validation does not HTML-encode.

## 4. Business validation

| Rule | Implementation | Outcome |
| --- | --- | --- |
| This user has not already saved this canonical URL | `existsByUserIdAndSourceUrlHash` | `409` |
| Duplicate is per user | Query includes `currentUser.getId()` only | Other users' rows ignored |
| Title/company quality for save | Mapper `requiresManualReview` | Still **200**; flag for the UI |

`requiresManualReview` is true when title or company is blank (null or `isBlank()` after clip) **or** title/company was truncated to max length. Empty salary or other fields do **not** set the flag.

`JobRequest` requires non-blank title and company on save. The flag exists so the client can force those fields before `POST /api/v1/jobs`. This module does not call jobs validation.

## 5. Mapper limits (not HTTP 400)

AI strings are clipped so save `@Size` will pass:

| Field | Max |
| --- | --- |
| description | 50000 |
| title, company, location, education | 255 |
| employmentType, experience, salary, department, industry | 100 |
| workMode, sourcePlatform | 50 |
| each skill | 255 |
| skill count | 50 |

Null AI `skills` → empty list (never null). Blank skills after clip are omitted. Extra skills beyond 50 are dropped. Skill-list clipping does not set `requiresManualReview`.

Truncating **workMode** (or other non-title/company fields) does not set `requiresManualReview`.

## 6. AI request DTO (internal)

`JobExtractionAiRequest` documents `@Size(max = 2048)` for URL and `100000` for text. The HTTP layer is stricter (2000 / 50000), so those AI annotations are not the user-facing contract. This service always sends the **canonical** URL and the original paste.

## 7. Related uniqueness

Database uniqueness `uk_job_user_source_url_hash` is enforced on **save** in `jobs`. Parse only pre-checks with `existsBy...`. A race (two saves, or parse then save from another tab) can still hit jobs-layer 409.

## Validation-related exceptions

- `MethodArgumentNotValidException` — input
- `InvalidJobUrlException` — URL shape
- `DuplicateJobException` — business uniqueness
- `EmailNotVerifiedException` — identity
- Mapper does not throw for oversize AI text; it clips

# Validation

Rules that are actually enforced on the automated parse path. Categories match the implementation: HTTP input, URL shape, SSRF, fetch/content quality, business rules, and downstream mapper limits.

## 1. Input validation (Bean Validation)

`AutomatedJobExtractionController.parseJobUrl` uses `@Valid @RequestBody AutomatedJobExtractionRequest`. Failures → `400` with `field: message` (comma-separated if several).

| Field | Annotations | Message |
| --- | --- | --- |
| `sourceUrl` | `@NotBlank` | `Job URL cannot be blank.` |
| `sourceUrl` | `@Size(max = 2000)` | `Job URL cannot exceed 2000 characters.` |

`2000` is `JobExtractionLimits.MAX_URL_LENGTH` via `AutomatedJobExtractionLimits.MAX_URL_LENGTH`.

There is **no** `@Pattern` on `sourceUrl`. `javascript:alert(1)` is not blank and is short enough; it fails later in URL normalization.

Missing body / malformed JSON → `400` `Request body is missing or malformed JSON.`

Whitespace-only strings fail `@NotBlank`.

Unknown JSON properties are ignored; they are not a validation error.

There is no `rawJobText` on this DTO.

## 2. URL validation (after Bean Validation)

`UrlNormalizationUtil.normalizeStrict` (shared `common` util):

| Rejected | Result |
| --- | --- |
| Null/blank (if it reached the util) | `Job URL must not be empty.` |
| Not parseable as URI | `Job URL must be a valid absolute http or https link.` |
| Scheme not `http`/`https` | Same fixed message (`javascript:`, `data:`, `ftp:`, …) |
| Missing host (`https:///path`) | Same fixed message |

Tracking query names are **stripped**, not rejected (`utm_*`, `share_id`, `access_token`, `token`, `auth`, and others listed in the util). Remaining query keys are sorted. That is canonicalization, not a 400.

Unlike manual parse tests, **localhost is not accepted** for automated parse: it is a valid absolute URL, then SSRF rejects it as `INVALID JOB URL`.

## 3. SSRF / security validation

| Check | Where | Outcome |
| --- | --- | --- |
| JWT / enabled / emailVerified | `JwtAuthenticationFilter` | Unauthenticated → `401` |
| Principal present | `CurrentUserService` | `401` |
| `emailVerified == true` | `AutomatedJobExtractionServiceImpl` (and gateway) | `403` |
| Rate limit | Filter | `429` |
| Public http(s) only, no user-info, no literal IP, public DNS | `SsrfProtectionService` | `400` `INVALID JOB URL` |
| Same SSRF on each redirect | `JobPageFetcher` | `400` `INVALID JOB URL` |
| Unsafe URI schemes in **AI** fields | Manual `JobExtractionMapper` | Field becomes `""` |

See [SSRF-AND-PAGE-FETCH.md](AUTOMATEDJOBEXTRACTION-SERVICE-SPECIFIC-DOCS/SSRF-AND-PAGE-FETCH.md).

## 4. Fetch and job-content validation

These are **not** Bean Validation. They all surface as `INVALID JOB URL` unless noted.

| Rule | Implementation | Outcome |
| --- | --- | --- |
| Redirect hop budget | `maxRedirects` (default 3) | `INVALID JOB URL` |
| Redirect `Location` present and http(s) | `resolveRedirect` | `INVALID JOB URL` |
| 401 / 403 / 404 / 407 / 410 / 451 / other 4xx | `JobPageFetcher` | `INVALID JOB URL` |
| Non-2xx that is not 4xx (e.g. 502) | `JobPageFetcher` | `502` fetch exception |
| Body over `maxResponseBytes` | `JdkJobPageHttpClient.readLimited` | `502` fetch exception |
| Challenge HTML (`captcha`, `just a moment`, …) **without** job markers | `looksLikeAccessChallenge` && !`looksLikeJob` | `INVALID JOB URL` |
| Empty/blank body | Pipeline | `INVALID JOB URL` |
| Formatted text shorter than 120 characters | `JobContentQualityValidator` | `INVALID JOB URL` |
| Listing page (many job links, no single-job body) | `looksLikeListingPage` | `INVALID JOB URL` |
| Identifiable job | structured data + title/company, or title+description, or title+markers, or job-like path + markers | continue |

Quality rules are detailed in [EXTRACTION-PIPELINE.md](AUTOMATEDJOBEXTRACTION-SERVICE-SPECIFIC-DOCS/EXTRACTION-PIPELINE.md).

## 5. Business validation (gateway)

| Rule | Implementation | Outcome |
| --- | --- | --- |
| This user has not already saved this canonical URL | `existsByUserIdAndSourceUrlHash` | `409` |
| Duplicate is per user | Query includes `currentUser.getId()` only | Other users' rows ignored |
| Title/company quality for save | Mapper `requiresManualReview` | Still **200**; flag for the UI |

`requiresManualReview` is true when title or company is blank after clip **or** title/company was truncated. Empty salary or other fields do **not** set the flag.

## 6. Extracted-text and mapper limits (not HTTP 400 from this controller)

`ExtractedJobTextFormatter` clips labeled text to `MAX_EXTRACTED_TEXT_LENGTH` (50,000) so the gateway’s `rawJobText` fits `JobExtractionLimits.MAX_DESCRIPTION_LENGTH`.

AI strings are then clipped in `JobExtractionMapper` so save `@Size` will pass (title 255, workMode 50, skills 50×255, …). Mapper does not throw for oversize AI text; it clips.

## 7. Related uniqueness

Database uniqueness `uk_job_user_source_url_hash` is enforced on **save** in `jobs`. Parse only pre-checks with `existsBy...`. A race (two saves, or parse then save from another tab) can still hit jobs-layer 409.

## Validation-related exceptions

- `MethodArgumentNotValidException` — input
- `InvalidJobUrlException` — URL shape
- `InvalidAutomatedJobUrlException` — SSRF, fetch 4xx, quality
- `DuplicateJobException` — business uniqueness
- `EmailNotVerifiedException` — identity

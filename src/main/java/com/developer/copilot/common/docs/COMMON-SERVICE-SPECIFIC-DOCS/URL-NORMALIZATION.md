# Job URL normalization

`UrlNormalizationUtil` turns messy job-board links into a **single canonical string**, then optionally a **SHA-256 hex** digest for indexing. `jobs` and `jobextraction` use this so two tracking links for the same posting compare equal.

It is a Spring `@Component`. It does not call the network.

## Why it exists

Users paste LinkedIn, Naukri, Workday, and career-site URLs that differ by `www.`, `utm_*`, `fbclid`, query order, trailing slashes, or `http` vs `https`. Duplicate detection on the raw string would miss matches or collide incorrectly. Canonical `source_url` plus a fixed-length hash (`sha256Hex`) is what those services persist.

## API

| Method | Behavior |
|---|---|
| `normalizeStrict(raw)` | Trim; require absolute http/https. Throws `InvalidJobUrlException` otherwise. |
| `normalizeLenient(raw)` | null → null; blank → `""`; well-formed http(s) canonicalized; other garbage returned trimmed; **unsafe schemes still throw**. |
| `sha256Hex(value)` | SHA-256 hex of UTF-8 bytes; null → null; 64 hex characters. |

Production callers today (`JobServiceImpl`, `JobExtractionServiceImpl`) use **strict** + `sha256Hex`. Lenient is tested and available for a softer persist path; it is not wired in those services.

## Canonicalization (once the URL is valid http/https)

```mermaid
flowchart TD
    U[Parsed URI] --> S[Lowercase scheme]
    S --> H[Lowercase host, strip leading www. if longer than www.]
    H --> P[Drop default ports 80/443]
    P --> Path[Path empty becomes / ; strip trailing slash if length greater than 1]
    Path --> Q[Query: drop tracking names and utm_ prefix; TreeMap sort remaining]
    Q --> OUT[scheme://host[:port]path[?query]]
```

Also dropped (not part of identity):

- **Fragment** (`#section`)
- **User info** (`user:pass@`) so credentials never become the stored URL

`http` and `https` stay different on purpose. Duplicate query keys: last value wins (`TreeMap.put`).

Root URLs `https://example.com` and `https://example.com/` both become `https://example.com/`.

## Tracking parameters removed

Exact names (case-insensitive), including:

- `utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, `utm_id`, `utm_name`
- Click ids: `fbclid`, `gclid`, `gclsrc`, `dclid`, `msclkid`, `yclid`, `twclid`, `irclickid`, `igshid`
- Share/ref: `share_id`, `shareid`, `share`, `shared_from`, `ref`, `ref_src`, `ref_url`, `referrer`, `referral_code`
- Misc: `src`, `trk`, `trkinfo`, `trackingid`, `tracking_id`, `mc_cid`, `mc_eid`, `spm`, `si`, `_hsenc`, `_hsmi`, `originalsubdomain`, `pagenumber`, `prehotel`
- Secrets in query: `token`, `access_token`, `auth`

Any query name starting with `utm_` is dropped. A name like `job_utm_id` is **kept** (it does not start with `utm_`).

Parameters such as `jobId`, `source`, and `position` are kept; different `source=` values are different canonical URLs.

## Errors

| Input | Exception message |
|---|---|
| null or blank (strict) | `Job URL must not be empty.` |
| Not absolute http/https, including `javascript:` / `ftp:` / missing host | `Job URL must be a valid absolute http or https link.` |

Messages do **not** include the raw URL (verified in tests for javascript and `<script>` in the host). HTTP mapping: 400 via `GlobalExceptionHandler`.

Lenient still throws that second message for `javascript`, `data`, `file`, and `vbscript` so those schemes cannot be stored.

## Hashing

`sha256Hex` is a plain digest of whatever string you pass (usually the canonical URL). It is not HMAC. Jobs use it as a stable uniqueness key when the canonical URL itself may be long.

## Example (from tests)

Input:

`https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W?share_id=LinkedIn_corporate_page`

Strict output:

`https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W`

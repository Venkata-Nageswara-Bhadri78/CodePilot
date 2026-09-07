# Testing

User-service tests live under `src/test/java/com/developer/copilot/user/`. They are JUnit 5 tests. Most service and parser tests are Mockito unit tests. Security tests use `@WebMvcTest` with the real `SecurityConfig` and mocked JWT/user beans. This document does not claim a coverage percentage.

When you add behavior, start from the nearest existing test class in the table below rather than introducing a new style unless the layer is new.

## Layout

| Area | Package / classes | Style |
|---|---|---|
| Public controllers | `controller/UserControllerTest`, `UserProfileControllerTest` | Standalone `MockMvc` + `GlobalExceptionHandler` |
| Internal controller | `controller/InternalResumeControllerTest` | Same |
| HTTP security | `UserSecurityTest`, `InternalResumeSecurityTest` | `@WebMvcTest` + `SecurityConfig` |
| Production Swagger | `UserProductionSecurityTest` | `@WebMvcTest`, profile `prod` |
| Profile / resume services | `service/UserProfileServiceImplTest`, `UserServiceImplTest` | Mockito |
| Parse orchestration | `service/ResumeParsingServiceImplTest` | Mockito |
| Parse pipeline | `service/parsing/*` | Mockito + real PDFBox fixtures |
| Rate limiting | `ratelimit/filter/UserRateLimitFilterTest`, `ratelimit/service/impl/UserRateLimitServiceImplTest` | Direct filter + Mockito Redis |
| Redis keys/service | `redis/key/UserRedisKeyBuilderTest`, `redis/service/impl/UserRedisServiceImplTest` | Unit |
| Validation | `validation/HttpOrHttpsUrlValidatorTest` | Direct validator |
| Exceptions | `exception/UserExceptionMappingTest` | Handler methods directly |
| Filename util | `util/ResumeFilenameUtilTest` | Unit |

There are **no** `@SpringBootTest` / Testcontainers tests in this package. Persistence and MinIO are mocked at the repository and `FileStorageService` boundaries.

## What is covered

### Controllers

- Resume upload returns `201`; list `200`; download sets `Content-Disposition` from a safe filename and falls back to `resume.pdf` on CR/LF names.
- Profile create accepts `{}`; long headline is `400`; javascript/data URLs on links/projects are `400`; valid `https` link is `201`; missing company / inverted years / year `< 1900` are `400`; unknown experience id is `404`; child cap is `400`.
- Internal GET returns parsed JSON; high-priority path calls `getParsedResume(null)`; unknown resume `404`; failed/pending parse `422`.

### Security

- Profile and resume GETs without `Authorization` are `401` and never call the service.
- Garbage JWT, unverified email, and disabled user are `401` on profile GET.
- Create profile without auth is `401`.
- Internal parse without JWT is `401` even if `X-Internal-Api-Key` is present; garbage JWT and unverified email are `401`.
- Under profile `prod`, `/v3/api-docs`, `/v3/api-docs/user`, `/v3/api-docs/internal`, and `/swagger-ui/index.html` are not `200`.

These tests mock `JwtService` / `UserRepository`; they do not mint real tokens.

### Profile and resume services

- Duplicate profile, missing profile, PUT null headline clears the field, child cap, delete experience, profile delete deletes resume rows and storage keys.
- Upload: missing profile, non-PDF, empty, oversize, filename too long, PNG content type, max count, duplicate checksum (object deleted), unique-constraint race, save failure deletes object, first resume is primary, second is not, unsanitary filename stored as allowlisted default.
- Delete resume promotes the most recent remaining; last resume does not promote.
- Download returns original filename; missing resume throws.
- Set high-priority clears the previous primary.

### Parsing

- `ResumeParsingServiceImpl`: completed cache hit skips parse; failed/pending matching version throw; stale `parserVersion` re-parses; cache miss parses and `persistAsync`; missing object → `ResumeNotFoundException`; null id uses high-priority; foreign resume → not found; initialize creates PENDING once; deletes delegate to the repository.
- `ResumeParser`: success populates the record; all attempts fail → `FAILED`; last-attempt success; configured attempt limit; storage failures retried; existing row updated in place; new PENDING starts at 0 attempts.
- `ResumeTextExtractor`: text + page count, newline normalization, truncation, image-only, extract-forbidden, password, non-PDF, empty, too many pages (real PDFBox documents).
- `ResumeSectionParser`: headings, synonyms, compound headings, decorations, contact/phone/email/github/linkedin, labelled header fields, non-phone digit runs, no headings → contact block, blank input.
- `ResumeContextTextRenderer`: profile layout, section order, strip duplicate contact lines, raw-text fallback, null for PENDING/FAILED.
- `ResumeParsedDataWriter`: insert, in-place update, skip if resume deleted.

### Rate limiting and Redis

- Filter: upload limited per IP and per user across IPs; internal parse limited; delete limited; `PATCH` high-priority and `GET` list are not counted; registration URL patterns include resume and internal parse paths.
- Service: blocks after limit; isolates identities; zero limit always allows; Redis increment used when present; Redis throw falls back to memory.
- Keys: colon sanitizing, blank identity → `unknown`; increment/TTL use namespaced keys.

### Other

- `HttpOrHttpsUrlValidator`: http(s) valid; javascript/data/file rejected; blank valid on the validator itself.
- `UserExceptionMappingTest`: max upload size uses configured MB; profile cap `400`; pending parse `422`.
- `ResumeFilenameUtilTest`: allowlist, CR/LF/quotes/paths/spaces, blank, length 255.

## Gaps a new developer should know

- No integration test against a real MySQL unique constraint or MinIO bucket.
- Internal key filter success path is not a `@WebMvcTest` in this package (JWT failure is). Key-filter behavior is covered in common tests if present.
- Profile controller tests do not exercise every child collection; experience/link/project cover the representative validation.
- Background `@Async` worker is not run on a real thread pool in tests; `ResumeParsingServiceImpl` mocks the worker.

## Adding tests

| Change | Add or extend |
|---|---|
| New public JSON field / validation | `UserProfileControllerTest` or a focused validator test |
| New resume upload rule | `UserServiceImplTest` + maybe `UserControllerTest` |
| New parse heuristic | `ResumeSectionParserTest` / `ResumeTextExtractorTest` |
| New internal contract field | `InternalResumeControllerTest` + mapper/renderer tests |
| New limited HTTP method | `UserRateLimitFilterTest` (`bucketFor` / registration) |
| New exception HTTP status | `UserExceptionMappingTest` and handler |
| New security rule | `UserSecurityTest` or `InternalResumeSecurityTest` |

Keep security tests on `@WebMvcTest` so filters actually run. Keep parser tests independent of Spring so PDFBox cases stay fast.

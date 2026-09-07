# Jobs Testing

Jobs tests live under `src/test/java/com/developer/copilot/jobs/`. They are mostly unit and slice tests (Mockito, standalone `MockMvc`, `@WebMvcTest`). They do not claim a coverage percentage.

Use this map to see what is already locked in and where to add tests for new behavior.

## Test layout

| Area | Class | What it proves |
|---|---|---|
| Controller / validation | `JobControllerTest` | Bean validation, duplicate/not-found mapping, list query rejection, empty location/skills accepted |
| Security (no JWT) | `JobSecurityTest` | `401` and service never called |
| Production Swagger | `JobProductionSecurityTest` | API docs / Swagger UI not public `200` on `prod` |
| Service behavior | `JobServiceImplTest` | URL normalize/hash, duplicates, integrity mapping, PATCH/PUT skills, search escape, unsafe URLs |
| Ownership | `JobOwnershipIsolationTest` | Foreign id → `JobNotFoundException` on every owned operation; list uses caller `userId` |
| Exceptions | `JobsExceptionMappingTest` | Handler status codes including jobs `429` + `Retry-After` |
| Mapper | `JobMapperTest` | URL not set by mapper; skills copy; PUT vs PATCH skills; summary omits descriptions |
| Sort | `JobSortSupportTest` | Allow-list, salary/hash/user path rejected, non-`asc` is desc |
| Query | `JobQuerySupportTest` | Paging bounds, LIKE escape, blank search → null |
| Rate-limit filter | `JobsRateLimitFilterTest` | IP limit, user limit across IPs, search vs list, mutate bucket, other paths skipped |
| Rate-limit service | `JobsRateLimitServiceImplTest` | Memory block, Redis path, Redis failure fallback, `limit=0` permit, `consumeOrThrow` |
| Redis keys | `JobsRedisKeyBuilderTest` | Colon sanitization, blank → `unknown` |
| Redis service | `JobsRedisServiceImplTest` | Namespace passed through to repository |

## Controller tests

`JobControllerTest` uses standalone `MockMvc` plus `GlobalExceptionHandler` (no security filter). It covers:

- Create missing title/company → 400, service not called
- Oversized skill, source URL, original description, title → 400
- Duplicate exception → 409 with the official duplicate message
- Get by id not found → 404
- Salary too long / blank title on field routes → 400
- Empty location string and empty skills array → 200
- Invalid `sortBy` including `salary`, `user.password`, `sourceUrlHash` → 400
- Illegal `size` / `page` / long `search` → 400 before service
- Valid `sortBy=title&sortDir=asc` reaches the service
- Malformed JSON → 400 envelope message

When adding a new jobs route, add at least: validation 400, and a success path if the mapping is non-obvious.

## Security tests

`JobSecurityTest` is `@WebMvcTest(JobController)` with `SecurityConfig`. Unauthenticated GET/POST/PUT/PATCH/DELETE and `PATCH .../location` return 401. A garbage Bearer token returns 401 and does not call `JobService`.

`JobProductionSecurityTest` uses `@ActiveProfiles("prod")` and asserts Swagger/OpenAPI URLs are not `200`.

These tests do **not** exercise a happy-path authenticated MVC call (JWT is mocked but not issued). Authenticated behavior is covered at the service layer.

## Service tests

`JobServiceImplTest` is the behavioral spec for:

- Tracking params / www / hash length 64 on create
- Duplicate pre-check skips `save`
- `javascript:`, `data:`, `file:` URLs → `InvalidJobUrlException`
- Unique-constraint `DataIntegrityViolationException` → `DuplicateJobException`; other constraints rethrown
- Null skills → empty list
- Unauthenticated `CurrentUserService` → `InvalidCredentialsException`, no repository id lookup
- PATCH blank title rejected; salary-only patch; omit vs replace skills
- PUT omit/empty skills clears
- Search `" 100% "` binds as `100\%`; `"%"` is not match-all
- Blank search uses `findAllByUserId`
- `updateSourceUrl` recalculates canonical URL and hash
- Delete uses `delete(entity)`, not a bulk JPQL wipe

`JobOwnershipIsolationTest` is the isolation spec: user 2 never loads user 1’s job id `100`; no `save`/`delete`/`findById`.

New business rules on create/update should get a method here, not only a controller test.

## Rate limit and Redis tests

Filter tests use an in-memory limiter (`JobsRedisService` null) and tight limits. They show POST counted per IP and per user, GET+query using the search budget, PATCH/PUT sharing mutate, and `/api/v1/auth/login` ignored.

Service tests mock Redis increment/TTL and prove fallback when Redis throws.

Key builder tests are the contract for IPv6 in keys.

## Mapper and util tests

Mapper tests document an important invariant: **mapping must not set `sourceUrl`**. If that invariant breaks, duplicate detection can be skipped or hashed on the raw URL.

Util tests are cheap places to add new sort fields or limit changes.

## How to extend tests

| You changed | Add coverage in |
|---|---|
| New endpoint | `JobControllerTest` + `JobSecurityTest` 401 |
| Ownership query | `JobOwnershipIsolationTest` |
| URL / duplicate / PATCH blank | `JobServiceImplTest` |
| New exception type | `JobsExceptionMappingTest` |
| New rate-limit bucket | `JobsRateLimitFilterTest` |
| Redis key shape | `JobsRedisKeyBuilderTest` |
| New sort field | `JobSortSupportTest` and controller invalid-sort if needed |

Run the jobs slice with Maven, for example:

```bash
./mvnw test -Dtest=com.developer.copilot.jobs.**
```

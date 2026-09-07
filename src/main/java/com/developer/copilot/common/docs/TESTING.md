# Testing

Tests for this package live under `src/test/java/com/developer/copilot/common/`. They are mostly **unit tests** (plain JUnit 5, Mockito, MockMvc-style servlet mocks). They do not start the full Spring Boot application or a real MinIO/Redis.

This document does not claim a coverage percentage.

## Layout

```
src/test/java/com/developer/copilot/common/
├── config/SwaggerConfigTest.java
├── dto/ApiResponseTest.java
├── exception/GlobalExceptionHandlerTest.java
├── exception/GlobalExceptionHandlerAiTest.java
├── ratelimit/filter/InternalApiRateLimitFilterTest.java
├── ratelimit/service/impl/CommonRateLimitServiceImplTest.java
├── redis/key/CommonRedisKeyBuilderTest.java
├── redis/service/impl/CommonRedisServiceImplTest.java
├── security/impl/CurrentUserServiceImplTest.java
├── security/internal/InternalApiKeyFilterTest.java
├── security/internal/InternalApiStartupValidatorTest.java
├── storage/config/StorageConfigTest.java
├── storage/service/impl/FileStorageServiceImplTest.java
├── storage/startup/StorageStartupValidatorTest.java
├── storage/util/ChecksumUtilTest.java
└── util/UrlNormalizationUtilTest.java
```

## Categories and what they lock in

### Security and internal API

`InternalApiKeyFilterTest` — valid/missing/wrong key; blank configured key fail-closed; skip check on `local`/`dev` (including `LOCAL`); reject when disabled on default/`staging`; custom header; constant-time length mismatch; identical client bodies for disable vs wrong key; previous-key rotation; filter URL patterns; not a `@Component`; null `Environment`.

`InternalApiStartupValidatorTest` — prod/production/staging/no-profile reject disabled API, blank/short/placeholder keys (including long “change-me” / “example” strings); strong key and padded strong key succeed; laptop may disable or use a short key; previous-key must also be strong outside laptop.

`CurrentUserServiceImplTest` — missing auth, unauthenticated token, non-`CustomUserDetails` principal, null principal, null `User` on details → `InvalidCredentialsException`; happy path returns the `User`.

### Rate limiting and Redis helpers

`InternalApiRateLimitFilterTest` — per-service-key hallway across paths; per-user hallway across paths; non-internal path not limited; zero limits always allow; registration patterns.

`CommonRateLimitServiceImplTest` — in-memory block after limit; `consumeOrThrow`; zero limit; Redis INCR/TTL path; fallback to memory when Redis throws.

`CommonRedisKeyBuilderTest` — colon sanitization (IPv6), blank identity → `unknown`.

`CommonRedisServiceImplTest` — increment/TTL delegate with namespaced keys.

### Storage

`FileStorageServiceImplTest` — traversal and unsafe charset rejected before MinIO; PDF magic/extension/content-type matrix; empty file; UUID key under folder; backslash and slash normalization; JWT ownership allow/deny; download missing → `StorageObjectNotFoundException`; `exists` false for `NoSuchKey`/`NoSuchObject`/`NotFound`; other codes and connection errors → `StorageException`; bucket init create vs fail.

`StorageStartupValidatorTest` — remote `minioadmin` and remote auto-create fail without laptop profile; loopback admin allowed; `local` allows remote admin; loopback detection.

`StorageConfigTest` — provider `minio`/`s3`/blank OK; `ftp` fails; `MinioClient` builds without connecting.

`ChecksumUtilTest` — known SHA-256 digests for `"hello world"` and empty input; stability.

### Error handling

`GlobalExceptionHandlerTest` — validation join; data-integrity message without SQL; malformed JSON; catch-all 500 + ERROR log; invalid file WARN not ERROR; storage 500 hides bucket; IllegalArgument allow-list vs 500; job URL 400; credentials 401; email 503 hides SMTP; all module 429s set `Retry-After`; email-not-verified 403; `jobId` vs other type mismatch; upload size 10 MB vs default 5; multipart vs size; empty constraint set; 405/missing param; 409 types; storage not-found 404; **classpath scan** that every custom runtime exception has a handler.

`GlobalExceptionHandlerAiTest` — AI 502/503, resume pending 409, parse 422, job/resume 404, rate limit, IllegalArgument, storage hide.

### OpenAPI, DTO, URLs

`SwaggerConfigTest` — Bearer + InternalApiKey on one `Components`; `ApiErrorResponse` / `ApiResponse`; no global InternalApiKey requirement; public auth customizer; `@Profile` excludes prod/production; no `publicOpenApi` method.

`ApiResponseTest` — builder fields, null data, timestamp on failure.

`UrlNormalizationUtilTest` — tracking strip, www, ports, slash, query sort, fragments, user-info, javascript without echoing input, strict vs lenient, SHA-256 length, duplicate query keys, `source` kept as identity.

## How to add tests for new common behavior

| If you add… | Extend or add… |
|---|---|
| A new `@ExceptionHandler` | Cases in `GlobalExceptionHandlerTest` (status, client message, no secret leak). The scan test will fail if you add an exception class without a handler. |
| Internal API behavior | `InternalApiKeyFilterTest` / startup validator tests (laptop vs not). |
| Hallway limits or Redis key shape | Filter + `CommonRateLimitServiceImplTest` + key builder tests. |
| Storage rules | `FileStorageServiceImplTest` (reject **before** `verify(minioClient, never())`). |
| URL canonicalization | `UrlNormalizationUtilTest` with expected canonical strings. |

Prefer mocks over live MinIO/Redis. Production-profile fail-closed behavior is already expressed with `MockEnvironment` profiles, not Docker.

## Related tests outside this package

Internal resume HTTP tests (`user` `InternalResumeControllerTest`, `InternalResumeSecurityTest`) exercise JWT + key together. They are not common-package tests but they are the end-to-end check of the hallway plus a real controller.

# Testing

AI tests live under `src/test/java/com/developer/copilot/ai/`. They are unit and slice tests (Mockito, standalone `MockMvc`, `@WebMvcTest`). There is no claim here of a coverage percentage.

Use them as the contract when changing prompts, grounding, errors, or filters.

## Layout

| Area | Class | What it locks in |
|---|---|---|
| HTTP mapping and errors | `controller/AiControllerTest` | 200/400/401/404/409/422/502/503/500; health vs config; SSE event names |
| JWT security chain | `controller/AiSecurityTest` | Unauthenticated, bad token, unverified email, disabled user; unknown JSON fields ignored |
| Orchestration | `service/impl/AiServiceImplTest` | Grounding precedence, job text fallback, empty resume chat, sanitized provider errors, stream filter/timeout, extraction, job-chat history trim |
| Resume adapter | `service/context/DefaultResumeContextServiceImplTest` | COMPLETED / PENDING / FAILED / empty text / in-progress exception |
| Prompts | `service/context/PromptTemplateServiceTest` | Modes, untrusted-data delimiters, extraction empty-field rules |
| DTO Bean Validation | `dto/AiRequestValidationTest` | Sizes, temperature, nested job-chat turns |
| Config | `config/AiPropertiesTest` | Timeout alias; cover-letter token cap |
| Circuit / bulkhead | `resilience/AiChatGuardTest` | 3 failures open circuit; success resets; stream ERROR chunks count |
| Rate-limit filter | `ratelimit/filter/AiRateLimitFilterTest` | Per-IP, per-user across IPs, resume-context vs chat, health not limited |
| Rate-limit store | `ratelimit/service/impl/AiRateLimitServiceImplTest` | Memory window, Redis path, Redis failure fallback, `consumeOrThrow` |
| Redis keys / service | `redis/key/AiRedisKeyBuilderTest`, `redis/service/impl/AiRedisServiceImplTest` | Colon sanitizing, namespaced increment/TTL |

## Controller tests (`AiControllerTest`)

Standalone `MockMvc` with `GlobalExceptionHandler` — **not** the full security filter chain.

Covered behaviors:

- Valid chat → `200`, `data.content` / `data.model`.
- Blank prompt, missing prompt, malformed JSON, empty body, invalid `mode`, oversized prompt → `400` and **no** service call.
- Unauthenticated `CurrentUserService` → `401`, service not called.
- `AiServiceException` → `502`; `AiUnavailableException` → `503`; unexpected `RuntimeException` → `500` `Something went wrong.`
- `JobNotFoundException` / `ResumeNotFoundException` / `UserProfileNotFoundException` → `404`.
- `AiResumePendingException` → `409`; `ResumeParsingException` → `422`.
- Resume-context success, 404, 409, 422.
- Health and config: `healthCheckType=configuration`, `status=UP`, `streamingSupported=true`.
- Stream: `event:message` then `event:done`; terminal `ERROR` → `event:error`; completed without `finishReason` → `done`; missing job **before** SSE → JSON `404`.

## Security tests (`AiSecurityTest`)

`@WebMvcTest(AiController)` with `SecurityConfig` and `SecurityBeansConfig`. JWT and user repository are mocked.

Covered:

- No `Authorization` on chat, health, resume-context → `401` `Unauthorized.`
- Garbage Bearer token (`JwtException`) → `401`.
- Email not verified / user disabled → `401`, service not called.
- Valid JWT + `unexpectedField` in JSON → `200` (unknown properties ignored).

## Service tests (`AiServiceImplTest`)

The important product rules:

- All `AiMode` values call `buildSystemPrompt` for that mode.
- `customResumeText` and `jobDescription` skip resume service and `JobRepository`.
- Owned job prefers `description`, then `originalDescription`; both blank still calls the provider with empty JD.
- Missing job or null `userId` with `jobId` → `JobNotFoundException`, no provider call.
- Missing stored resume without `resumeId` → empty context + `recordMissingResume`.
- Explicit missing `resumeId` → not found, no provider call.
- Provider 401/404/quota/timeout/high-demand → friendly messages; secrets and config paths stripped.
- Cover letter `maxTokens` 4096 vs 2048; temperature applied only when set.
- Guard `AiUnavailableException` propagated; stream empty tokens filtered; stream errors become `ERROR` chunks without leaking the cause; stream timeout → `ERROR`.
- Extraction: structured entity, null entity, generic failure, temperature `0.0`.
- Job chat: first turn is only the new prompt; prior turns rebuild user/assistant pairs; 20 turns trim to last 16 plus the new prompt (33 messages); 41 turns rejected; blank turn rejected; custom resume skips resume service; unknown/blank email → `Job not found.`

## Where to add tests

| Change | Add or extend |
|---|---|
| New HTTP field or status | `AiControllerTest`, `AiRequestValidationTest` |
| New auth rule | `AiSecurityTest` |
| Grounding or provider mapping | `AiServiceImplTest` |
| Parse-state mapping | `DefaultResumeContextServiceImplTest` |
| Prompt wording that must stay | `PromptTemplateServiceTest` |
| Limit numbers or Redis key format | rate-limit / redis tests |
| Circuit thresholds | `AiChatGuardTest` |

Run the AI slice with Maven, for example:

```bash
mvn -Dtest='com.developer.copilot.ai.**' test
```

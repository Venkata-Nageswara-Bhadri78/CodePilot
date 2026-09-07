# Testing

Tests live under `src/test/java/com/developer/copilot/chatassistant/`. They are unit / slice tests (Mockito, standalone MockMvc, `@WebMvcTest`). There is no dedicated Testcontainers suite in this package.

This document does **not** claim a coverage percentage.

## Layout

| Class | Kind | What it protects |
| --- | --- | --- |
| `controller/ChatAssistantControllerTest` | Standalone MockMvc + `ChatAssistantExceptionHandler` + `GlobalExceptionHandler` | HTTP status mapping, validation, paging, empty history/list, AI/resume/conflict errors |
| `controller/ChatAssistantSecurityTest` | `@WebMvcTest` + real `SecurityConfig` | Unauthenticated and garbage JWT → 401; service never called |
| `service/impl/ChatAssistantServiceImplTest` | Mockito unit | Send/history/list/delete behavior, titles, locks, sanitization, empty-session discard |
| `mapper/ChatAssistantMapperTest` | Pure unit | Null session, null job, paging fields, `createdAt` copy |
| `util/ChatAssistantHtmlSanitizerTest` | Pure unit | Script strip, null → `""`, markdown left intact |
| `ratelimit/filter/ChatAssistantRateLimitFilterTest` | Filter + in-memory limiter | IP limit, user limit across IPs, GET/DELETE not limited, path bucket |
| `ratelimit/service/impl/ChatAssistantRateLimitServiceImplTest` | Mockito Redis | Memory block, `consumeOrThrow`, Redis deny + TTL, Redis exception → memory fallback |

## Controller tests

`ChatAssistantControllerTest` mocks `ChatAssistantService` and asserts JSON `success` / `data` / status:

- Send **201** with `latestTurn`; unknown JSON fields ignored; prompt length 8000 vs 8001
- Blank and whitespace prompts **400**
- Malformed JSON **400** `"Request body is missing or malformed JSON."`
- `JobNotFoundException` **404**
- `AiServiceException` **502**, `AiUnavailableException` **503**
- `AiResumePendingException` **409**, `ResumeParsingException` **422**, `ChatConflictException` **409**
- Escaping `DataIntegrityViolationException` **409** with the global conflict message
- AI `IllegalArgumentException` prior-turns message **400**
- History: page/size forwarded, sort `turnNumber ASC`, illegal page/size **400**, non-numeric job id **400** `"Invalid job id."`
- No chat started → **200** with empty `chatSessionId` and `messages`
- List empty and non-empty **200**; huge `size` **400**
- Delete **200**; job not found **404**

These tests **do not** run the JWT filter (standalone setup). Use `ChatAssistantSecurityTest` for auth.

## Security tests

Without `Authorization`, all four routes return **401**. A `Bearer not-a-jwt` send also returns **401** and never calls `sendMessage`.

## Service tests

`ChatAssistantServiceImplTest` stubs `ChatAssistantTransactionRunner` to run the callback inline. Coverage includes:

- First send creates title `"Amazon - SDE 1"`, turn 1, empty prior turns, null resume fields on `JobChatAiRequest`
- Second send reuses the session, turn 2, prior turn forwarded, `updatedAt` set
- History for the model uses `findRecentByChatSessionId`, not full `findAll...OrderByTurnNumberAsc`
- Last 16 turns only, chronological after reverse
- Concurrent second send while AI is in flight → `ChatConflictException`; one model call
- Null/blank AI content: no `save` on messages; `AiServiceException` with empty-response text
- `<script>` stripped from stored reply
- Title fallbacks (company-only, title-only, blank company, clamp 255, both blank without NPE)
- Unowned job: `JobNotFoundException`, no AI call
- Unauthenticated `CurrentUserService` → `InvalidCredentialsException` on all four use cases
- AI failure on existing session: message not saved
- AI failure on **new** session: `deleteByChatSessionId` + session `delete`
- GET history empty vs populated; list empty vs summaries; delete existing vs idempotent no-op; delete unowned job does not query sessions

## Rate-limit tests

Filter tests use `messagesPerMinute = 2` to keep assertions short. They confirm GET/DELETE/list and `POST /api/v1/ai/chat` are not in the `messages` bucket.

Service tests confirm Redis namespace `rl-messages-user` and fallback when `increment` throws.

## Mapper and sanitizer

Mapper: null session produces null ids and empty messages while keeping `jobId` and page metadata; null job on a summary does not NPE; `createdAt` is copied from the entity.

Sanitizer: `"<script>alert(1)</script>You match this role."` → `"You match this role."`

## Where to add tests

| Change | Prefer |
| --- | --- |
| New HTTP status or validation message | `ChatAssistantControllerTest` |
| New permitAll / auth rule | `ChatAssistantSecurityTest` |
| Session/turn/lock/title behavior | `ChatAssistantServiceImplTest` |
| New Redis key or fallback | `ChatAssistantRateLimitServiceImplTest` |
| Newly limited HTTP method | `ChatAssistantRateLimitFilterTest` |
| Response JSON field mapping | `ChatAssistantMapperTest` |

There are no repository slice tests (`@DataJpaTest`) in this package; unique constraints and `FOR UPDATE` are exercised only via mocked repositories. Add persistence tests if you change uniqueness or lock queries.

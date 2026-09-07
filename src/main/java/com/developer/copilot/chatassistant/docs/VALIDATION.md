# Validation

Validation in chat assistant falls into four buckets: **HTTP input**, **paging**, **security/ownership**, and **business rules** around send/persist. There is no custom `ConstraintValidator` class in this package.

## 1. Input validation (HTTP / Bean Validation)

`SendChatMessageRequest.prompt`:

| Rule | Annotation / behavior | Failure |
| --- | --- | --- |
| Required, not empty, not whitespace | `@NotBlank` | 400 `"prompt: Prompt cannot be blank."` |
| Max length 8000 | `@Size(max = 8000)` | 400 `"prompt: Prompt cannot exceed 8000 characters."` |
| 8000 characters accepted | Covered by controller tests | 201 |
| Body missing / broken JSON | `HttpMessageNotReadableException` | 400 `"Request body is missing or malformed JSON."` |
| Extra JSON properties | Ignored (Jackson default) | Request still succeeds if `prompt` is valid |

`jobId` path variable must bind to `Long`. `"abc"` → **400** `"Invalid job id."` (`ChatAssistantExceptionHandler`).

The controller is `@Validated`, but paging is **not** done with `@Min`/`@Max` on parameters. It uses an explicit check (next section). `@Valid` on the send body is what triggers `MethodArgumentNotValidException`.

## 2. Paging validation

`requireValidPage` in the controller:

- `page >= 0`
- `size` between **1** and `ChatAssistantLimits.MAX_PAGE_SIZE` (**50**)

Otherwise `IllegalArgumentException` with  
`"page must be >= 0 and size must be between 1 and 50."` → **400**.

Defaults: `page=0`, `size=50`. History additionally forces `Sort.by("turnNumber").ascending()`; list uses repository `OrderByUpdatedAtDesc` and does not accept a client sort.

## 3. Security validation

| Check | Implementation | Failure |
| --- | --- | --- |
| Authenticated user | JWT filter; `CurrentUserService.getCurrentUser()` | 401 |
| Job ownership | `jobRepository.findByIdAndUserId` | 404 `"Job not found."` (same text if the job does not exist) |
| JWT user usable | enabled + email verified at filter | 401, never reaches the controller |

List-chats does not take a job id; it filters by `currentUser.id` in the query.

Rate-limit identity (IP / user id) is not a validator; it is a filter. Over-limit is **429**, not 400. See [RATE-LIMITING.md](./CHATASSISTANT-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

## 4. Business validation

### Session and turns

| Rule | Where | Failure / behavior |
| --- | --- | --- |
| One chat per job | Unique `job_id` | Race: re-read session or 409 |
| One row per turn number | Unique `(chat_session_id, turn_number)` | Retry once under `FOR UPDATE`, then 409 |
| Only one in-flight send per job **per JVM** | `ReentrantLock.tryLock()` | 409 `"This chat was updated at the same time. Please retry."` |
| Do not persist blank AI text | After sanitization, `StringUtils.hasText` | `AiServiceException` → 502; no message row |
| Do not keep an empty new session | `discardEmptySessionIfCreated` | Session deleted if this request created it and max turn is 0 |
| Title length | Clamp to 255 with `"..."` | Insert still succeeds |
| Prior turns to the model | Last **16** complete turns only | Incomplete (blank prompt or reply) turns filtered out |

Chat assistant does **not** cap how many turns a session may store. Only the **model window** is 16; GET history pages the rest.

### AI-side checks (invoked on send)

These run inside `AiService.continueJobChat`, not in chat-assistant validators:

| Rule | Effect on this API |
| --- | --- |
| Prior turns list size > 40 | 400 `"Prior turns cannot exceed 40 entries."` (chat assistant never sends more than 16) |
| A prior turn missing prompt or reply | 400 from AI; chat assistant already filters blanks |
| New prompt blank / > 8000 | Constrained on `JobChatAiRequest`; HTTP already validated the same limits |
| Resume still parsing | 409 `AiResumePendingException` |
| Resume parse failed / empty | 422 `ResumeParsingException` |
| Job not found by email lookup | 404 `"Job not found."` |
| Missing default resume / profile | **Not** failed: empty resume context |

### Output sanitization (not schema validation)

`ChatAssistantHtmlSanitizer.stripScripts` removes `<script>...</script>` blocks (dotall, case-insensitive) before persist. Null model content becomes `""`, which then fails the blank check. Markdown such as `**bold**` is unchanged.

## Limits (constants)

`ChatAssistantLimits`:

| Constant | Value | Used for |
| --- | --- | --- |
| `MAX_PAGE_SIZE` | 50 | GET page size |
| `MAX_PRIOR_TURNS` | 16 | Rows loaded for the model (`findRecentByChatSessionId`) |
| `MAX_CHAT_TITLE_LENGTH` | 255 | Title clamp |
| `JOB_NOT_FOUND` | `"Job not found."` | Ownership miss |

16 matches the AI property `app.ai.max-prior-turns-sent` default, and stays under the AI inbound cap of 40.

## What is not validated

- Chat title uniqueness (collisions are allowed)
- Prompt language, toxicity, or injection (the AI system prompt treats resume/job/turns as untrusted data; that is in the AI module)
- Session id on input (clients never send it)
- `createdAt` / `updatedAt` from the client

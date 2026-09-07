# Validation

Validation in the `ai` service falls into four groups: Bean Validation on DTOs, business rules in the service, security/ownership checks, and provider-side option constraints.

## Input validation (Bean Validation)

HTTP chat and stream use `@Valid @RequestBody AiChatRequest`. Internal DTOs are validated when a caller runs a `Validator`; `continueJobChat` also repeats some rules in code.

### `AiChatRequest`

| Field | Rules |
|---|---|
| `prompt` | `@NotBlank`, `@Size(max = 8000)` |
| `jobDescription` | `@Size(max = 16000)` if present |
| `customResumeText` | `@Size(max = 16000)` if present |
| `temperature` | `@DecimalMin(0.0)`, `@DecimalMax(2.0)` if present |
| `mode` | Must deserialize to `AiMode`; unknown → malformed JSON / `400` |
| `resumeId`, `jobId` | No Bean Validation; optional Longs |

Blank prompt (`"   "`) fails `@NotBlank`. Max length 8000 is valid; 8001 is not.

### `JobExtractionAiRequest` (in-process)

| Field | Rules |
|---|---|
| `jobUrl` | `@NotBlank`, max 2048 |
| `rawJobText` | `@NotBlank`, max 100000 |

### `JobChatAiRequest` / `ChatTurnDto` (in-process)

| Field | Rules |
|---|---|
| `jobId` | `@NotNull` |
| `newPrompt` | `@NotBlank`, max 8000 |
| `customResumeText` | max 16000 |
| `priorTurns` | `@Valid` list, `@Size(max = 40)` |
| `ChatTurnDto.userPrompt` | `@NotBlank`, max 8000 |
| `ChatTurnDto.aiResponse` | `@NotBlank`, max 16000 |

## Business validation (`AiServiceImpl`)

These run even if Bean Validation was skipped (in-process job chat):

| Rule | Failure |
|---|---|
| `priorTurns.size() > 40` | `IllegalArgumentException`: `Prior turns cannot exceed 40 entries.` |
| Any turn null or blank `userPrompt` / `aiResponse` | `IllegalArgumentException`: `Each prior turn must include non-blank userPrompt and aiResponse.` |
| More than `maxPriorTurnsSent` (default 16) valid turns | Not an error: **oldest turns are dropped** before the provider call |
| Custom resume/job strings | Trimmed; blank custom resume does not count as “present” (`isBlank()` → fall through to stored resume) |
| Job text | `description` if non-blank, else `originalDescription`, else `""` |
| Extraction result `null` | `AiServiceException` (unparsable job information) |

`GET /resume-context` has no request body. Usable context requires status `COMPLETED` and non-blank `contextText`.

## Security and ownership validation

| Check | Where | Outcome |
|---|---|---|
| JWT, enabled, email verified | `JwtAuthenticationFilter` | No principal → `401` |
| Resume belongs to current user and is active | `ResumeParsingService` | `ResumeNotFoundException` |
| High-priority resume exists | `getParsedResume(null)` | `ResumeNotFoundException` if none |
| Job belongs to user id | `findByIdAndUserId` | `JobNotFoundException` |
| Job chat user exists | `findByEmail` | `JobNotFoundException` (`Job not found.`) |
| Parse still running | resume context adapter | `AiResumePendingException` |
| Parse failed or empty | resume context adapter | `ResumeParsingException` |

Chat **without** `resumeId`: `ResumeNotFoundException` and `UserProfileNotFoundException` are converted to empty resume context. Pending and failed parses still fail the request.

Chat **with** `resumeId`: missing resume/profile is `404`.

## Provider / option validation

- `temperature` is applied only when non-null.
- Extraction always sets temperature `0.0`.
- `maxTokens` is `maxTokensFor(mode)` for chat/stream/job-chat (`COVER_LETTER` uses the higher cap). Extraction uses `maxCompletionTokens`.
- Model id is always `app.ai.default-model` on the request options.

There is no separate custom `ConstraintValidator` class in the AI package.

## Categories summary

| Category | Examples |
|---|---|
| Input | Prompt length, temperature range, JSON shape, enum `mode` |
| Business | Prior-turn cap, trim-to-16, empty extraction entity, parse status |
| Security | JWT, ownership of resume/job, email lookup without leaking identity |
| Infrastructure | Circuit/bulkhead (`503`), rate limit (`429`) — not Bean Validation |

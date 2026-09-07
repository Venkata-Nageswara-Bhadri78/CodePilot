# Flows

This document covers the workflows the `ai` service actually implements. Every diagram is taken from the current code and tests.

## 1. Authenticated request lifecycle (HTTP)

All `/api/v1/ai/**` routes go through the shared JWT filter, then the AI rate-limit filter, then the controller.

```mermaid
sequenceDiagram
  participant Client
  participant JWT as JwtAuthenticationFilter
  participant RL as AiRateLimitFilter
  participant Ctrl as AiController
  participant CUS as CurrentUserService
  participant Svc as AiServiceImpl
  participant GEH as GlobalExceptionHandler

  Client->>JWT: Authorization Bearer
  alt missing/invalid JWT, disabled, or unverified
    JWT->>Client: 401 Unauthorized.
  else principal set
    JWT->>RL: filter chain
    alt chat or resume-context over limit
      RL->>Client: 429 plus Retry-After
    else allowed or unmetered path
      RL->>Ctrl: controller
      Ctrl->>CUS: getCurrentUser (chat, stream, resume-context)
      Ctrl->>Svc: service method
      alt domain or provider exception
        Svc->>GEH: exception
        GEH->>Client: ApiResponse success false
      else success
        Svc->>Ctrl: result
        Ctrl->>Client: ApiResponse or SSE
      end
    end
  end
```

`GET /health` and `GET /config` skip rate limiting and do not call `CurrentUserService`, but they still require JWT authentication.

## 2. Synchronous chat

`POST /api/v1/ai/chat` returns one complete answer.

```mermaid
flowchart TD
  A[Valid AiChatRequest] --> B{customResumeText present?}
  B -->|yes| C[Trim custom text]
  B -->|no| D[ResumeContextService.getResumeContext]
  D --> E{resumeId set?}
  E -->|yes and missing| F[404 Resume not found]
  E -->|no and missing profile/resume| G[Empty resume context]
  D --> H{pending parse?}
  H -->|yes| I[409]
  D --> J{failed or empty?}
  J -->|yes| K[422]
  C --> L{jobDescription present?}
  G --> L
  L -->|yes| M[Trim JD]
  L -->|no jobId| N[Empty JD]
  L -->|jobId| O[JobRepository.findByIdAndUserId]
  O -->|missing| P[404 Job not found]
  O -->|found| Q[description else originalDescription]
  M --> R[Clear persistence context]
  N --> R
  Q --> R
  R --> S[Build system and user prompts]
  S --> T[AiChatGuard.call]
  T -->|circuit open or bulkhead full| U[503]
  T --> V[ChatClient.call with timeout]
  V -->|provider error| W[AiServiceException 502]
  V -->|ok| X[AiChatResponse 200]
```

Context precedence is fixed: inline `customResumeText` wins over `resumeId`; inline `jobDescription` wins over `jobId`. Ids must belong to the authenticated user. Chat without a stored resume continues with `[No resume context provided]` in the prompt.

After a successful call, `AiMetrics.recordChatSuccess` logs latency and token count (tokens may be null).

## 3. Streaming chat (SSE)

`POST /api/v1/ai/chat/stream` uses the same grounding rules as chat. The difference is after the provider stream starts.

```mermaid
sequenceDiagram
  participant Client
  participant Ctrl as AiController
  participant Svc as AiServiceImpl
  participant Guard as AiChatGuard
  participant LLM as ChatClient.stream

  Client->>Ctrl: POST /chat/stream
  Ctrl->>Svc: streamChat
  Note over Svc: Resolve resume and job. Fail here as JSON 404/409/422.
  Svc->>Guard: guardStream
  Guard->>LLM: content flux
  loop non-empty tokens
    LLM-->>Ctrl: AiStreamChunk isCompleted false
    Ctrl-->>Client: event message
  end
  alt stream completes
    Svc-->>Ctrl: completion chunk finishReason STOP
    Ctrl-->>Client: event done
  else timeout or provider error after open
    Svc-->>Ctrl: error chunk finishReason ERROR
    Ctrl-->>Client: event error
    Note over Client: HTTP status may still be 200
  end
```

Empty tokens are dropped. The controller maps chunks to SSE event names: incomplete → `message`; completed with `finishReason=ERROR` → `error`; otherwise `done`. Clients must treat terminal `error` as failure even when the HTTP status is `200`.

## 4. Resume context read

`GET /api/v1/ai/resume-context` always requests the high-priority resume (`getResumeContext(null)`). Unlike chat, a missing resume is **not** turned into empty text — it is `404`.

```mermaid
flowchart TD
  A[GET /resume-context] --> B[ResumeParsingService.getParsedResume null]
  B -->|no profile or no high-priority resume| C[404]
  B -->|in progress or PENDING| D[409 AiResumePendingException]
  B -->|FAILED or blank contextText| E[422 ResumeParsingException]
  B -->|COMPLETED with text| F[200 data is plain text PII]
```

Parse-in-progress is detected both from status `PENDING` (or any non-`COMPLETED` / non-`FAILED` status) and from a `ResumeParsingException` whose message contains “still in progress”, “still being processed”, or “retry shortly”.

## 5. Job extraction (in-process)

HTTP for this feature is `POST /api/v1/job-extraction/parse`. The AI service only implements the model call.

```mermaid
sequenceDiagram
  participant JX as JobExtractionServiceImpl
  participant Guard as JobExtractionAiGuard
  participant Svc as AiServiceImpl
  participant P as PromptTemplateService
  participant LLM as ChatClient.entity

  JX->>Guard: call
  Guard->>Svc: extractJobInfo
  Svc->>P: extraction system and user prompts
  Svc->>LLM: temperature 0.0 entity JobExtractionAiResponse
  alt null entity or provider failure
    Svc-->>JX: AiServiceException
  else parsed
    Svc-->>JX: JobExtractionAiResponse
  end
```

`extractJobInfo` is **not** wrapped by `AiChatGuard`. Temperature is forced to `0.0`. `sourceUrl` and `originalDescription` are not model fields; the caller already knows them.

## 6. Job-scoped multi-turn chat (in-process)

HTTP for this feature is owned by chat-assistant. The AI service receives `JobChatAiRequest` plus the user’s email.

```mermaid
flowchart TD
  A[continueJobChat] --> B{priorTurns size greater than 40?}
  B -->|yes| C[IllegalArgumentException 400]
  B -->|blank turn fields| C
  B -->|ok| D[Keep last maxPriorTurnsSent default 16]
  D --> E[Resolve resume same precedence as chat]
  E --> F[UserRepository.findByEmail]
  F -->|blank or unknown email| G[404 Job not found]
  F --> H[JobRepository.findByIdAndUserId]
  H -->|foreign job| G
  H --> I[System prompt with resume and JD once]
  I --> J[Messages: user/assistant pairs plus new prompt]
  J --> K[AiChatGuard plus ChatClient.call]
  K --> L[AiChatResponse mode GENERAL_CHAT]
```

Resume and job text are **not** repeated on every user turn. Unknown email is mapped to `Job not found.` so the client never sees whether the email existed.

## 7. Rate-limit decision

```mermaid
flowchart TD
  A[Request] --> B{POST /chat or /chat/stream?}
  B -->|yes| C[bucket chat]
  B -->|GET /resume-context| D[bucket resume-context]
  B -->|health config other| E[Pass through]
  C --> F[Consume IP counter]
  D --> F
  F -->|denied| G[429 Retry-After]
  F -->|ok and authenticated user id| H[Consume user counter]
  H -->|denied| G
  H -->|ok| I[Controller]
```

Redis is preferred when enabled and reachable; otherwise an in-memory sliding window is used. See [RATE-LIMITING.md](./AI-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

## 8. Provider resilience

Chat, stream, and job chat acquire the bulkhead and check the circuit **before** calling the provider. Three consecutive `AiServiceException`s (or stream chunks with `finishReason=ERROR`) open the circuit for 30 seconds. A success resets the failure count. Extraction uses job-extraction’s guard instead.

Timeouts use `app.ai.timeout-seconds` (default 60; `streaming-timeout-seconds` is a deprecated alias). Stream timeouts become an SSE `error` chunk; sync timeouts become `502` with a timeout-friendly message.

Details: [PROVIDER-RESILIENCE.md](./AI-SERVICE-SPECIFIC-DOCS/PROVIDER-RESILIENCE.md).

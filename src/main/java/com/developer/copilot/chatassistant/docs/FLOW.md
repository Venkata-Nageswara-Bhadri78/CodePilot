# Flows

This document covers the workflows the chat assistant actually implements. Diagrams follow the code in `ChatAssistantController`, `ChatAssistantServiceImpl`, `ChatAssistantRateLimitFilter`, and the shared security filter.

## 1. Request lifecycle

Every call hits Spring Security first, then the chat-assistant rate-limit filter, then the controller.

```mermaid
sequenceDiagram
    participant C as Client
    participant JWT as JwtAuthenticationFilter
    participant EP as JsonAuthenticationEntryPoint
    participant RL as ChatAssistantRateLimitFilter
    participant Ctrl as ChatAssistantController
    participant Svc as ChatAssistantServiceImpl

    C->>JWT: Authorization Bearer JWT
    alt missing / invalid / unverified / disabled user
        JWT->>EP: unauthenticated
        EP-->>C: 401 Unauthorized.
    else authenticated
        JWT->>RL: principal set
        alt POST .../jobs/{id}/messages over limit
            RL-->>C: 429 + Retry-After
        else GET, DELETE, or send under limit
            RL->>Ctrl: continue
            Ctrl->>Svc: use case
            Svc-->>C: ApiResponse
        end
    end
```

GET history, GET list, and DELETE skip rate-limit counting (`limitFor` returns 0). Only send is a paid door.

## 2. Send a message

This is the only path that calls the model. The AI provider wait runs **between** two short transactions.

```mermaid
flowchart TD
    A[POST /jobs/jobId/messages] --> B[Validate prompt]
    B -->|blank or over 8000| B400[400]
    B --> C[Current user]
    C --> D{tryLock jobId}
    D -->|busy| D409[409 concurrent send]
    D -->|acquired| E[TX: prepareSend]
    E --> F{Job owned?}
    F -->|no| F404[404 Job not found.]
    F -->|yes| G{Session exists?}
    G -->|no| H[Insert chat_sessions]
    H -->|unique collision| I[Re-read session]
    G -->|yes| J[Load last 16 turns]
    H --> J
    I --> J
    J --> K[AiService.continueJobChat]
    K -->|runtime failure| L[Discard empty new session]
    L --> M[Rethrow: 409/422/502/503]
    K -->|ok| N{Sanitized content blank?}
    N -->|yes| L
    N -->|no| O[TX: persistTurn]
    O --> P[Lock session, next turnNumber, INSERT message]
    P --> Q[Bump session.updatedAt]
    Q --> R[201 latestTurn only]
```

Behavior that is easy to miss:

- HTTP **201** means a **turn** was created, including turn 20 — not only the first message
- The response is **not** the full thread; clients poll GET history
- `resumeId` and `customResumeText` are left null; the AI service uses the user’s high-priority resume or empty context
- Prior turns with blank prompt or reply are dropped before the model call
- If this send **created** the session and the AI call then fails (or returns blank), the empty session is deleted so GET history still returns the unused-chat shape

See [SEND-AND-CONCURRENCY.md](./CHATASSISTANT-SERVICE-SPECIFIC-DOCS/SEND-AND-CONCURRENCY.md) and [AI-INTEGRATION.md](./CHATASSISTANT-SERVICE-SPECIFIC-DOCS/AI-INTEGRATION.md).

## 3. Concurrent send on the same job

```mermaid
sequenceDiagram
    participant A as Request A
    participant B as Request B
    participant Lock as ReentrantLock jobId
    participant AI as AiService

    A->>Lock: tryLock success
    A->>AI: continueJobChat
    B->>Lock: tryLock fail
    B-->>B: ChatConflictException 409
    AI-->>A: content
    A->>Lock: unlock
```

The in-memory lock is **per JVM**. Two instances can both pass `tryLock`. Persist then uses `findByIdForUpdate` and a unique `(session, turn_number)` constraint; a remaining collision becomes 409 after one retry (`"This chat was updated at the same time. Please retry."`).

## 4. Get chat history

Does **not** call the AI service.

```mermaid
flowchart TD
    A[GET /jobs/jobId] --> B[Validate page/size]
    B -->|illegal| B400[400]
    B --> C{Job owned?}
    C -->|no| C404[404 Job not found.]
    C -->|yes| D{Session for user+job?}
    D -->|no| E[200: chatSessionId null, messages]
    D -->|yes| F[Page messages turnNumber ASC]
    F --> G[200: session + page]
```

404 always means **job** missing or not yours. A missing chat is success with empty data. That lets a UI open a fresh composer without special-casing 404.

## 5. List my chats

```mermaid
flowchart TD
    A[GET /api/v1/chat-assistant] --> B[Validate page/size]
    B --> C[findAllByUserIdOrderByUpdatedAtDesc]
    C --> D[200: chats page, may be empty]
```

No job-id parameter. Rows are already scoped by `user_id`. Job title and company are loaded with an `@EntityGraph` on `job` to avoid N+1. `updatedAt` is the session row, bumped when a turn is saved.

## 6. Delete a job’s chat

Does **not** delete the job and does **not** call the AI service.

```mermaid
flowchart TD
    A[DELETE /jobs/jobId] --> B{Job owned?}
    B -->|no| B404[404]
    B -->|yes| C{Session exists?}
    C -->|no| D[200 no-op]
    C -->|yes| E[DELETE messages by session id]
    E --> F[DELETE session]
    F --> G[200]
```

After delete, the next send creates a new session and a new title snapshot. Message ids and session id will be new.

## 7. Rate-limit on send

```mermaid
flowchart TD
    A[POST .../messages] --> B[bucket = messages]
    B --> C[consume messages-ip]
    C -->|denied| D[429 Retry-After]
    C -->|allowed| E{Authenticated user id?}
    E -->|yes| F[consume messages-user]
    F -->|denied| D
    F -->|allowed| G[Controller]
    E -->|no| G
```

Window is 60 seconds. Default limit is 8 (`app.chatassistant.messages-per-minute`). Redis uses a **fixed** counter + TTL; memory uses a **sliding** window of timestamps. Redis failures fall back to memory. See [RATE-LIMITING.md](./CHATASSISTANT-SERVICE-SPECIFIC-DOCS/RATE-LIMITING.md).

## 8. AI grounding inside continueJobChat

Once the chat assistant has prepared `JobChatAiRequest`, the AI service:

1. Validates prior turns (max 40 inbound; chat assistant only sends 16)
2. Resolves resume text (default high-priority resume, or `""` if none)
3. Resolves job description by user email + job id (second ownership check)
4. Builds a job-chat system prompt (resume + job embedded once)
5. Sends prior turns as user/assistant messages, then the new prompt
6. Runs the call through `AiChatGuard` (circuit + bulkhead) and a timeout

Failures map to 409 (resume still parsing), 422 (parse failed/empty), 502 (provider/blank after mapping), or 503 (circuit open / bulkhead full). See [AI-INTEGRATION.md](./CHATASSISTANT-SERVICE-SPECIFIC-DOCS/AI-INTEGRATION.md).

## 9. Security path for a stolen or garbage token

`JwtAuthenticationFilter` only sets the security context when:

- `Authorization: Bearer ...` is present
- the JWT parses to a user id
- that user exists, is enabled, has verified email, and the token is valid for that user

Otherwise the request continues unauthenticated and `SecurityFilterChain` (`anyRequest().authenticated()`) returns **401** with `"Unauthorized."` via `JsonAuthenticationEntryPoint`. The controller is never reached. Garbage tokens are tested in `ChatAssistantSecurityTest`.

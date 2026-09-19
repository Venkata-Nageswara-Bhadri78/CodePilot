# Copilot backend — system documentation

This is the **global** documentation for the Copilot Spring Boot backend: one JVM process that helps a candidate save job postings, keep a career profile and resumes, and talk to an LLM about those jobs.

It is **not** a catalog of every class. Module-level contracts live next to the code under `src/main/java/com/developer/copilot/*/docs/`. This set explains how those modules form one system.

The implementation is the source of truth. These files describe behavior that exists in `src/main/` and `src/test/` today.

---

## What this backend does

Copilot is a **personal career copilot API**, not a public job board.

A user:

1. Registers, verifies email via OTP, and logs in.
2. Builds a profile and uploads PDF resumes (parsed in the background).
3. Extracts structured job fields from pasted text or from a fetched URL, then **saves** the posting into a private notebook.
4. Chats with an LLM about a saved job, or uses the general AI copilot (optionally grounded in resume + job text).

Identity is JWT-based and stateless. Persistence is MySQL. Object storage is MinIO (or S3-compatible). Redis is optional and used for distributed rate-limit counters and short extraction caches, not as a session store.

---

## Primary responsibilities

| Area | What the system owns |
| --- | --- |
| Identity | Accounts, OTP email, password reset, access JWT + opaque refresh UUID, browser-extension JWT |
| Career data | One profile per user, child collections, resume files + parsed text |
| Job notebook | Per-user saved postings, URL canonicalization, duplicate detection |
| Extraction | Preview-only parse (paste or fetch). Persistence is always `POST /api/v1/jobs` |
| AI | Career chat (sync + SSE), structured job extraction used by extraction modules, job-scoped chat used by chat assistant |
| Shared kernel | `ApiResponse` envelope, global errors, internal API key hallway, MinIO, URL hashing |

---

## Major modules

All packages live under `com.developer.copilot` in a **single Spring Boot application**. They are not separately deployed microservices.

| Module | Package | Role |
| --- | --- | --- |
| **auth** | `auth` | Identity, JWT filter chain, CORS, public auth APIs, scheduled token cleanup |
| **user** | `user` | Profile, resumes, background PDF parse, internal parsed-resume HTTP |
| **jobs** | `jobs` | Saved job CRUD for the authenticated user |
| **jobextraction (manual)** | `jobextraction.manualextraction` | Preview from pasted URL + page text |
| **automated job extraction** | `jobextraction.automatedjobextraction` | Fetch URL (SSRF-guarded), extract page text, reuse manual parse |
| **ai** | `ai` | LLM client, prompts, resume/job grounding, in-process chat circuit/bulkhead |
| **chatassistant** | `chatassistant` | Persisted, job-keyed multi-turn conversations |
| **common** | `common` | Shared HTTP contract, storage, internal key, URL util, global exceptions |

---

## Shared components and infrastructure

- **HTTP contract:** `ApiResponse<T>` (`success`, `message`, `data`, `timestamp`) on JSON endpoints. Resume download returns PDF bytes, not the envelope. AI stream returns SSE.
- **Security:** `SecurityConfig` + `JwtAuthenticationFilter` + `AuthRateLimitFilter`. Internal paths add `InternalApiKeyFilter` and `InternalApiRateLimitFilter`.
- **MySQL / JPA:** schema via `spring.jpa.hibernate.ddl-auto` (typically `update`). Auditing on `BaseEntity`.
- **Redis:** Boot Data Redis auto-config is **excluded**. Each module that needs Redis creates its own client when `app.<module>.redis.enabled=true`.
- **MinIO:** resume PDFs.
- **SMTP:** OTP and password-reset mail.
- **OpenAI-compatible LLM:** Spring AI `ChatClient` (example config uses Gemini or another compatible base URL).

---

## High-level security model

- **Public:** selected `POST /api/v1/auth/*` paths and `/error`. Swagger is anonymous only when the active profile is **not** `prod` / `production`.
- **Authenticated (web JWT):** almost everything else. The access JWT must belong to an **enabled, email-verified** user; `tokenVersion` must match.
- **Authenticated (extension JWT):** same identity, plus `cid=browser-extension`. Allowed **only** on `/api/v1/job-extraction/**` and `/api/v1/automated-job-extraction/**`.
- **Internal:** `/api/v1/internal/**` requires a valid **web** JWT **and** `X-Internal-Api-Key`. Ownership is still the JWT user.
- **Not implemented:** HTTP-only refresh cookies, MFA, per-endpoint `ADMIN` vs `USER` checks on business APIs.

Details: [SECURITY.md](SECURITY.md).

---

## Major workflows

1. **Register → OTP → login → Bearer JWT** on subsequent calls.
2. **Refresh rotation** with reuse detection; logout-all / reset-password bump `tokenVersion`.
3. **Profile + resume upload → async parse → (optional) internal GET of parsed PII.**
4. **Extract preview → user edits → save job.** Extraction never writes `jobs` rows.
5. **Job-scoped chat** persists turns; general AI chat does not persist history.

Details: [FLOW.md](FLOW.md).

---

## Data stores

| Store | What lives there |
| --- | --- |
| MySQL | Users, tokens, profile, resumes, parsed resume, jobs, chat sessions/messages |
| MinIO | Resume PDF bytes |
| Redis (optional) | Rate-limit counters; auth mail cooldown / login backoff; extraction preview / fetched-content caches |

---

## How this documentation is organized

| Document | Contents |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, boundaries, dependency direction |
| [FLOW.md](FLOW.md) | Cross-module workflows and diagrams |
| [ENDPOINTS.md](ENDPOINTS.md) | Global REST catalog |
| [SECURITY.md](SECURITY.md) | Authn/z, filters, CORS, abuse controls |
| [DATABASE.md](DATABASE.md) | Domains, entities, relationships |
| [REDIS-INFRASTRUCTURE.md](REDIS-INFRASTRUCTURE.md) | Redis as shared infrastructure |
| [ERROR-HANDLING.md](ERROR-HANDLING.md) | Exceptions → HTTP |
| [VALIDATION.md](VALIDATION.md) | Validation layers |
| [CONFIGURATION.md](CONFIGURATION.md) | Properties and profiles (no secrets) |
| [TESTING.md](TESTING.md) | Test strategy |
| [DEPENDENCIES.md](DEPENDENCIES.md) | Why major libraries exist |

---

## Service-specific documentation

Deeper module docs (do not duplicate them here):

| Module | Start here |
| --- | --- |
| Auth | [`src/main/java/com/developer/copilot/auth/docs/AUTH.md`](../src/main/java/com/developer/copilot/auth/docs/AUTH.md) |
| User | [`src/main/java/com/developer/copilot/user/docs/USER.md`](../src/main/java/com/developer/copilot/user/docs/USER.md) |
| Jobs | [`src/main/java/com/developer/copilot/jobs/docs/JOBS.md`](../src/main/java/com/developer/copilot/jobs/docs/JOBS.md) |
| Manual job extraction | [`src/main/java/com/developer/copilot/jobextraction/manualextraction/docs/JOBEXTRACTION.md`](../src/main/java/com/developer/copilot/jobextraction/manualextraction/docs/JOBEXTRACTION.md) |
| AI | [`src/main/java/com/developer/copilot/ai/docs/AI.md`](../src/main/java/com/developer/copilot/ai/docs/AI.md) |
| Chat assistant | [`src/main/java/com/developer/copilot/chatassistant/docs/CHATASSISTANT.md`](../src/main/java/com/developer/copilot/chatassistant/docs/CHATASSISTANT.md) |
| Common | [`src/main/java/com/developer/copilot/common/docs/COMMON.md`](../src/main/java/com/developer/copilot/common/docs/COMMON.md) |

Automated job extraction does not have a parallel `docs/` tree; behavior is described in this global set and in the `automatedjobextraction` package.

---

## Where to start

1. This README for the map.
2. [ARCHITECTURE.md](ARCHITECTURE.md) then [FLOW.md](FLOW.md).
3. [ENDPOINTS.md](ENDPOINTS.md) and [SECURITY.md](SECURITY.md) before calling the API.
4. Module `docs/` when you change a single package.

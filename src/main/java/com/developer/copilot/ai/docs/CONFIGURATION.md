# Configuration

AI behavior is driven by Spring AI’s OpenAI-compatible properties plus `app.ai.*`. Values below come from `AiProperties`, `AiRateLimitProperties`, `AiRedisProperties`, and `src/main/resources/application.properties.example`. Do not copy real secrets into documentation or source control.

## Provider connection (Spring AI)

These properties configure the `ChatClient` auto-configuration (OpenAI starter, used with Gemini’s OpenAI-compatible endpoint in the example file).

| Property | Example / default in repo | Purpose |
|---|---|---|
| `spring.ai.openai.api-key` | `${GEMINI_API_KEY:...}` | Provider credential. Never expose this. |
| `spring.ai.openai.base-url` | `https://generativelanguage.googleapis.com/v1beta/openai/` | OpenAI-compatible base URL |
| `spring.ai.openai.chat.options.model` | `gemini-flash-latest` | Auto-config default model |
| `spring.ai.openai.chat.options.temperature` | `0.7` | Auto-config default temperature |

**Source of truth for the model name the AI service reports and sends on each call** is `app.ai.default-model`, applied in `AiConfig` and per-call `OpenAiChatOptions`. Keep it aligned with `spring.ai.openai.chat.options.model`.

Chat only sets temperature on the call when the request body includes `temperature`. Extraction always uses `0.0`. Job chat does not set temperature on the per-call options.

## Application AI settings (`app.ai`)

Bound by `AiProperties` and `AiRateLimitProperties` (same prefix).

| Property | Code default | Purpose |
|---|---|---|
| `app.ai.provider` | `gemini` | Label only (health `activeModel` string). Not a switch that changes clients. |
| `app.ai.default-model` | `gemini-flash-latest` | Model id on every `ChatClient` call and in responses |
| `app.ai.timeout-seconds` | `60` | Sync `Mono.timeout` and stream `Flux.timeout` |
| `app.ai.streaming-timeout-seconds` | (alias) | Deprecated setter; writes `timeoutSeconds`. Example file still uses this name. |
| `app.ai.max-completion-tokens` | `2048` | `maxTokens` for all modes except cover letter; also extraction |
| `app.ai.cover-letter-max-completion-tokens` | `4096` | `maxTokens` when `mode=COVER_LETTER` |
| `app.ai.max-prior-turns-sent` | `16` | Newest prior turns sent to the model in job chat (inbound still allows 40) |
| `app.ai.chat-per-minute` | `8` | Rate limit for `POST /chat` and `/chat/stream` |
| `app.ai.resume-context-per-minute` | `20` | Rate limit for `GET /resume-context` |

`maxTokensFor` never returns less than `1`.

### Present in the example file but unused by the AI package

`app.ai.agentic-server-url` appears in `application.properties.example`. No class in `com.developer.copilot.ai` binds or reads it. Do not assume a separate agentic server is wired.

## AI Redis (`app.ai.redis`)

Bound by `AiRedisProperties`. Beans are created only when `enabled=true`.

| Property | Default | Purpose |
|---|---|---|
| `app.ai.redis.enabled` | `false` | Distributed rate-limit counters |
| `app.ai.redis.host` | `localhost` | |
| `app.ai.redis.port` | `6379` | |
| `app.ai.redis.password` | none | Applied only if non-blank |
| `app.ai.redis.database` | `0` | |
| `app.ai.redis.timeout-ms` | `2000` | Lettuce command timeout |
| `app.ai.redis.key-prefix` | `ai` | Redis key prefix |

Not listed in the example properties file; defaults still apply. Independent of `app.auth.redis` and `app.jobextraction.redis`.

## Related application settings (not AI-owned)

These affect AI **grounding** but are owned by other modules:

- JWT: `app.jwt.secret`, `app.jwt.expiration` — required for all AI HTTP routes.
- Resume parsing: `resume.parsing.*` — parse timeout and version affect when resume context is `409` vs usable text.
- MySQL datasource — needed when resolving stored jobs or resumes.

## Profiles

`AiOpenApiConfig` is active unless the profile is `prod` or `production`. That only hides the AI Swagger group; it does not disable the REST API.

## Environment variables commonly used

| Variable | Used for |
|---|---|
| `GEMINI_API_KEY` | Example mapping into `spring.ai.openai.api-key` |
| `APP_JWT_SECRET` | Shared JWT signing (auth). Required in production profiles. |

Use placeholders in committed files. Rotate any key that was ever checked in.

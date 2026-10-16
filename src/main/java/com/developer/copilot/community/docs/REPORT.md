# Community module report

Verified with `./mvnw clean package` on 16 October 2026. Result: **BUILD SUCCESS**. Tests: **1311 run, 0 failures, 0 errors, 0 skipped**. The jar is `target/copilot-0.0.1-SNAPSHOT.jar`.

## 1. Implemented functionality

A signed-in user can accept community terms, start from a saved job they own, and either create the group for that posting or explicitly join the one that already exists. They can belong to many communities, leave, and rejoin the same membership. Active members can read a shared header, the member list, and text history one page at a time. Live text is sent over STOMP, stored first, then delivered to current members. Presence tracks sockets on this process, including more than one tab.

Not included: media, reactions, replies, edits, deletes, search, unread counts, direct messages, community expiry, or a Redis chat bus.

## 2. Architecture and data model

Package `com.developer.copilot.community`. Schema is Hibernate `ddl-auto`. Tables:

| Table | Role |
| --- | --- |
| `community_accounts` | One opt-in per `users.id`. Terms version and accept time only |
| `communities` | Shared title, company, location, description, immutable generated name, immutable canonical posting key, immutable creator |
| `community_url_aliases` | Normalized URL and its hash. One hash belongs to one community |
| `community_memberships` | One row per user per community. `ACTIVE` or `LEFT`. Rejoin updates the same row |
| `community_messages` | Text, sender `users.id`, `created_at`. History index `(community_id, created_at, id)` |

`jobs.id` is not stored. The canonical key is the SHA-256 of the primary normalized posting URL. Member count is the number of `ACTIVE` rows. Presence is memory on this JVM.

Account create, community create, and join use their own transactions. A unique-key race returns the winning row.

## 3. API contract

REST base `/api/v1/community`. Web JWT required. Details, examples, and statuses: [ENDPOINTS.md](ENDPOINTS.md).

| Method | Path |
| --- | --- |
| GET, POST | `/account` |
| GET | `/jobs/{jobId}/prefill` |
| POST | `/resolve` |
| POST | `/` |
| POST | `/descriptions/refine` |
| GET | `/memberships` |
| GET | `/{communityId}` |
| GET | `/{communityId}/members` |
| GET | `/{communityId}/membership` |
| POST | `/{communityId}/join` |
| POST | `/{communityId}/leave` |
| GET | `/{communityId}/messages` |

STOMP endpoint `/ws/community`. Send `/app/communities/{communityId}/messages`. Subscribe `/topic/communities/{communityId}/messages`, `/topic/communities/{communityId}/presence`, `/user/queue/errors`, `/user/queue/presence`, `/user/queue/membership`. Full contract: [WEBSOCKET.md](WEBSOCKET.md). Frontend steps: [FRONTEND.md](FRONTEND.md).

OpenAPI group `community` in the existing springdoc setup. It is off for `prod` and `production`. STOMP is not in that JSON.

## 4. Security

The existing JWT filter and `webFrontendComplete()` protect REST. Extension tokens and missing usernames are rejected. Community routes other than reading consent require a current community account. Prefill and create load the job with `findByIdAndUserId`. History, members, the community header, subscribe, send, and live delivery require an active membership. Leave removes that community from an open socket. The client cannot set the sender. Email is not returned. The socket token is a CONNECT header, not a query parameter, and it is not logged. Origins come from `cors.allowed-origins` with `*` removed. Clients cannot publish to `/topic`.

## 5. URL matching

Normalization strips tracking parameters, keeps a job id in the query, and folds host, `www`, and a trailing slash. The server does not fetch the URL. A homepage or a single generic path such as `/jobs` is uncertain and does not create a community. URLs that already belong to two communities are a conflict. Title and company never decide identity. Concurrent creates of one posting produce one community. The loser is not joined.

## 6. AI integration

`POST /api/v1/community/descriptions/refine` and create with `refineDescription: true` call `AiService.refineCommunityDescription` with only that text. No resume and no job row are loaded. If the model fails, the submitted description is kept and create still succeeds. Nothing is stored by the refine route alone.

## 7. Files changed for this verification pass

Documentation added under `src/main/java/com/developer/copilot/community/docs/`: `ARCHITECTURE.md`, `ENDPOINTS.md`, `WEBSOCKET.md`, `FRONTEND.md`, `SECURITY.md`, `CONFIGURATION.md`, `ERROR-HANDLING.md`, and this report. `DATABASE.md` and `ACCOUNT.md` now point at the current behavior.

OpenAPI response codes were added on the community, membership, and history controllers. The group description notes that live send is STOMP.

A startup cycle was fixed: presence and broadcast look up the STOMP template when a message is sent, not while the broker is still being created.

The feature itself was built in the earlier tasks. Those files live under `com.developer.copilot.community` plus the WebSocket dependency in `pom.xml`, the `/ws/community` permit in `SecurityConfig`, and the `app.community.*` keys in `application.properties.example`.

## 8. Tests and build

Command: `./mvnw clean package`

Result: BUILD SUCCESS.

Tests run: 1311. Failures: 0. Errors: 0. Skipped: 0.

A browser STOMP client was not driven against a running server. Socket behavior is covered by unit tests for authentication, destinations, membership checks, revoked delivery, broadcast of a stored id, two tabs, and a forgotten session.

## 9. Configuration

Not secrets. Defaults apply if the keys are absent.

- `app.community.terms-version=1`
- `app.community.max-url-aliases=8`
- `app.community.max-message-length=4000`
- `app.community.message-page-size=30`
- `app.community.max-message-page-size=50`
- `app.community.ws.heartbeat-ms=10000`
- `app.community.ws.presence-sweep-ms=15000`

Socket origins follow `cors.allowed-origins`.

## 10. Known limitations

- Presence and live delivery are per process. Two instances do not share sockets. History in MySQL is shared.
- There is no REST send. The composer uses STOMP.
- Salary is not part of the shared header.
- Communities and messages are not deleted.
- A full end-to-end browser socket session was not executed in this verification.

## Completion checklist

The section 26 behaviors above are implemented and covered by the passing suite, with the two limitations called out: multi-instance presence, and no browser-driven socket session. Section 28 exclusions (deletion, media, auto-join, job id as identity, client-supplied sender, email in payloads, uncertain-URL merges) were not added.

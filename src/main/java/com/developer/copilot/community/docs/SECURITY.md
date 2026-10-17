# Community security

Community routes use the existing JWT filter, `CurrentUserService`, and `webFrontendComplete()`. There is no second login system.

## Who can call

| Check | Where |
| --- | --- |
| Web JWT, username present, not an extension token | Every `/api/v1/community/**` route |
| Current community account and current terms | Every community route except `GET /api/v1/community/account` |
| Saved job owned by the caller | Prefill and create. Someone else's job is the same 404 as a missing job |
| Active membership | Community header, member list, history, STOMP subscribe, STOMP send, and live delivery |
| Explicit join | A URL match does not insert a membership |

The caller cannot send `userId`, a sender name, or a message timestamp. Join, leave, history, and STOMP send ignore a client identity and use the token.

## Socket

`/ws/community` is permitted at the HTTP layer so the browser can open the upgrade. `CONNECT` requires `Authorization: Bearer <access token>`. The token is not taken from the query string and is not logged. Origins are the CORS allow-list with `*` removed. Clients cannot publish to `/topic`. A subscription or send is checked against the account and the active membership. After leave, that socket is removed from delivery for that community even if the subscription started earlier.

## Privacy

API and socket payloads may include username, full name, and `users.id` where the client must match a sender to a member. They do not include email, password, phone, resume, private job notes, or AI history. Community rows do not copy a job. Salary is not stored. Logs use user id and community id.

## URL fetch

Matching does not fetch the posting page. There is no server-side request to the job URL, so there is no SSRF path in this module.

## Limits

Description, URL, alias count, message length, and history page size are bounded. A history response never returns every message. STOMP frames are capped at 64 KB.

## Concurrency

Unique keys stop a second account, a second community for one posting, and a second membership row. The losing request reads the winner. Leave and rejoin update the same membership row.

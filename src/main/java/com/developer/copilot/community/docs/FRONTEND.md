# Community frontend guide

Use the existing Bearer access token. Do not send `userId`. Do not treat a URL match as membership. Visual layout is up to the client. The contracts below are the ones the server implements.

Base path: `/api/v1/community`. Envelope: `success`, `message`, `data`, `timestamp`. Error strings are in [ERROR-HANDLING.md](ERROR-HANDLING.md). Field detail is in [ENDPOINTS.md](ENDPOINTS.md). The socket is in [WEBSOCKET.md](WEBSOCKET.md).

## Flow

1. `GET /api/v1/community/account`. If `data.state` is not `ACCOUNT_ACTIVE`, show the terms step and stop.
2. Show the current terms. The version string is `data.currentTermsVersion` (default `1`).
3. `POST /api/v1/community/account` with `{ "accepted": true, "termsVersion": "<current>" }`. `201` creates the account. `200` means it was already current or the same row accepted a new version.
4. The person picks one saved job.
5. `GET /api/v1/community/jobs/{jobId}/prefill`. Read `data.match.outcome`.
6. `EXISTING_COMMUNITY` and `membership` `NOT_A_MEMBER` shows **Join Community**. `MEMBER` shows **Open**. `NO_COMMUNITY` shows the create form. `MATCH_UNCERTAIN` asks for the real posting URL. Do not create.
7. Prefill the form from `title`, `company`, `location`, `description`, and `sourceUrl`. The person cannot type the community name.
8. They may edit those fields and add or remove alternative posting URLs. Before submit, `POST /api/v1/community/resolve` with `{ "urls": [primary, ...alternatives] }`.
9. Create with `POST /api/v1/community`. `201` means they are the first member: open the room. `200` `EXISTING_COMMUNITY` does not join them: show Join unless `membership` is `MEMBER`. Optional: `POST /api/v1/community/descriptions/refine` before create. If `refined` is false, keep their text.
10. Join is `POST /api/v1/community/{communityId}/join` with no body. `JOINED` and `REJOINED` enter the room. `ALREADY_MEMBER` enters without an error.
11. Header: `GET /api/v1/community/{communityId}`. Members: `GET /api/v1/community/{communityId}/members`. Newest messages: `GET /api/v1/community/{communityId}/messages`. Draw that page oldest-first. The last item is the latest.
12. Open `ws(s)://<host>/ws/community` and STOMP `CONNECT` with `Authorization: Bearer <access token>`. Subscribe to `/topic/communities/{communityId}/messages`, `/topic/communities/{communityId}/presence`, `/user/queue/errors`, `/user/queue/presence`, and `/user/queue/membership`.
13. Send `{ "content": "..." }` to `/app/communities/{communityId}/messages`. Append topic events the screen does not already have, matched by `id`. Mark members online from the presence snapshot and later `ONLINE` / `OFFLINE` events.
14. Scroll up with `?cursor={nextCursor}`. Prepend the page. Stop when `hasMore` is false.
15. After a drop, load the newest history page again, connect, and subscribe. Dedupe by `id`.

The community list is `GET /api/v1/community/memberships`. Leave is `POST /api/v1/community/{communityId}/leave` with no body. Then drop that room. `creator: true` does not close the community.

## Authorization the screen will see

| Status | Message | Action |
| --- | --- | --- |
| 401 | `Unauthorized.` | Login |
| 403 | `This client is not authorized to access this resource.` | Use the web session, not the extension |
| 403 | `Complete your username to continue.` | Username step |
| 403 | `A community account is required. Accept the current terms to create one.` | Terms |
| 403 | `The community terms have changed. Accept the current version to continue.` | Accept again |
| 403 | `Join this community to continue.` | Join, or leave the room |
| 404 | `This community is not available.` | Not a community |
| 404 | `Job not found with id: …` | Not their job |
| 409 | Uncertain URL or two communities | Do not create |

Socket error `code` values are `MEMBERSHIP_REQUIRED`, `ACCOUNT_REQUIRED`, `TERMS_REQUIRED`, `COMMUNITY_UNAVAILABLE`, `INVALID_MESSAGE`, `DESTINATION_NOT_ALLOWED`, and `SEND_FAILED`. `SEND_FAILED` means the message was not stored.

## Repeated calls

Join twice is safe. Leave twice is safe. Create twice for one posting returns the existing community and does not join the second caller. Sending the same text twice stores two messages. A second subscribe to the same topic does not double-deliver.

## Not available

No images, reactions, replies, edits, deletes, search, unread counts, or direct messages. No REST URL that sends a chat message.

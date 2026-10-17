# Community endpoints

Base path: `/api/v1/community`

`Authorization: Bearer <access token>`. The token must be a web session with a username. Extension tokens are 403. Bodies are JSON. Success and error use the envelope in [ERROR-HANDLING.md](ERROR-HANDLING.md).

`GET /api/v1/community/account` does not require a community account. Every other route does. A URL match never joins the caller.

Live send is not in this list. It is [WEBSOCKET.md](WEBSOCKET.md).

## `GET /api/v1/community/account`

Reads consent. Does not insert a row.

`data.state`: `TERMS_ACCEPTANCE_REQUIRED`, `TERMS_REACCEPTANCE_REQUIRED`, or `ACCOUNT_ACTIVE`.

`200` always when the token is valid, including when no account exists.

## `POST /api/v1/community/account`

```json
{ "accepted": true, "termsVersion": "1" }
```

`accepted` must be true. `termsVersion` must equal `app.community.terms-version` after trim. `userId` is 400.

| Status | Result |
| --- | --- |
| 201 | First accept. The account row is created |
| 200 | Same version again, or a new configured version written on the same row |
| 400 | Consent missing, version mismatch, or an unknown field |

## `GET /api/v1/community/jobs/{jobId}/prefill`

The job must belong to the caller. Returns title, company, location, description (cleaned, otherwise the original paste), `sourceUrl`, and a match. Does not create or join.

A missing job and someone else's job are both `404` `Job not found with id: {id}`.

## `POST /api/v1/community/resolve`

```json
{ "urls": ["https://jobs.example.com/openings/backend-engineer"] }
```

One to twenty URLs. Tracking parameters are removed. A job id in the query is kept. Host, `www`, and a trailing slash do not make a second posting.

`data.outcome`:

| Outcome | Meaning |
| --- | --- |
| `NO_COMMUNITY` | Create is allowed |
| `EXISTING_COMMUNITY` | `data.community` is the safe summary. `membership` is `MEMBER` or `NOT_A_MEMBER` |
| `MATCH_UNCERTAIN` | Homepage or a generic jobs path. Do not create |
| `ALIAS_CONFLICT` | The URLs already belong to different communities |

All four are HTTP 200. This call does not join.

Summary fields: `id`, `communityName`, `title`, `company`, `location`, `description`, `createdAt`, `membership`, `activeMemberCount`. No email, username, or user id.

## `POST /api/v1/community`

Creates a community, its aliases, and the creator membership, or returns the community that already owns the URL.

| Field | Required | Rules |
| --- | --- | --- |
| `jobId` | yes | Caller's saved job. Not the community id |
| `title`, `company` | yes | Max 255 |
| `location` | no | Max 255 |
| `description` | yes | Max 50000 |
| `sourceUrl` | yes | Absolute http(s), max 2000. Becomes the canonical posting after normalization |
| `alternativeUrls` | no | Same posting. Count is capped |
| `refineDescription` | no | When true, the description is rewritten before insert. A model failure still creates the community with the submitted text |

The server generates `communityName`. The client cannot send one. The saved job is not updated.

| Status | `data.outcome` |
| --- | --- |
| 201 | `CREATED`. The caller is `MEMBER` |
| 200 | `EXISTING_COMMUNITY`. The caller is not joined |
| 409 | Uncertain URL or alias conflict. Nothing is inserted |
| 404 | Job missing or not owned |

Two creates of the same posting cannot insert two communities. The loser gets the existing community and is not joined.

## `POST /api/v1/community/descriptions/refine`

```json
{ "description": "Build APIs" }
```

Only that text is sent to the model. Nothing is stored. `data.refined` false means the original text was returned.

## `GET /api/v1/community/memberships`

Communities where the caller is an active member, most recently joined first. Not a directory. An empty list is 200 with `communities: []`. Each item is the summary above, with `membership` `MEMBER`.

## `GET /api/v1/community/{communityId}`

Shared header for an active member: the same summary. A departed member is 403 `Join this community to continue.` A missing id is 404 `This community is not available.`

The join screen uses the summary from resolve or create, not this route.

## `GET /api/v1/community/{communityId}/members`

Active members only, earliest current join first. Each person is `userId`, `username`, `fullName`, `membershipStatus` `ACTIVE`, and `joinedAt`. No email. `activeMemberCount` equals the list length. Active membership required.

## `GET /api/v1/community/{communityId}/membership`

The caller's own row. Does not require membership and does not return the description or other people.

| `outcome` | `membership` | Meaning |
| --- | --- | --- |
| `MEMBER` | `MEMBER` | Active |
| `ALREADY_LEFT` | `NOT_A_MEMBER` | They left. `leftAt` is set |
| `NOT_A_MEMBER` | `NOT_A_MEMBER` | No row. `membershipStatus` is null |

`creator` stays true after the recorded creator leaves. It does not grant extra actions.

## `POST /api/v1/community/{communityId}/join`

No body. A body field, including `userId`, is 400 `Send no fields. A user id is not accepted.`

| Status | `outcome` |
| --- | --- |
| 201 | `JOINED` |
| 200 | `ALREADY_MEMBER` or `REJOINED` |

Rejoin updates the same row, sets `joinedAt` to now, and clears `leftAt`. A racing second insert returns the existing active membership.

## `POST /api/v1/community/{communityId}/leave`

No body. Same unknown-field rule as join.

| `outcome` | Effect |
| --- | --- |
| `LEFT` | Row becomes `LEFT`. `leftAt` is set. `joinedAt` stays |
| `ALREADY_LEFT` | First `leftAt` is kept |
| `NOT_A_MEMBER` | No row is inserted |

The community, messages, and `created_by_user_id` stay. The creator may leave, including when they are the last active member. HTTP 200 in all three cases.

## `GET /api/v1/community/{communityId}/messages`

Active membership required. Omit `cursor` for the newest page. Pass `nextCursor` for the next older page.

`limit` is optional. Omitted means 30. Allowed range is 1 through 50 (`app.community.max-message-page-size`).

Messages in the page are oldest first. The last item is the newest in that page. `hasMore` false and `nextCursor` null end the history. The cursor is opaque. Send it back unchanged.

```json
{
  "id": 42,
  "communityId": 10,
  "sender": { "userId": 7, "username": "ada", "fullName": "Ada Lovelace" },
  "content": "Has anyone heard back?",
  "createdAt": "2026-10-16T13:00:00"
}
```

A bad cursor is 400 `Message history cursor is invalid.` The response never contains every message in the community. Header and members stay on their own routes.

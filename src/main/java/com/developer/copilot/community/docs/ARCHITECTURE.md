# Community architecture

The community module is a job-posting group chat inside the existing application. It does not add a service, a database, or a second user system. Package: `com.developer.copilot.community`.

## Responsibilities

| Piece | Owns |
| --- | --- |
| Account | One opt-in row per user after they accept the current terms |
| Matching | Normalized posting URL to one community. Title and company are not identity |
| Creation | Community, URL aliases, and the creator's membership in one transaction |
| Membership | Explicit join, leave, and rejoin of the same row |
| History | Stored text and cursor pages |
| Live | STOMP send that calls the history send service, then broadcasts the saved message |
| Presence | In-memory sockets for this process |

`users` and `jobs` stay the sources of truth. A community never stores `jobs.id` as its identity. The canonical key is the SHA-256 hex of the primary normalized posting URL.

## Request path

REST uses the existing JWT filter and `webFrontendComplete()`: web token, username present, not a browser-extension token. Community routes then call `CommunityAccountService.requireActiveAccount()`. Member-only routes and the socket also require an `ACTIVE` membership.

The socket handshake at `/ws/community` is anonymous so a browser can open it. The STOMP `CONNECT` frame carries `Authorization: Bearer <access token>`.

## Transactions and races

Account create, community create, and join each use `REQUIRES_NEW`. A unique-key loser reads the winning row instead of returning SQL.

- `uk_community_account_user` — one account per user
- `uk_community_canonical_posting_key` and `uk_community_url_alias_hash` — one community per posting URL
- `uk_community_name` — generated name, retried once with a hash suffix
- `uk_community_membership_community_user` — one membership row per user per community, including rejoin

Leave updates that membership row. It does not delete the community or messages. The creator column is immutable. If the creator leaves, the community stays open.

Message send flushes the row, then publishes `CommunityMessagePersisted`. Inside a transaction the event waits until after commit. The STOMP listener only then copies the saved payload to subscribers.

## What this module does not do

No community or message deletion, no media, no reactions, no direct messages, no search, no unread counters, and no Redis chat bus. Description refinement sends only the description text to the existing AI service. If the model fails, the submitted text is kept.

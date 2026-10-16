# Community WebSocket

STOMP over WebSocket. One application process. Presence is not stored and is not shared across instances. MySQL history is the shared record.

## Connect

1. Open `ws://<host>/ws/community` or `wss` in production. The HTTP upgrade does not send the token. Browsers cannot set `Authorization` on that request, so the path is allowed through HTTP security.
2. STOMP `CONNECT` with header `Authorization: Bearer <access token>`.
3. Do not put the token in the URL. The server does not log it.

Origins are `cors.allowed-origins` after `*` and blanks are removed. The extension origin is not added. Extension tokens fail at `CONNECT`.

| CONNECT failure | Frame text |
| --- | --- |
| Missing, bad, or expired token | `Unauthorized.` |
| Extension token | `This client is not authorized to access this resource.` |
| Username missing | `Complete your username to continue.` |

The socket closes on those failures.

Heartbeats default to 10 seconds each way (`app.community.ws.heartbeat-ms`). A client that stops them is disconnected. Every 15 seconds (`app.community.ws.presence-sweep-ms`) a session the broker has already dropped is removed from presence. Frames larger than 64 KB are rejected.

## Client send

Only:

`/app/communities/{communityId}/messages`

```json
{ "content": "Has anyone heard back?" }
```

Any other field, including `userId`, is rejected. The server checks the community account and active membership, stores the text, and only then broadcasts. A database failure does not broadcast. The sender gets `/user/queue/errors` with code `SEND_FAILED` and message `The message could not be sent.`

Sending to `/topic/...` is rejected. That path is server-to-client only.

## Client subscribe

| Destination | Payload |
| --- | --- |
| `/topic/communities/{communityId}/messages` | The saved message. Same fields as history: `id`, `communityId`, `sender.userId`, `sender.username`, `sender.fullName`, `content`, `createdAt` |
| `/topic/communities/{communityId}/presence` | `{ communityId, userId, username, fullName, status }` where `status` is `ONLINE` or `OFFLINE` |
| `/user/queue/errors` | `{ code, message }` |
| `/user/queue/presence` | `{ communityId, online: [ presence objects ] }` when a presence subscribe is accepted |
| `/user/queue/membership` | `{ communityId, userId, membership }` where `membership` is `MEMBER` or `NOT_A_MEMBER` |

Subscribe checks the account and active membership. A second subscribe to the same topic is ignored so the message is not delivered twice.

## Presence

A person is online in a community while this process has at least one of their sockets and they are an active member. The first socket announces `ONLINE`. Another tab does not announce again. Closing one tab does not announce `OFFLINE` while another remains. The last tab does. Leave announces `OFFLINE` for that community even if other tabs stay connected to other communities.

## After leave

The REST leave updates the membership row. The socket then stops receiving that community's topics. `/user/queue/membership` tells the client to unsubscribe. A later send or subscribe is rejected with `MEMBERSHIP_REQUIRED`.

## Reconnect

Load `GET /api/v1/community/{communityId}/messages`, connect, and subscribe again. Drop a live event whose `id` is already on screen. The socket is not the history.

## Error codes on `/user/queue/errors`

`MEMBERSHIP_REQUIRED`, `ACCOUNT_REQUIRED`, `TERMS_REQUIRED`, `COMMUNITY_UNAVAILABLE`, `INVALID_MESSAGE`, `DESTINATION_NOT_ALLOWED`, `SEND_FAILED`.

The messages match the REST strings in [ERROR-HANDLING.md](ERROR-HANDLING.md), except `SEND_FAILED` and `DESTINATION_NOT_ALLOWED`, which exist only on the socket.

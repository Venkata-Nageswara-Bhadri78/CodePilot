# Community errors

Every community HTTP error uses the shared envelope. `success` is false, `data` is null, and `message` is safe to show. There is no separate error-code field. The message string is stable.

```json
{
  "success": false,
  "message": "Join this community to continue.",
  "data": null,
  "timestamp": "2026-10-16T15:00:00"
}
```

## Shared access

| Status | Message | When |
| --- | --- | --- |
| 401 | `Unauthorized.` | No usable web JWT |
| 403 | `This client is not authorized to access this resource.` | Browser-extension token |
| 403 | `Complete your username to continue.` | Username not chosen |
| 403 | `A community account is required. Accept the current terms to create one.` | No community account |
| 403 | `The community terms have changed. Accept the current version to continue.` | Stored terms version is not current |
| 403 | `Join this community to continue.` | History, members, or the community header, and the caller is not an active member |
| 404 | `This community is not available.` | Community id does not exist |
| 400 | `Invalid community id.` | Community id in the path is not a number |
| 400 | `Community id must be a positive number.` | Zero or negative |
| 400 | `Invalid job id.` | Job id in the path is not a number |
| 404 | `Job not found with id: {id}` | Saved job is missing or belongs to someone else |

Unknown JSON fields, including `userId`, are 400. The message depends on the route: account consent, community create, or join/leave.

An integrity error that the service does not recognize is 409 `The request conflicts with existing data. Please retry.` The body does not include SQL.

## Account

| Status | Message |
| --- | --- |
| 400 | `You must accept the community terms.` |
| 400 | `Terms version is required.` |
| 400 | `Accept the current community terms version.` |
| 400 | `Send only accepted and termsVersion. A user id is not accepted.` |

`GET /api/v1/community/account` is 200 for every consent state, including when an account does not exist yet.

## Match and create

| Status | Message |
| --- | --- |
| 200 | `No community exists for this posting yet.` |
| 200 | `A community already exists for this posting.` |
| 200 body outcome `MATCH_UNCERTAIN` | `This URL is not specific enough to identify one job posting. Open the posting page and use that URL, including any job id.` |
| 409 on create | Same uncertain message, or `These URLs belong to different communities. Remove the URL that does not match this posting.` |
| 201 | `Community created.` |

Resolve returns uncertain and conflict as 200 with an outcome. Create returns 409 for those two so nothing is inserted.

## Membership

Join `201` is only the first join (`JOINED`). Rejoin and already-a-member are `200`. Leave is always `200`, including a repeated leave and a leave from someone who never joined.

| Outcome | Message |
| --- | --- |
| `JOINED` | `You joined the community.` |
| `ALREADY_MEMBER` | `You are already a member of this community.` |
| `REJOINED` | `You rejoined the community.` |
| `LEFT` | `You left the community. It remains open.` |
| `ALREADY_LEFT` | `You have already left this community.` |
| `NOT_A_MEMBER` | `You are not a member of this community.` |
| `MEMBER` | `You are a member of this community.` |

## History

| Status | Message |
| --- | --- |
| 400 | `Message history cursor is invalid.` |
| 400 | `Message page size must be between 1 and {max}.` |

There is no REST send. Invalid live text is a STOMP error. See [WEBSOCKET.md](WEBSOCKET.md).

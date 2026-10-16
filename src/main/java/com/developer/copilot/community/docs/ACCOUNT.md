# Community account and terms

Consent is the only community HTTP API in this slice. Creating or joining a group is not available yet.

The signed-in user is taken from the JWT through `CurrentUserService`. The request body cannot name the account owner.

## Routes

Both routes are `/api/v1/community/account`. They require a normal web access token. Extension tokens and users who still need a username are rejected by the existing security rules.

### `GET /api/v1/community/account`

Reads consent. Does not insert a row.

| | |
| --- | --- |
| Auth | `Authorization: Bearer <access JWT>` |
| Body | none |
| Success | `200` |

`data.state` is one of:

| State | Meaning |
| --- | --- |
| `TERMS_ACCEPTANCE_REQUIRED` | No community account. Signup did not create one. |
| `TERMS_REACCEPTANCE_REQUIRED` | An account exists, but `acceptedTermsVersion` is not the configured current version. |
| `ACCOUNT_ACTIVE` | An account exists and it accepted the current version. |

`termsAcceptanceRequired` is true for the first two states. `account` is null only for the first.

Example when the user has never accepted (`app.community.terms-version=1`):

```json
{
  "success": true,
  "message": "Community terms must be accepted before an account can be created.",
  "data": {
    "state": "TERMS_ACCEPTANCE_REQUIRED",
    "accountPresent": false,
    "termsAcceptanceRequired": true,
    "currentTermsVersion": "1",
    "account": null
  },
  "timestamp": "2026-10-15T16:40:00"
}
```

### `POST /api/v1/community/account`

Accepts the **current** terms.

| Field | Type | Required | Rule |
| --- | --- | --- | --- |
| `accepted` | boolean | yes | Must be `true`. `false` or missing is not consent. |
| `termsVersion` | string | yes | Max 64 characters. After trim it must equal `currentTermsVersion`. |

Unknown JSON fields, including `userId`, are rejected.

```json
{
  "accepted": true,
  "termsVersion": "1"
}
```

| Result | Status | Message |
| --- | --- | --- |
| First acceptance | `201` | `Community account created.` |
| Same current version again | `200` | `Community account is active.` |
| Older account accepts the new current version | `200` | `Community terms accepted.` |

The success body is the same shape as GET, with `state` `ACCOUNT_ACTIVE`.

A repeated accept of the same version does not change `termsAcceptedAt` and does not insert a second row. Two simultaneous first accepts also leave one row; the loser receives that row.

## Terms version rule

`app.community.terms-version` is the version that counts as current. The default is `1`. Comparison is exact after trim, and it is case-sensitive.

When that property changes, every account whose `accepted_terms_version` differs is not current. GET returns `TERMS_REACCEPTANCE_REQUIRED`. Later community actions must call `CommunityAccountService.requireActiveAccount()`, which throws instead of treating the old acceptance as enough. Accepting the new version updates the same row (`accepted_terms_version` and `terms_accepted_at`). Accepting the old version returns `400` with `Accept the current community terms version.`

## Errors

The envelope is the existing `ApiResponse`: `success`, `message`, `data`, `timestamp`. Error `data` is null.

| Situation | Status | Message |
| --- | --- | --- |
| No JWT, or a bad JWT | `401` | `Unauthorized.` |
| Browser-extension token | `403` | `This client is not authorized to access this resource.` |
| Username still missing | `403` | `Complete your username to continue.` |
| `accepted` is missing or false | `400` | `accepted: You must accept the community terms.` |
| `termsVersion` is blank | `400` | `termsVersion: Terms version is required.` |
| `termsVersion` is longer than 64 | `400` | `termsVersion: Terms version cannot exceed 64 characters.` |
| Version is not the current one | `400` | `Accept the current community terms version.` |
| Unknown field such as `userId` | `400` | `Send only accepted and termsVersion. A user id is not accepted.` |
| No account, when a later feature calls the gate | `403` | `A community account is required. Accept the current terms to create one.` |
| Stale terms, when a later feature calls the gate | `403` | `The community terms have changed. Accept the current version to continue.` |

Validation messages use the existing `field: message` form. The account response never includes email, password, username, full name, or a user id.

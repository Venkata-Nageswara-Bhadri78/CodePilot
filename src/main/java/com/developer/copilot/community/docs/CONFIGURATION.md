# Community configuration

All keys have Java defaults. They are not secrets. Copy them from `application.properties.example` when a local file should set them.

| Key | Default | Meaning |
| --- | --- | --- |
| `app.community.terms-version` | `1` | Exact terms string a community account must have accepted. Changing it makes existing accounts stale |
| `app.community.max-url-aliases` | `8` | Alternative posting URLs on one create, besides the primary URL. Bean validation also caps the list at 20 |
| `app.community.max-message-length` | `4000` | Message length in Unicode code points. Emoji count as one |
| `app.community.message-page-size` | `30` | History page size when `limit` is omitted. Cannot exceed the max page size |
| `app.community.max-message-page-size` | `50` | Largest history page |
| `app.community.ws.heartbeat-ms` | `10000` | STOMP heartbeat both ways |
| `app.community.ws.presence-sweep-ms` | `15000` | How often to drop a presence session the broker has already closed |

Field lengths that are not properties: title, company, and location 255; description 50000; URL 2000.

The socket origin list is `cors.allowed-origins` after blank values and `*` are removed. Community sockets do not add the browser-extension origin.

OpenAPI for this module is the existing springdoc group `community` (`/v3/api-docs/community` when not running `prod` or `production`). Live STOMP is not part of that JSON. It is described in [WEBSOCKET.md](WEBSOCKET.md).

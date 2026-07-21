# vespa-relay

A standalone Nostr relay with trust-ranked search. It serves full-text
[NIP-50](https://github.com/nostr-protocol/nips/blob/master/50.md) search, ranked by
the searcher's web of trust, backed by [Vespa](https://vespa.ai).

Point a [Nostr](https://nostr.com) client at it. It speaks NIP-01 filters and NIP-50
search over websockets. Log in with NIP-42 to rank results by your own web of trust.
Anonymous searches use the operator's default. A web search UI ships on the same port.

It serves what is in the store. Filling the store from the network — crawling and
trust-sync — is a separate job.

## Run it

One command stands up a single-node Vespa plus the relay:

```bash
docker compose up --build
# relay + web UI on ws://localhost:7777
```

On first run the relay deploys the bundled Vespa schema, then serves. Open
`http://localhost:7777` for the search UI, or connect a Nostr client to the websocket.

To run against an existing Vespa, without Docker:

```bash
RELAY_URL=wss://relay.example.com VESPA_URL=http://localhost:8080 ./gradlew :relay:run
```

Vespa is a prerequisite, like a database. `docker compose up` stands one up for you.
Otherwise point `VESPA_URL` at your own.

## Configuration

All configuration is through environment variables.

### Core

| var | meaning | default |
|---|---|---|
| `RELAY_URL` | this relay's own ws url — its NIP-42 identity and NIP-62 vanish scope | **required** |
| `VESPA_URL` | the Vespa query endpoint | `http://localhost:8080` |
| `RELAY_PORT` | port to listen on | `7777` |
| `DEFAULT_OBSERVER` | 64-hex pubkey whose web of trust ranks anonymous searches | unset ⇒ untrusted |
| `AUTO_DEPLOY` | deploy the bundled schema on first run | `true` |
| `LOG_CONNECTIONS` | log the live connection count on connect/disconnect | `false` |

### Relay identity (NIP-11)

| var | meaning | default |
|---|---|---|
| `RELAY_NAME` / `RELAY_DESCRIPTION` / `RELAY_ICON` / `RELAY_BANNER` | how the relay presents itself | — |
| `RELAY_CONTACT` | a human contact | — |
| `RELAY_CONTACT_PUBKEY` / `RELAY_SELF_PUBKEY` | the relay's contact and self pubkeys | — |
| `RELAY_VERSION` | overrides the build version | — |
| `RELAY_POSTING_POLICY` / `RELAY_PRIVACY_POLICY` / `RELAY_TERMS_OF_SERVICE` | policy urls | — |

### Limits

| var | meaning | default |
|---|---|---|
| `MAX_MESSAGE_LENGTH` / `MAX_SUBSCRIPTIONS` / `MAX_FILTERS` / `MAX_LIMIT` / `DEFAULT_LIMIT` / `MAX_SUBID_LENGTH` / `MAX_EVENT_TAGS` / `MAX_CONTENT_LENGTH` / `MIN_POW_DIFFICULTY` / `CREATED_AT_LOWER_LIMIT` / `CREATED_AT_UPPER_LIMIT` | protection limits, enforced by the engine and shown in the NIP-11 `limitation` block | sane defaults |
| `NEG_FRAME_SIZE_LIMIT` / `NEG_MAX_SYNC_EVENTS` / `NEG_MAX_SESSIONS_PER_CONNECTION` | NIP-77 negentropy tuning (`NEG_MAX_SYNC_EVENTS` caps how many ids one reconciliation walks) | strfry-parity |

### Access control

| var | meaning | default |
|---|---|---|
| `ALLOW_PUBKEYS` / `DENY_PUBKEYS` | write authorization by pubkey — allowlist (empty ⇒ everyone) minus denylist, 64-hex, comma/space-separated | — |
| `ALLOW_KINDS` / `DENY_KINDS` | write authorization by kind — allow (empty ⇒ all) minus deny | — |
| `REJECT_FUTURE_SECONDS` | reject events dated more than N seconds in the future | `0` (off) |
| `EXPIRATION_SWEEP_SECONDS` | how often to prune NIP-40 expired events | `3600` (0 ⇒ off) |

### Admin (NIP-86)

| var | meaning | default |
|---|---|---|
| `RELAY_ADMIN_PUBKEYS` | comma/space-separated 64-hex admin keys; when set, enables the NIP-86 management API (`POST /`, NIP-98 auth) | unset ⇒ off |
| `RELAY_STATE_FILE` | path where NIP-86 ban/allow lists are persisted (survives restart) | unset ⇒ in-memory |
| `RELAY_HTTP_URL` | the http(s) url NIP-98 auth events must be tagged with | derived from `RELAY_URL` |

## What it supports

NIP-01 (filters/publishes), 09 (deletion), 11 (relay info), 40 (expiration), 42
(auth → per-user ranking), 45 (COUNT), 50 (search, plus the `sort:` / `filter:rank:` /
`include:spam` / `observer:` extensions), 62 (vanish), 77 (negentropy), and — when
`RELAY_ADMIN_PUBKEYS` is set — 86 (relay management: ban/allow pubkeys, events, and
kinds, and change name/description/icon at runtime).

## Embed it

The relay also runs inside your own JVM/Ktor app. `serveRelay(relay, port, ...)` binds a
port batteries-included, or `Route.nostrRelay(relay)` and friends mount the pieces in an
existing server. See `NostrRelayServer` and `RelayApp.kt`.

## Build

```bash
./gradlew build              # compile + tests + spotlessCheck
./gradlew :relay:run         # run against VESPA_URL / RELAY_URL from the environment
./gradlew :relay:installDist # a runnable distribution under relay/build/install/vespa-relay
```

Kotlin 2.4 / JDK 21. Quartz and the [vespa-eventstore](https://github.com/vitorpamplona/vespa-eventstore)
store come from JitPack, pinned by commit in `gradle/libs.versions.toml`.

## License

MIT © Vitor Pamplona

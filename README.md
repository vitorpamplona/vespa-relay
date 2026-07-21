# vespa-relay

A standalone, trust-ranking [Nostr](https://nostr.com) relay — full-text
[NIP-50](https://github.com/nostr-protocol/nips/blob/master/50.md) search ranked by
the searcher's web of trust, backed by [Vespa](https://vespa.ai).

Point clients at it and they speak plain NIP-01 filters and NIP-50 search over
websockets. A NIP-42 login makes results ranked by *your* web of trust; anonymous
searches rank by the operator's default. It bundles a web search UI (itself a Nostr
client) served on the same port.

This is the relay half of [SoT](https://github.com/vitorpamplona/sot) (Search over
Trust), split out to run on its own. It serves what's in the store; **filling** the
store from the network (crawl / trust-sync) is a separate service — that's SoT.

## Run it

The whole thing in one command — a single-node Vespa plus the relay:

```bash
docker compose up --build
# NIP-50 relay + web UI on ws://localhost:7777
```

The relay deploys the bundled Vespa schema on first run, then serves. Open
`http://localhost:7777` for the search UI, or connect a Nostr client to the websocket.

Or run it against an existing Vespa without Docker:

```bash
RELAY_URL=wss://relay.example.com VESPA_URL=http://localhost:8080 ./gradlew :relay:run
```

### Configuration (environment)

| var | meaning | default |
|---|---|---|
| `RELAY_URL` | this relay's own ws url — its NIP-42 identity / NIP-62 vanish scope | **required** |
| `VESPA_URL` | the Vespa query endpoint | `http://localhost:8080` |
| `RELAY_PORT` | port to listen on | `7777` |
| `DEFAULT_OBSERVER` | 64-hex pubkey whose web of trust ranks anonymous searches | unset ⇒ untrusted |
| `AUTO_DEPLOY` | deploy the bundled schema on first run | `true` |
| `RELAY_NAME` / `RELAY_DESCRIPTION` / `RELAY_ICON` / `RELAY_BANNER` / `RELAY_CONTACT_PUBKEY` / `RELAY_SELF_PUBKEY` / `RELAY_CONTACT` / `RELAY_VERSION` / `RELAY_POSTING_POLICY` / `RELAY_PRIVACY_POLICY` / `RELAY_TERMS_OF_SERVICE` | the NIP-11 identity (`RELAY_CONTACT` is a human contact; `RELAY_VERSION` overrides the build version) | — |
| `MAX_MESSAGE_LENGTH` / `MAX_SUBSCRIPTIONS` / `MAX_FILTERS` / `MAX_LIMIT` / `DEFAULT_LIMIT` / `MAX_SUBID_LENGTH` / `MAX_EVENT_TAGS` / `MAX_CONTENT_LENGTH` / `MIN_POW_DIFFICULTY` / `CREATED_AT_LOWER_LIMIT` / `CREATED_AT_UPPER_LIMIT` | protection limits — enforced by the engine and rendered into the NIP-11 `limitation` block | sane defaults |
| `NEG_FRAME_SIZE_LIMIT` / `NEG_MAX_SYNC_EVENTS` / `NEG_MAX_SESSIONS_PER_CONNECTION` | NIP-77 negentropy tuning (`NEG_MAX_SYNC_EVENTS` caps how many ids one reconciliation walks) | strfry-parity |
| `ALLOW_PUBKEYS` / `DENY_PUBKEYS` | static write authorization — allowlist (empty ⇒ everyone) minus denylist, 64-hex, comma/space-separated | — |
| `ALLOW_KINDS` / `DENY_KINDS` | static kind authorization — allow (empty ⇒ all) minus deny | — |
| `REJECT_FUTURE_SECONDS` | reject events dated more than N seconds in the future | `0` (off) |
| `EXPIRATION_SWEEP_SECONDS` | how often to prune NIP-40 expired events | `3600` (0 ⇒ off) |
| `LOG_CONNECTIONS` | log the live websocket connection count on connect/disconnect | `false` |
| `RELAY_ADMIN_PUBKEYS` | comma/space-separated 64-hex admin keys; when set, enables the NIP-86 management API (`POST /`, NIP-98 auth) | unset ⇒ NIP-86 off |
| `RELAY_STATE_FILE` | path where NIP-86 ban/allow lists are persisted (survives restart) | unset ⇒ in-memory |
| `RELAY_HTTP_URL` | the http(s) url NIP-98 auth events must be tagged with | derived from `RELAY_URL` |

Vespa is a prerequisite, like a database. `docker compose up` stands one up for you;
otherwise point `VESPA_URL` at your own.

## What it implements

NIP-01 (filters/publishes), 09 (deletion), 11 (relay info), 40 (expiration), 42
(auth → per-user ranking), 45 (COUNT), 50 (search, plus the `sort:` / `filter:rank:` /
`include:spam` / `observer:` extensions), 62 (vanish), 77 (negentropy), and — when
`RELAY_ADMIN_PUBKEYS` is set — 86 (relay management: ban/allow pubkeys, events, kinds;
change name/description/icon at runtime, NIP-98-authenticated on `POST /`).

Published events have their id + signature verified in the ingest queue's parallel
stage (off the connection's hot path) before they reach the store — the store itself
never verifies, and it also holds unsigned rumors. One connection can't exhaust the
server: per-connection limits are enforced by the engine, and a client that stops
draining its subscription is disconnected rather than buffered without bound.

## Embed it instead

The relay is also usable from your own JVM/Ktor app — `serveRelay(relay, port, nip11,
limits, supportedNips, admin, landingPage)` binds a port batteries-included (every
argument after `nip11` is optional), or `Route.nostrRelay(relay)` + `relayInfoJson(...)`
+ `Route.nip86Admin(...)` give you the pieces to mount in an existing server. See
`NostrRelayServer` and `RelayApp.kt`.

## Build

```bash
./gradlew build     # compile + tests + spotlessCheck
./gradlew :relay:run        # run against VESPA_URL / RELAY_URL from the environment
./gradlew :relay:installDist # a runnable distribution under relay/build/install/vespa-relay
```

Kotlin 2.4 / JDK 21. Quartz and the [vespa-eventstore](https://github.com/vitorpamplona/vespa-eventstore)
store come from JitPack, pinned by commit in `gradle/libs.versions.toml`.

## License

MIT © Vitor Pamplona

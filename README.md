# vespa-relay

A ready-to-serve [Nostr](https://nostr.com) relay over a
[vespa-eventstore](https://github.com/vitorpamplona/vespa-eventstore) store, with
**trust-ranked NIP-50 search**.

It wires [Quartz](https://github.com/vitorpamplona/amethyst)'s relay protocol engine
(`RelayServerBase` + `LiveEventStore`) to a `VespaEventStore`, adds a per-connection
ranking observer, and hands you a Ktor websocket mount and the NIP-11 document. Point
clients at it and they speak plain NIP-01 filters and NIP-50 search — ranked, when
authenticated, by the searcher's own web of trust.

This is the relay front door extracted from [SoT](https://github.com/vitorpamplona/sot)
(Search over Trust). SoT is the reference application — relay + trust-sync — built on
this library and vespa-eventstore.

## What you get

- **A Nostr relay in a few lines.** `NostrRelayServer` over any Quartz `IEventStore`
  (use `vespa-eventstore`'s), inheriting full NIP-01 filters, NIP-50 search, live
  subscriptions, EVENT publishes, NIP-45 COUNT, and server-side NIP-77 negentropy.
- **Per-connection trust ranking.** NIP-42 auth switches the ranking observer: a
  search runs through the authenticated user's web of trust, or the operator's
  default when anonymous. The `onObserver` callback lets your app react to logins
  (e.g. enroll them for sync) without the relay knowing anything about it.
- **The NIP-11 doc and a websocket route**, ready to mount in your Ktor app.

## Quick start

```kotlin
dependencies {
    implementation("com.vitorpamplona.quartz.eventstore:relay:0.1.0")
    implementation("com.vitorpamplona.quartz.eventstore:store:0.1.0")
}
```

Released to Maven Central. For a commit snapshot, JitPack works too:
`com.github.vitorpamplona.vespa-relay:relay:<commit>`.

```kotlin
import com.vitorpamplona.quartz.eventstore.relay.NostrRelayServer
import com.vitorpamplona.quartz.eventstore.relay.nostrRelay
import com.vitorpamplona.quartz.eventstore.relay.relayInfoJson
import com.vitorpamplona.quartz.eventstore.store.VespaEventStore

val store = VespaEventStore.open("http://localhost:8080")
val relay = NostrRelayServer(
    store = store,
    defaultObserver = housePubkeyHex,       // ranks anonymous searches; null = untrusted
    relayUrl = myRelayUrl,                   // NIP-42 identity / NIP-62 vanish scope
    onObserver = { pubkey -> /* a NIP-42 login — enroll for sync, etc. */ },
)

// Mount it in a Ktor app, beside the NIP-11 doc served on GET / (nostr+json).
routing { nostrRelay(relay) }
// relayInfoJson(name = "my relay", selfPubkey = relayPubkey)  ->  the NIP-11 body
```

The store never verifies signatures (many Nostr events are rumors), so the relay runs
a `VerifyPolicy` on publishes — forged EVENTs are rejected before they reach the store.

## Modules

- **`:relay`** — `NostrRelayServer` (the protocol engine over the store), the
  `Route.nostrRelay` websocket mount, and `relayInfoJson` (the NIP-11 doc).

## Build

```bash
./gradlew build     # compile + tests + spotlessCheck
```

Kotlin 2.4 / JDK 21. Quartz and vespa-eventstore come from JitPack, pinned by commit
in `gradle/libs.versions.toml`.

## Releasing

Publishing uses the [vanniktech Maven Publish](https://github.com/vanniktech/gradle-maven-publish-plugin)
plugin (the same one Quartz ships to Central with).

- **CI** (`.github/workflows/build.yml`) runs `./gradlew build` on every push and PR to `main`.
- **Release** (`.github/workflows/create-release.yml`) publishes to Maven Central on a
  `v*` tag. It needs four repo secrets: `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`,
  `SIGNING_PRIVATE_KEY` (armored GPG key), `SIGNING_PASSWORD`.

Bump the version in `gradle/libs.versions.toml` (`app`), tag `vX.Y.Z`, and push the tag.

## License

MIT © Vitor Pamplona

/*
 * Copyright (c) 2026 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.eventstore.relay

import com.vitorpamplona.quartz.eventstore.store.VespaEventStore
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * Run a standalone trust-ranking Nostr relay against a Vespa. This is the
 * serve-only half — it opens the store, serves the NIP-50 relay + NIP-11 doc, and
 * blocks. Crawling/trust-sync (filling the store from the network) is a separate
 * concern; SoT (github.com/vitorpamplona/sot) is the reference app that adds it.
 *
 * Configuration is entirely from the environment:
 *
 *   VESPA_URL           the Vespa query endpoint          (default http://localhost:8080)
 *   RELAY_PORT          the port to listen on             (default 7777)
 *   RELAY_URL           this relay's own ws url — its NIP-42 identity and NIP-62
 *                       vanish scope                      (REQUIRED)
 *   DEFAULT_OBSERVER    64-hex pubkey whose web of trust ranks anonymous searches;
 *                       unset ⇒ anonymous searches are untrusted
 *   AUTO_DEPLOY         deploy the bundled schema on first run (default true)
 *   RELAY_NAME / RELAY_DESCRIPTION / RELAY_ICON / RELAY_CONTACT_PUBKEY /
 *   RELAY_SELF_PUBKEY   the NIP-11 identity fields
 */
fun main() {
    val env = System.getenv()
    val vespaUrl = env["VESPA_URL"] ?: "http://localhost:8080"
    val port = env["RELAY_PORT"]?.toIntOrNull() ?: 7777
    val relayUrlRaw = env["RELAY_URL"] ?: error("RELAY_URL is required — this relay's own ws url (NIP-42 identity / NIP-62 vanish scope).")
    val relayUrl =
        RelayUrlNormalizer.normalizeOrNull(relayUrlRaw)
            ?: error("RELAY_URL '$relayUrlRaw' is not a valid relay url.")
    val autoDeploy = env["AUTO_DEPLOY"]?.toBooleanStrictOrNull() ?: true

    val store = VespaEventStore.open(vespaUrl, relay = relayUrl, autoDeploy = autoDeploy)
    val relay =
        NostrRelayServer(
            store = store,
            defaultObserver = env["DEFAULT_OBSERVER"],
            relayUrl = relayUrl,
        )
    Runtime.getRuntime().addShutdownHook(
        Thread {
            relay.close()
            store.close()
        },
    )

    println("vespa-relay listening on :$port  (vespa $vespaUrl, relay $relayUrl)")
    serveRelay(
        relay = relay,
        port = port,
        nip11 =
            Nip11Info(
                name = env["RELAY_NAME"] ?: "vespa-relay",
                description = env["RELAY_DESCRIPTION"],
                icon = env["RELAY_ICON"],
                contactPubkey = env["RELAY_CONTACT_PUBKEY"],
                selfPubkey = env["RELAY_SELF_PUBKEY"],
            ),
        // The bundled web UI (a NIP-50 client) — served on a plain browser GET.
        landingPage = webUi(),
    )
}

/** The bundled search UI (`resources/index.html`), or null if it isn't on the classpath. */
private fun webUi(): String? = object {}.javaClass.getResource("/index.html")?.readText()

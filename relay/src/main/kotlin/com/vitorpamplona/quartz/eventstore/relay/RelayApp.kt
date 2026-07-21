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

import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets

/**
 * The NIP-11 relay identity served on `GET /` (Accept: application/nostr+json).
 * Everything but [name] is optional. [selfPubkey] is the relay's OWN key;
 * [contactPubkey] is the admin contact.
 */
data class Nip11Info(
    val name: String = "vespa-relay",
    val description: String? = null,
    val icon: String? = null,
    val contactPubkey: String? = null,
    val selfPubkey: String? = null,
)

/**
 * Stand up a complete relay on [port], batteries-included (this bundles the Ktor
 * Netty engine):
 *
 *   WS   /  -> the NIP-50 relay ([nostrRelay]: full filters, search, COUNT, NIP-77)
 *   GET  /  -> the NIP-11 doc on Accept: application/nostr+json, else [landingPage]
 *              (e.g. a web UI) — or a plain notice when it is null.
 *
 * Returns the started server; call `stop(...)` to shut it down. With [wait] = true
 * (the default) it blocks until the server stops, which is what a `main()` wants;
 * pass false to keep serving in the background and manage the lifecycle yourself.
 *
 * The store is NOT owned here — the caller opens and closes it (the relay and, in
 * a fuller app, the sync side share one store).
 */
fun serveRelay(
    relay: NostrRelayServer,
    port: Int,
    nip11: Nip11Info,
    landingPage: String? = null,
    wait: Boolean = true,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, port = port) {
        install(WebSockets)
        routing {
            nostrRelay(relay)
            get("/") {
                val accept = call.request.headers["Accept"] ?: ""
                if (accept.contains("application/nostr+json")) {
                    call.respondText(
                        relayInfoJson(
                            name = nip11.name,
                            description = nip11.description,
                            icon = nip11.icon,
                            contactPubkey = nip11.contactPubkey,
                            selfPubkey = nip11.selfPubkey,
                        ),
                        ContentType.parse("application/nostr+json"),
                    )
                } else {
                    landingPage?.let { call.respondText(it, ContentType.Text.Html) }
                        ?: call.respondText("${nip11.name} - a NIP-50 search relay; connect a WebSocket here.")
                }
            }
        }
    }.start(wait = wait)

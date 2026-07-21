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

import com.vitorpamplona.quartz.eventstore.store.ObserverContext
import com.vitorpamplona.quartz.eventstore.store.OriginalFilters
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayServerBase
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayServerListener
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.LiveEventStore
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.SessionBackend
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.OptionalAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyStack
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyAuthOnlyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanListPolicy
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * The Nostr relay: Quartz's protocol engine over the Vespa-backed store.
 * It provides one store, full NIP-01 filters, NIP-50 search, live
 * subscriptions, EVENT publishes, NIP-45 COUNT, and server-side NIP-77
 * negentropy. Most of this is inherited from [RelayServerBase] and
 * [LiveEventStore]. The store supplies storage semantics and
 * `snapshotIdsForNegentropy`.
 *
 * Each connection runs a small policy stack. [VerifyAuthOnlyPolicy] verifies
 * NIP-42 AUTH events inline; EVENT publishes are NOT verified here — instead
 * [IngestQueue] verifies their id and signature in a parallel stage off the
 * hot path (the store itself never verifies). An optional [BanListPolicy]
 * (present only when NIP-86 admin is configured) rejects banned pubkeys and
 * event ids before they reach the queue. [OptionalAuthPolicy] sends a NIP-42
 * challenge on connect but gates nothing on it.
 *
 * [limits] are enforced by the engine (which auto-installs a LimitsPolicy) and
 * also drive the NIP-11 `limitation` block, so what the doc advertises and
 * what the relay rejects can never disagree. [negentropySettings] bound NIP-77
 * reconciliation (frame size, max events synced, sessions per connection).
 *
 * What auth does change is ranking. [ObserverRoutingBackend] resolves the
 * observer for every REQ/COUNT: the authenticated pubkey, or else
 * [defaultObserver]. This makes a search run through the caller's own web of
 * trust.
 *
 * [close] shuts down the connections and the ingest writer, but not the
 * store. The composition root owns the store, and the sync service shares it.
 */
class NostrRelayServer(
    store: IEventStore,
    defaultObserver: String?,
    relayUrl: NormalizedRelayUrl,
    parentContext: CoroutineContext = SupervisorJob(),
    listener: RelayServerListener = RelayServerListener.None,
    limits: RelayLimits? = defaultRelayLimits(),
    negentropySettings: NegentropySettings = NegentropySettings.Default,
    // When set, published events from banned pubkeys / banned event ids are
    // rejected before ingest. Shared with the NIP-86 admin endpoint, which
    // mutates the same store.
    banStore: BanStore? = null,
    // Fires with each authenticated pubkey seen on a ranked read. This lets
    // the composition root enroll NIP-42 logins as sync observers
    // (SyncService.enroll dedups).
    onObserver: ((String) -> Unit)? = null,
) : RelayServerBase(
        // Events are verified in the ingest queue's parallel stage, so the
        // policy only verifies AUTH. BanListPolicy (if present) rejects banned
        // sources first; OptionalAuthPolicy issues the NIP-42 challenge.
        policyBuilder = {
            PolicyStack(
                *listOfNotNull(
                    banStore?.let(::BanListPolicy),
                    VerifyAuthOnlyPolicy,
                    OptionalAuthPolicy(relayUrl),
                ).toTypedArray<IRelayPolicy>(),
            )
        },
        parentContext = parentContext,
        negentropySettings = negentropySettings,
        listener = listener,
        limits = limits,
    ) {
    // The queue verifies each event's id + signature in its own parallel stage
    // (off the connection's hot path), which is why the policy stack only needs
    // VerifyAuthOnlyPolicy. Invalid events get an InsertOutcome.Rejected -> OK:false.
    private val ingest = IngestQueue(store = store, parentContext = parentContext, verify = { it.verify() })

    override val backend: SessionBackend = ObserverRoutingBackend(LiveEventStore(store, ingest), defaultObserver, onObserver)

    override fun close() {
        closeConnections()
        ingest.close()
        scope.cancel()
    }
}

/**
 * Delegates everything to [LiveEventStore], wrapping each read in an
 * [ObserverContext] that carries the session's ranking observer: the first
 * NIP-42-authenticated pubkey, or else the operator's default. The store
 * reads that element back out when it builds the Vespa query. This is how a
 * per-connection fact crosses the caller-agnostic `IEventStore` interface.
 */
internal class ObserverRoutingBackend(
    private val inner: LiveEventStore,
    private val defaultObserver: String?,
    private val onObserver: ((String) -> Unit)? = null,
) : SessionBackend {
    override suspend fun query(
        ctx: RequestContext,
        filters: List<Filter>,
        onEach: (Event) -> Unit,
        onEose: () -> Unit,
    ) = ranked(ctx, filters) { inner.query(ctx, filters, onEach, onEose) }

    override suspend fun count(
        ctx: RequestContext,
        filters: List<Filter>,
    ): Int = ranked(ctx, filters) { inner.count(ctx, filters) }

    override suspend fun countResult(
        ctx: RequestContext,
        filters: List<Filter>,
    ): CountResult = ranked(ctx, filters) { inner.countResult(ctx, filters) }

    override suspend fun submit(
        event: Event,
        onComplete: (IEventStore.InsertOutcome) -> Unit,
    ) = inner.submit(event, onComplete)

    override suspend fun snapshotIdsForNegentropy(
        filters: List<Filter>,
        maxEntries: Int?,
    ): List<IdAndTime> = inner.snapshotIdsForNegentropy(filters, maxEntries)

    private suspend fun <T> ranked(
        ctx: RequestContext,
        filters: List<Filter>,
        block: suspend () -> T,
    ): T {
        val authenticated = ctx.authenticatedUsers.firstOrNull()
        authenticated?.let { onObserver?.invoke(it) }
        val observer = authenticated ?: defaultObserver
        // Both elements cross IEventStore's caller-agnostic interface through
        // the coroutine context. OriginalFilters preserves the NIP-50
        // extensions that Quartz's engine strips before the store, which this
        // store honors. ObserverContext carries the ranking observer.
        var context: CoroutineContext = OriginalFilters(filters)
        if (observer != null) context += ObserverContext(observer)
        return withContext(context) { block() }
    }
}
